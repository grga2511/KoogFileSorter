import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

class FileToolsTest : FunSpec() {

    private lateinit var temporaryDirectory: Path
    private lateinit var workspace: SorterWorkspace
    private lateinit var fileTools: FileTools

    init {
        beforeTest {
            temporaryDirectory = Files.createTempDirectory("koog-sorter-test-")
                .toRealPath()

            val config = AppConfig(workspaceOverride = temporaryDirectory)

            workspace = SorterWorkspace(config)
            workspace.initialize()

            fileTools = FileTools(workspace)
        }

        afterTest {
            Files.walk(temporaryDirectory).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        }

        test("initialize - creates inbox and all category directories") {
            Files.isDirectory(workspace.inbox) shouldBe true

            FileCategory.entries.forEach { category ->
                Files.isDirectory(
                    workspace.directoryFor(category)
                ) shouldBe true
            }
        }

        test("readFile - returns text content") {
            Files.writeString(
                workspace.inbox.resolve("notes.txt"),
                "Project meeting notes."
            )

            fileTools.readFile("notes.txt") shouldBe
                    "Project meeting notes."
        }

        test("readFile - rejects a path outside inbox") {
            val outsideFile = temporaryDirectory.resolve("outside.txt")
            Files.writeString(outsideFile, "Outside content.")

            shouldThrow<IllegalArgumentException> {
                fileTools.readFile("../outside.txt")
            }

            Files.readString(outsideFile) shouldBe "Outside content."
        }

        test("readFile - rejects a missing file") {
            shouldThrow<IllegalArgumentException> {
                fileTools.readFile("missing.txt")
            }
        }

        test("readFile - reports files exceeding the size limit") {
            Files.write(
                workspace.inbox.resolve("large.txt"),
                ByteArray(65_537) { 'a'.code.toByte() }
            )

            fileTools.readFile("large.txt") shouldStartWith "READ_FAILED:"
        }

        test("readFile - reports binary content") {
            Files.write(
                workspace.inbox.resolve("binary.dat"),
                byteArrayOf(65, 0, 66)
            )

            fileTools.readFile("binary.dat") shouldStartWith "READ_FAILED:"
        }

        test("moveFile - moves the file and preserves its content") {
            val source = workspace.inbox.resolve("notes.txt")
            val destination = workspace.directoryFor(FileCategory.DOCUMENTS)
                .resolve("notes.txt")

            Files.writeString(source, "Project meeting notes.")

            fileTools.moveFile("notes.txt", "DOCUMENTS")

            Files.exists(source) shouldBe false
            Files.readString(destination) shouldBe "Project meeting notes."
        }

        test("moveFile - sends duplicate names to review without overwriting") {
            val source = workspace.inbox.resolve("notes.txt")
            val destination = workspace.directoryFor(FileCategory.DOCUMENTS)
                .resolve("notes.txt")
            val reviewDirectory = workspace.directoryFor(FileCategory.REVIEW)

            Files.writeString(source, "New content.")
            Files.writeString(destination, "Existing content.")

            fileTools.moveFile("notes.txt", "DOCUMENTS")

            Files.exists(source) shouldBe false
            Files.readString(destination) shouldBe "Existing content."

            val reviewFiles = Files.list(reviewDirectory).use { it.toList() }
            reviewFiles.size shouldBe 1

            val reviewedFile = reviewFiles.single()
            reviewedFile.fileName.toString() shouldStartWith "notes-"
            reviewedFile.fileName.toString().endsWith(".txt") shouldBe true
            Files.readString(reviewedFile) shouldBe "New content."
        }

        test("moveFile - rejects an unsupported category") {
            val source = workspace.inbox.resolve("notes.txt")
            Files.writeString(source, "Project meeting notes.")

            shouldThrow<IllegalStateException> {
                fileTools.moveFile("notes.txt", "UNKNOWN")
            }

            Files.exists(source) shouldBe true
        }

        test("moveFile - moves a file to review") {
            val source = workspace.inbox.resolve("unknown.txt")
            val destination = workspace.directoryFor(FileCategory.REVIEW)
                .resolve("unknown.txt")

            Files.writeString(source, "")

            fileTools.moveFile("unknown.txt", "REVIEW")

            Files.exists(source) shouldBe false
            Files.exists(destination) shouldBe true
        }
    }
}