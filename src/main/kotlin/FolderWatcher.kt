import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

class FolderWatcher(
    private val workspace: SorterWorkspace
) {
    private val logger = LoggerFactory.getLogger(FolderWatcher::class.java)

    private data class FileState(
        val size: Long,
        val modifiedAt: Long
    )

    suspend fun watch(processFile: suspend (String) -> Unit) {
        val previousStates = mutableMapOf<Path, FileState>()
        val attemptedStates = mutableMapOf<Path, FileState>()

        logger.info("Watching directory: {}", workspace.inbox)
        logger.info("Stop the application to stop watching.")

        while (currentCoroutineContext().isActive) {
            val files = Files.list(workspace.inbox).use { stream ->
                stream.filter {
                    Files.isRegularFile(it, NOFOLLOW_LINKS)
                }.toList()
            }

            previousStates.keys.retainAll(files.toSet())
            attemptedStates.keys.retainAll(files.toSet())

            for (file in files) {
                try {
                    val attributes = Files.readAttributes(
                        file,
                        BasicFileAttributes::class.java,
                        NOFOLLOW_LINKS
                    )

                    val state = FileState(
                        size = attributes.size(),
                        modifiedAt = attributes.lastModifiedTime().toMillis()
                    )

                    val previousState = previousStates.put(file, state)

                    // Wait until size and modification time are unchanged
                    // across two consecutive directory scans.
                    if (previousState != state) {
                        continue
                    }

                    // Avoid repeatedly calling the LLM for an unchanged file
                    // after a failed attempt.
                    if (attemptedStates[file] == state) {
                        continue
                    }

                    attemptedStates[file] = state

                    val fileName = file.fileName.toString()
                    logger.info("New file ready for processing: {}", fileName)

                    processFile(fileName)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("File processing failed: {}", file.fileName, e)
                }
            }

            delay(2_000)
        }
    }
}