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
import si.nicofi.discgolfeye.shared.DeviceStatus
import si.nicofi.discgolfeye.shared.PingResponse
import si.nicofi.discgolfeye.shared.VideoFileInfo

class DiscGolfClient {

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json()
        }
    }

    private var serverIp: String? = null
    private var serverPort: Int = 8347

    fun setServer(ip: String, port: Int = 8347) {
        serverIp = ip
        serverPort = port
    }

    fun getServerBaseUrl(): String? {
        val ip = serverIp ?: return null
        return "http://$ip:$serverPort"
    }

    suspend fun ping(serverIp: String, port: Int = 8347): Result<PingResponse> {
        return try {
            val response: PingResponse = httpClient.get("http://$serverIp:$port/ping").body()
            this.serverIp = serverIp
            this.serverPort = port
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStatus(): Result<DeviceStatus> {
        val baseUrl = getServerBaseUrl() ?: return Result.failure(Exception("Not connected"))
        return try {
            val response: DeviceStatus = httpClient.get("$baseUrl/status").body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVideos(): Result<List<VideoFileInfo>> {
        val baseUrl = getServerBaseUrl() ?: return Result.failure(Exception("Not connected"))
        return try {
            val response: List<VideoFileInfo> = httpClient.get("$baseUrl/videos").body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerFlush(): Result<String> {
        val baseUrl = getServerBaseUrl() ?: return Result.failure(Exception("Not connected"))
        return try {
            val response: Map<String, String> = httpClient.post("$baseUrl/trigger-flush").body()
            Result.success(response["lastFile"] ?: "unknown")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getVideoStreamUrl(filename: String): String? {
        val baseUrl = getServerBaseUrl() ?: return null
        return "$baseUrl/stream/$filename"
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
