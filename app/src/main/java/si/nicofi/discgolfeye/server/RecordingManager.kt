package si.nicofi.discgolfeye.server

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class RecordingManager(private val context: Context) {

    companion object {
        private const val TAG = "RecordingManager"
        private const val CHUNK_DURATION_MS = 60_000L // 1 minuta
        private const val MAX_FILES = 30 // Maksymalnie 30 plików (30 minut)
    }

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentRecordingFile: File? = null

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val thumbnailExecutor = Executors.newSingleThreadExecutor()
    private var chunkJob: Job? = null

    val isRecording: Boolean
        get() = activeRecording != null

    val recordingsDir: File by lazy {
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "DiscGolfEye").apply {
            if (!exists()) mkdirs()
        }
    }

    fun initialize(lifecycleOwner: LifecycleOwner, onReady: () -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HD, // 720p - dobry kompromis między jakością a zużyciem baterii
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )
                )
                .setExecutor(cameraExecutor)
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    videoCapture
                )
                Log.d(TAG, "Camera initialized successfully")
                onReady()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording(scope: CoroutineScope) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        startNewChunk()

        // Uruchom automatyczne dzielenie na chunki
        chunkJob = scope.launch {
            while (isActive) {
                delay(CHUNK_DURATION_MS)
                if (isRecording) {
                    Log.d(TAG, "Switching to new chunk...")
                    stopCurrentChunk()
                    startNewChunk()
                    cleanupOldFiles()
                }
            }
        }
    }

    private fun startNewChunk() {
        val videoCapture = videoCapture ?: run {
            Log.e(TAG, "VideoCapture not initialized")
            return
        }

        val fileName = "vid_${SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())}.mp4"
        val videoFile = File(recordingsDir, fileName)
        currentRecordingFile = videoFile

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        activeRecording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .start(cameraExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "Recording started: ${videoFile.name}")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (event.hasError()) {
                            Log.e(TAG, "Recording error: ${event.error}")
                        } else {
                            Log.d(TAG, "Recording saved: ${videoFile.name} (${videoFile.length() / 1024}KB)")
                            // Generuj miniaturkę w tle
                            generateThumbnail(videoFile)
                        }
                    }
                }
            }
    }

    private fun stopCurrentChunk() {
        activeRecording?.stop()
        activeRecording = null
        currentRecordingFile = null
    }

    fun stopRecording() {
        chunkJob?.cancel()
        chunkJob = null
        stopCurrentChunk()
        Log.d(TAG, "Recording stopped")
    }

    fun forceFlush(): String? {
        if (!isRecording) return null

        stopCurrentChunk()
        val lastFile = getVideoFiles().maxByOrNull { it.lastModified() }
        startNewChunk()

        return lastFile?.name
    }

    fun getVideoFiles(): List<File> {
        val currentFile = currentRecordingFile
        return recordingsDir.listFiles { file ->
            file.extension == "mp4" && file != currentFile
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun cleanupOldFiles() {
        val files = getVideoFiles()
        if (files.size > MAX_FILES) {
            files.drop(MAX_FILES).forEach { file ->
                Log.d(TAG, "Deleting old file: ${file.name}")
                file.delete()
                // Usuń też miniaturkę
                getThumbnailFile(file)?.delete()
            }
        }
    }

    private fun generateThumbnail(videoFile: File) {
        thumbnailExecutor.execute {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoFile.absolutePath)

                // Pobierz klatkę z 1 sekundy filmu
                val bitmap = retriever.getFrameAtTime(1_000_000) // 1 sekunda w mikrosekundach
                retriever.release()

                if (bitmap != null) {
                    val thumbFile = File(recordingsDir, videoFile.nameWithoutExtension + ".jpg")
                    FileOutputStream(thumbFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                    }
                    bitmap.recycle()
                    Log.d(TAG, "Thumbnail generated: ${thumbFile.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate thumbnail for ${videoFile.name}", e)
            }
        }
    }

    fun getThumbnailFile(videoFile: File): File? {
        val thumbFile = File(recordingsDir, videoFile.nameWithoutExtension + ".jpg")
        return if (thumbFile.exists()) thumbFile else null
    }

    fun getThumbnailForVideo(filename: String): File? {
        val baseName = filename.removeSuffix(".mp4")
        val thumbFile = File(recordingsDir, "$baseName.jpg")
        return if (thumbFile.exists()) thumbFile else null
    }

    fun release() {
        stopRecording()
        cameraExecutor.shutdown()
        thumbnailExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}
