package si.nicofi.discgolfeye.server

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

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
    val videoUrl: String
)

class VideoServer(
    private val context: Context,
    private val port: Int = 8080,
    private val getRecordingManager: () -> RecordingManager?
) {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var startTime: Long = 0

    val isRunning: Boolean
        get() = server != null

    fun start(scope: CoroutineScope) {
        if (server != null) return
        startTime = System.currentTimeMillis()

        scope.launch(Dispatchers.IO) {
            server = embeddedServer(Netty, port = port) {
                install(ContentNegotiation) {
                    json()
                }
                install(PartialContent) {
                    maxRangeCount = 10
                }

                routing {
                    get("/ping") {
                        call.respond(PingResponse())
                    }

                    get("/status") {
                        val status = getDeviceStatus()
                        call.respond(status)
                    }

                    post("/record/start") {
                        val manager = getRecordingManager()
                        if (manager == null) {
                            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Camera not ready"))
                            return@post
                        }
                        if (manager.isRecording) {
                            call.respond(mapOf("status" to "already_recording"))
                        } else {
                            manager.startRecording(scope)
                            call.respond(mapOf("status" to "recording_started"))
                        }
                    }

                    post("/record/stop") {
                        val manager = getRecordingManager()
                        if (manager == null) {
                            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Camera not ready"))
                            return@post
                        }
                        manager.stopRecording()
                        call.respond(mapOf("status" to "recording_stopped"))
                    }

                    post("/trigger-flush") {
                        val manager = getRecordingManager()
                        if (manager == null) {
                            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Camera not ready"))
                            return@post
                        }
                        val flushedFile = manager.forceFlush()
                        call.respond(mapOf(
                            "status" to "flushed",
                            "lastFile" to (flushedFile ?: "none")
                        ))
                    }

                    get("/videos") {
                        val manager = getRecordingManager()
                        if (manager == null) {
                            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Camera not ready"))
                            return@get
                        }
                        val files = manager.getVideoFiles().map { file ->
                            VideoFileInfo(
                                filename = file.name,
                                timestamp = file.lastModified(),
                                sizeMb = file.length() / (1024f * 1024f),
                                videoUrl = "/stream/${file.name}"
                            )
                        }
                        call.respond(files)
                    }

                    get("/stream/{filename}") {
                        val filename = call.parameters["filename"] ?: run {
                            call.respond(HttpStatusCode.BadRequest, "Missing filename")
                            return@get
                        }
                        val manager = getRecordingManager() ?: run {
                            call.respond(HttpStatusCode.ServiceUnavailable, "Camera not ready")
                            return@get
                        }
                        val file = File(manager.recordingsDir, filename)
                        if (!file.exists()) {
                            call.respond(HttpStatusCode.NotFound, "File not found")
                            return@get
                        }
                        call.response.header(HttpHeaders.ContentType, ContentType.Video.MP4.toString())
                        call.respondFile(file)
                    }
                }
            }.start(wait = false)
        }
    }

    private fun getDeviceStatus(): DeviceStatus {
        val recordingManager = getRecordingManager()

        // Bateria
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 1
        val batteryPct = (level * 100) / scale
        val tempInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTemp = tempInt / 10f

        // Storage
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
        val gbAvailable = bytesAvailable / (1024f * 1024f * 1024f)

        val isRecording = recordingManager?.isRecording == true
        val state = when {
            recordingManager == null -> "INITIALIZING"
            isRecording -> "RECORDING"
            else -> "IDLE"
        }

        return DeviceStatus(
            state = state,
            batteryLevel = batteryPct,
            batteryTemp = batteryTemp,
            storageFreeGb = gbAvailable,
            isRecording = isRecording,
            uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000
        )
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}
