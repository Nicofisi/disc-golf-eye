package si.nicofi.discgolfeye.client

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable

@Serializable
data class PingResponse(
    val status: String,
    val message: String
)

class DiscGolfClient {

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun ping(serverIp: String, port: Int = 8080): Result<PingResponse> {
        return try {
            val response: PingResponse = httpClient.get("http://$serverIp:$port/ping").body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        /**
         * Pobiera adres IP bramy (gateway) - czyli telefonu z Hotspotem
         */
        fun getGatewayIp(context: Context): String? {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return null
            val linkProperties: LinkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null

            // Znajdź domyślną trasę i pobierz adres gateway
            val gateway = linkProperties.routes
                .firstOrNull { it.isDefaultRoute }
                ?.gateway
                ?.hostAddress

            return gateway
        }
    }
}
