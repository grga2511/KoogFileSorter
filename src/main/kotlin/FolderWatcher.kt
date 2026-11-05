import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.attribute.BasicFileAttributes

class FolderWatcher(
    private val workspace: SorterWorkspace
) {
    private val logger = LoggerFactory.getLogger(FolderWatcher::class.java)

    private data class FileState(
        val size: Long,
        val modifiedAt: Long,
        val createdAt: Long,
        val fileKey: Any?
    )

    suspend fun watch(processFile: suspend (String) -> Unit) {
        val attemptedStates = mutableMapOf<Path, FileState>()

        workspace.inbox.fileSystem.newWatchService().use { watchService ->
            // Register first so arrivals during the initial scan are queued.
            workspace.inbox.register(watchService, ENTRY_CREATE)

            logger.info("Watching directory: {}", workspace.inbox)
            logger.info("Processing existing files.")



            logger.info("Initial scan completed. Waiting for new file events.")

            while (currentCoroutineContext().isActive) {
                // Wait for an event instead of repeatedly scanning the folder.
                val key = runInterruptible(Dispatchers.IO) {
                    watchService.take()
                }

                for (event in key.pollEvents()) {
                    //primjer: Zamisli da ubaciš puno datoteka odjednom.
                    // Dok tvoj program čeka da Gemini klasificira jednu datoteku,
                    // obavijesti o ostalima se gomilaju. Ako se neke izgube,
                    // program može dobiti događaj OVERFLOW.
                    // Taj događaj mu kaže „neke su promjene propuštene”, ali mu ne kaže koje.
                    if (event.kind() == OVERFLOW) {

                        logger.warn(
                            "File event overflow detected. Rescanning inbox."
                        )

                        for (file in listInputFiles()) {
                            processCandidate(
                                file,
                                attemptedStates,
                                processFile
                            )
                        }

                        continue
                    }

                    if (event.kind() == ENTRY_CREATE) {
                        val relativePath = event.context() as? Path
                            ?: continue

                        val file = workspace.inbox
                            .resolve(relativePath)
                            .normalize()

                        if (file.parent == workspace.inbox) {
                            processCandidate(
                                file,
                                attemptedStates,
                                processFile
                            )
                        }
                    }
                }

                if (!key.reset()) {
                    logger.warn(
                        "Input directory is no longer available. Watching stopped."
                    )
                    break
                }
            }
        }
    }

    private fun listInputFiles(): List<Path> =
        Files.list(workspace.inbox).use { stream ->
            stream.filter {
                isInputFile(it)
            }.toList()
        }

    private fun isInputFile(file: Path): Boolean =
        file.fileName.toString() != ".gitkeep" &&
                Files.isRegularFile(file, NOFOLLOW_LINKS)

    private suspend fun processCandidate(
        file: Path,
        attemptedStates: MutableMap<Path, FileState>,
        processFile: suspend (String) -> Unit
    ) {
        if (!isInputFile(file)) {
            return
        }

        try {
            val initialState = readFileState(file)

            if (attemptedStates[file] == initialState) {
                return
            }

            val stableState = waitForFile(file)
            attemptedStates[file] = stableState

            val fileName = file.fileName.toString()

            logger.info("File ready for processing: {}", fileName)
            processFile(fileName)

            if (!Files.exists(file, NOFOLLOW_LINKS)) {
                attemptedStates.remove(file)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(
                "File processing failed: {}",
                file.fileName,
                e
            )
        }
    }

    private suspend fun waitForFile(file: Path): FileState {
        var previousState = readFileState(file)

        repeat(30) {
            delay(1_000)

            val currentState = readFileState(file)

            if (currentState == previousState) {
                return currentState
            }

            previousState = currentState
        }

        error(
            "File did not become stable within 30 seconds: ${file.fileName}"
        )
    }

    private fun readFileState(file: Path): FileState {
        val attributes = Files.readAttributes(
            file,
            BasicFileAttributes::class.java,
            NOFOLLOW_LINKS
        )

        require(attributes.isRegularFile) {
            "Input must be a regular file: ${file.fileName}"
        }

        return FileState(
            size = attributes.size(),
            modifiedAt = attributes.lastModifiedTime().toMillis(),
            createdAt = attributes.creationTime().toMillis(),
            fileKey = attributes.fileKey()
        )
    }
}