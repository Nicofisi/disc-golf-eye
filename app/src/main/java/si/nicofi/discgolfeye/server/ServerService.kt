package si.nicofi.discgolfeye.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import si.nicofi.discgolfeye.R

class ServerService : Service(), LifecycleOwner {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var lifecycleRegistry: LifecycleRegistry

    private var videoServer: VideoServer? = null
    private var recordingManager: RecordingManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    val isServerRunning: Boolean
        get() = videoServer?.isRunning == true

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    inner class LocalBinder : Binder() {
        fun getService(): ServerService = this@ServerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
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

        val notification = createNotification("Uruchamianie...")
        startForeground(NOTIFICATION_ID, notification)

        // WakeLock - trzymaj procesor aktywnym (ekran może się wyłączyć ale nagrywanie działa)
        // Timeout: 4 godziny (wystarczy na długą rundę disc golfa)
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DiscGolfEye::RecordingWakeLock"
        ).apply {
            acquire(4 * 60 * 60 * 1000L) // 4 godziny
        }

        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        // Inicjalizuj RecordingManager
        recordingManager = RecordingManager(this).apply {
            initialize(this@ServerService) {
                // Kamera gotowa - automatycznie startuj nagrywanie
                startRecording(serviceScope)
                updateNotification("Nagrywanie na porcie $PORT")
            }
        }

        // Uruchom serwer HTTP
        videoServer = VideoServer(
            context = this,
            port = PORT,
            getRecordingManager = { recordingManager }
        ).also {
            it.start(serviceScope)
        }

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    private fun stopServer() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        recordingManager?.release()
        recordingManager = null

        videoServer?.stop()
        videoServer = null

        // Zwolnij WakeLock
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

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

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DiscGolfEye")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        recordingManager?.release()
        videoServer?.stop()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START_SERVER = "si.nicofi.discgolfeye.START_SERVER"
        const val ACTION_STOP_SERVER = "si.nicofi.discgolfeye.STOP_SERVER"
        const val CHANNEL_ID = "discgolfeye_server_channel"
        const val NOTIFICATION_ID = 1
        const val PORT = 8347
    }
}
