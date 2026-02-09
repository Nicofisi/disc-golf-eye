package si.nicofi.discgolfeye.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import si.nicofi.discgolfeye.R

class ServerService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var videoServer: VideoServer? = null

    val isServerRunning: Boolean
        get() = videoServer?.isRunning == true

    inner class LocalBinder : Binder() {
        fun getService(): ServerService = this@ServerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> startServer()
            ACTION_STOP_SERVER -> stopServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        if (videoServer?.isRunning == true) return

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        videoServer = VideoServer(PORT).also {
            it.start(serviceScope)
        }
    }

    private fun stopServer() {
        videoServer?.stop()
        videoServer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DiscGolfEye Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Serwer kamery działa w tle"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DiscGolfEye")
            .setContentText("Serwer kamery działa na porcie $PORT")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoServer?.stop()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START_SERVER = "si.nicofi.discgolfeye.START_SERVER"
        const val ACTION_STOP_SERVER = "si.nicofi.discgolfeye.STOP_SERVER"
        const val CHANNEL_ID = "discgolfeye_server_channel"
        const val NOTIFICATION_ID = 1
        const val PORT = 8080
    }
}
