package si.nicofi.discgolfeye.shared

import kotlinx.serialization.Serializable

/**
 * Wspólne modele danych używane przez serwer i klient
 */

@Serializable
data class PingResponse(
    val status: String = "ok",
    val message: String = "DiscGolfEye Server is running"
)

@Serializable
data class DeviceStatus(
    val state: String,
    val batteryLevel: Int,
    val batteryTemp: Float,
    val storageFreeGb: Float,
    val isRecording: Boolean,
    val uptimeSeconds: Long
)

@Serializable
data class VideoFileInfo(
    val filename: String,
    val timestamp: Long,
    val sizeMb: Float,
    val videoUrl: String,
    val thumbUrl: String? = null
)
