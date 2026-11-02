import java.nio.file.Path
import java.util.Properties

class AppConfig(
    workspaceOverride: Path? = null
) {
    private val properties = Properties()

    init {
        val stream = javaClass.classLoader
            .getResourceAsStream("application.properties")
            ?: error("application.properties was not found.")

        stream.use {
            properties.load(it)
        }
    }

    val workspace: Path =
        (workspaceOverride ?: Path.of(required("sorter.workspace")))
            .also {
                require(it.isAbsolute) {
                    "sorter.workspace must be an absolute path."
                }
            }
            .normalize()

    val inbox: Path = workspace.resolve("inbox")

    val modelName = required("llm.model")

    val proxyEnabled = required("proxy.enabled").toBooleanStrict()

    val proxyUrl: String? =
        if (proxyEnabled) required("proxy.url") else null

    private fun required(name: String): String =
        properties.getProperty(name)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: error("Missing configuration property: $name")
}