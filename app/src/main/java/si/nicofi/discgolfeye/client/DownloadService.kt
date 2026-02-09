package si.nicofi.discgolfeye.client

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    companion object {
        const val ACTION_DOWNLOAD = "si.nicofi.discgolfeye.DOWNLOAD"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_FILENAME = "filename"

        private const val CHANNEL_ID = "discgolfeye_download"
        private val nextNotificationId = AtomicInteger(2001)

        fun startDownload(context: Context, videoUrl: String, filename: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_FILENAME, filename)
            }
            context.startForegroundService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloads = mutableMapOf<Int, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: return START_NOT_STICKY
                val filename = intent.getStringExtra(EXTRA_FILENAME) ?: return START_NOT_STICKY

                val notificationId = nextNotificationId.getAndIncrement()

                // Startuj jako foreground z pierwszym powiadomieniem
                if (activeDownloads.isEmpty()) {
                    startForeground(notificationId, createProgressNotification(notificationId, filename, 0))
                }

                startDownload(videoUrl, filename, notificationId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(videoUrl: String, filename: String, notificationId: Int) {
        val job = serviceScope.launch {
            var savedFileUri: Uri? = null

            try {
                // Pokaż powiadomienie
                showProgressNotification(notificationId, filename, 0)

                val connection = URL(videoUrl).openConnection()
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                val contentLength = connection.contentLength.toLong()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    savedFileUri = downloadWithMediaStore(connection, filename, contentLength, notificationId)
                } else {
                    savedFileUri = downloadToFile(connection, filename, contentLength, notificationId)
                }

                withContext(Dispatchers.Main) {
                    showCompletedNotification(notificationId, filename, savedFileUri)
                    Toast.makeText(this@DownloadService, "Zapisano: $filename", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorNotification(notificationId, filename, e.message ?: "Nieznany błąd")
                    Toast.makeText(this@DownloadService, "Błąd: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                activeDownloads.remove(notificationId)

                // Jeśli to ostatnie pobieranie, zatrzymaj serwis
                if (activeDownloads.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }

        activeDownloads[notificationId] = job
    }

    private suspend fun downloadWithMediaStore(
        connection: java.net.URLConnection,
        filename: String,
        contentLength: Long,
        notificationId: Int
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/DiscGolfEye")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("Nie można utworzyć pliku w galerii")

        contentResolver.openOutputStream(uri)?.use { output ->
            connection.getInputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                var lastProgress = -1

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    val progress = if (contentLength > 0) ((totalRead * 100) / contentLength).toInt() else 0
                    if (progress != lastProgress) {
                        lastProgress = progress
                        showProgressNotification(notificationId, filename, progress)
                    }
                }
            }
        }

        // Oznacz jako gotowy
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)

        return uri
    }

    private fun downloadToFile(
        connection: java.net.URLConnection,
        filename: String,
        contentLength: Long,
        notificationId: Int
    ): Uri {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val appDir = File(moviesDir, "DiscGolfEye")
        if (!appDir.exists()) appDir.mkdirs()
        val outputFile = File(appDir, filename)

        FileOutputStream(outputFile).use { output ->
            connection.getInputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                var lastProgress = -1

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    val progress = if (contentLength > 0) ((totalRead * 100) / contentLength).toInt() else 0
                    if (progress != lastProgress) {
                        lastProgress = progress
                        showProgressNotification(notificationId, filename, progress)
                    }
                }
            }
        }

        return FileProvider.getUriForFile(this, "${packageName}.provider", outputFile)
    }

    private fun showProgressNotification(notificationId: Int, filename: String, progress: Int) {
        val notification = createProgressNotification(notificationId, filename, progress)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pobieranie nagrań",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Postęp pobierania nagrań z kamery"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createProgressNotification(notificationId: Int, filename: String, progress: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Pobieranie: $filename")
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun showCompletedNotification(notificationId: Int, filename: String, fileUri: Uri?) {
        // Anuluj powiadomienie z postępem
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(notificationId)

        // Nowe powiadomienie o zakończeniu
        val openIntent = if (fileUri != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "video/mp4")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                type = "video/*"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Pobrano: $filename")
            .setContentText("Kliknij aby otworzyć")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(notificationId + 1000, notification)
    }

    private fun showErrorNotification(notificationId: Int, filename: String, error: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(notificationId)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Błąd pobierania")
            .setContentText("$filename: $error")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(notificationId + 2000, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeDownloads.values.forEach { it.cancel() }
        serviceScope.cancel()
    }
}
