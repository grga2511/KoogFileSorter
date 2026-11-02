import java.nio.file.Files
import java.nio.file.Path

class SorterWorkspace(config: AppConfig) {
    val root: Path = config.workspace
    val inbox: Path = config.inbox

    fun initialize() {
        createDirectory(root)
        createDirectory(inbox)

        FileCategory.entries.forEach { category ->
            createDirectory(directoryFor(category))
        }
    }

    fun directoryFor(category: FileCategory): Path =
        root.resolve(category.folderName)

    private fun createDirectory(path: Path) {
        require(!Files.isSymbolicLink(path)) {
            "Symbolic links are not allowed: $path"
        }

        Files.createDirectories(path)

        require(Files.isDirectory(path)) {
            "Path is not a directory: $path"
        }
    }
}