package si.nicofi.discgolfeye.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class PingResponse(
    val status: String = "ok",
    val message: String = "DiscGolfEye Server is running"
)

class VideoServer(private val port: Int = 8080) {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    val isRunning: Boolean
        get() = server != null

    fun start(scope: CoroutineScope) {
        if (server != null) return

        scope.launch(Dispatchers.IO) {
            server = embeddedServer(Netty, port = port) {
                install(ContentNegotiation) {
                    json()
                }

                routing {
                    get("/ping") {
                        call.respond(PingResponse())
                    }

                    get("/status") {
                        // TODO: Dodać prawdziwy status (bateria, temperatura, etc.)
                        call.respond(
                            mapOf(
                                "state" to "IDLE",
                                "batteryLevel" to 100,
                                "batteryTemp" to 25.0,
                                "storageFreeGb" to 10.0,
                                "isRecording" to false,
                                "uptimeSeconds" to 0
                            )
                        )
                    }
                }
            }.start(wait = false)
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}
