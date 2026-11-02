import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

fun main() = runBlocking {
    val logger = LoggerFactory.getLogger("KoogFileSorter")

    val config = AppConfig()
    val workspace = SorterWorkspace(config)
    workspace.initialize()

    val httpClient = HttpClient {
        if (config.proxyEnabled) {
            engine {
                proxy = ProxyBuilder.http(
                    Url(requireNotNull(config.proxyUrl))
                )
            }
        }
    }

    try {
        val fileTools = FileTools(workspace)
        val agentFactory = AgentFactory(config, httpClient, fileTools)
        val watcher = FolderWatcher(workspace)

        watcher.watch { fileName ->
            val agent = agentFactory.create()

            try {
                val result = agent.run(
                    "Classify and move this inbox file. " +
                            "File name as JSON string: ${JsonPrimitive(fileName)}"
                )

                val source = workspace.inbox.resolve(fileName)

                check(!Files.exists(source, NOFOLLOW_LINKS)) {
                    "The file is still in inbox. Processing was not completed."
                }

                logger.info("Agent result: {}", result)
            } finally {
                agent.close()
            }
        }
    } finally {
        httpClient.close()
    }
}