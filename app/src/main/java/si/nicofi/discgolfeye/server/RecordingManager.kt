package si.nicofi.discgolfeye.server

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
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
    }

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentRecordingFile: File? = null
    private var currentCameraId: String? = null

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val thumbnailExecutor = Executors.newSingleThreadExecutor()
    private var chunkJob: Job? = null

    private val cameraPreferences = CameraPreferences(context)

    // Lista wykrytych kamer
    private var availableCameras: List<CameraInfo> = emptyList()

    val isRecording: Boolean
        get() = activeRecording != null

    val currentCamera: CameraInfo?
        get() = availableCameras.find { it.id == currentCameraId }

    val recordingsDir: File by lazy {
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "DiscGolfEye").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getAvailableCameras(): List<CameraInfo> = availableCameras

    fun initialize(lifecycleOwner: LifecycleOwner, onReady: () -> Unit) {
        // Wykryj dostępne kamery
        availableCameras = CameraInfo.detectCameras(context)
        Log.d(TAG, "Detected cameras: ${availableCameras.map { "${it.id}: ${it.displayName}" }}")

        // Wczytaj zapisaną preferencję kamery
        val savedCameraId = cameraPreferences.selectedCameraId

        // Jeśli zapisana kamera nie jest dostępna, użyj pierwszej tylnej
        currentCameraId = if (savedCameraId != null && availableCameras.any { it.id == savedCameraId }) {
            savedCameraId
        } else {
            availableCameras.firstOrNull { !it.isFront }?.id ?: availableCameras.firstOrNull()?.id
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            currentCameraId?.let { cameraId ->
                bindCamera(lifecycleOwner, cameraId, onReady)
            } ?: run {
                Log.e(TAG, "No camera available")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCamera(lifecycleOwner: LifecycleOwner, cameraId: String, onReady: () -> Unit) {
        // Wybierz jakość na podstawie preferencji
        val quality = when (cameraPreferences.videoQuality) {
            VideoQualityOption.SD -> Quality.SD
            VideoQualityOption.HD -> Quality.HD
            VideoQualityOption.FHD -> Quality.FHD
            VideoQualityOption.UHD -> Quality.UHD
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    quality,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
            )
            .setExecutor(cameraExecutor)
            .build()

        videoCapture = VideoCapture.withOutput(recorder)

        Log.d(TAG, "Binding camera ID: $cameraId")

        // Wybierz kamerę po ID
        val cameraSelector = CameraSelector.Builder()
            .addCameraFilter { cameraInfos ->
                cameraInfos.filter { cameraInfo ->
                    try {
                        val id = Camera2CameraInfo.from(cameraInfo).cameraId
                        id == cameraId
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            .build()

        try {
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                videoCapture
            )
            currentCameraId = cameraId
            cameraPreferences.selectedCameraId = cameraId
            Log.d(TAG, "Camera bound successfully: $cameraId")
            onReady()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera $cameraId: ${e.message}", e)
            // Fallback do domyślnej tylnej kamery
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    videoCapture
                )
                // Znajdź ID domyślnej kamery
                currentCameraId = availableCameras.firstOrNull { !it.isFront }?.id
                onReady()
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to bind any camera", e2)
            }
        }
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

    @SuppressLint("MissingPermission")
    private fun startNewChunk() {
        val videoCapture = videoCapture ?: run {
            Log.e(TAG, "VideoCapture not initialized")
            return
        }

        val fileName = "vid_${SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())}.mp4"
        val videoFile = File(recordingsDir, fileName)
        currentRecordingFile = videoFile

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        val pendingRecording = videoCapture.output
            .prepareRecording(context, outputOptions)

        // Dodaj dźwięk jeśli włączony w preferencjach
        if (cameraPreferences.recordAudio) {
            pendingRecording.withAudioEnabled()
        }

        activeRecording = pendingRecording
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
        val limitMinutes = cameraPreferences.storageLimit.minutes
        if (limitMinutes == Int.MAX_VALUE) {
            // Bez limitu
            return
        }

        val starred = getStarredFilenames()
        val files = getVideoFiles()
            .filter { !starred.contains(it.name) }
            .sortedByDescending { it.lastModified() } // Najnowsze pierwsze

        // Każdy plik to ~1 minuta (CHUNK_DURATION_MS)
        // Liczymy ile plików możemy trzymać
        val maxFiles = limitMinutes // 1 plik = 1 minuta

        if (files.size > maxFiles) {
            files.drop(maxFiles).forEach { file ->
                Log.d(TAG, "Deleting old file (limit: ${limitMinutes}min): ${file.name}")
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

    // Starred files management
    private val starredFile: File by lazy {
        File(recordingsDir, ".starred")
    }

    private fun getStarredFilenames(): Set<String> {
        return try {
            if (starredFile.exists()) {
                starredFile.readLines().toSet()
            } else {
                emptySet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun saveStarredFilenames(filenames: Set<String>) {
        try {
            starredFile.writeText(filenames.joinToString("\n"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save starred files", e)
        }
    }

    fun isStarred(filename: String): Boolean {
        return getStarredFilenames().contains(filename)
    }

    fun toggleStar(filename: String): Boolean {
        val starred = getStarredFilenames().toMutableSet()
        val isNowStarred = if (starred.contains(filename)) {
            starred.remove(filename)
            false
        } else {
            starred.add(filename)
            true
        }
        saveStarredFilenames(starred)
        return isNowStarred
    }

    fun deleteVideo(filename: String): Boolean {
        // Nie pozwól usunąć starred
        if (isStarred(filename)) {
            Log.w(TAG, "Cannot delete starred file: $filename")
            return false
        }

        val videoFile = File(recordingsDir, filename)
        val thumbFile = getThumbnailForVideo(filename)

        var deleted = false
        if (videoFile.exists()) {
            deleted = videoFile.delete()
            Log.d(TAG, "Deleted video: $filename = $deleted")
        }
        thumbFile?.delete()

        return deleted
    }

    fun release() {
        // Zatrzymaj nagrywanie
        stopRecording()

        // Unbind kamery - to wystarczy, executory niech żyją do końca procesu
        // Zamykanie executorów powoduje RejectedExecutionException bo CameraX
        // nadal próbuje ich używać z callbacków MediaCodec
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding camera", e)
        }

        // NIE zamykamy executorów - CameraX ich potrzebuje do dokończenia operacji
        // Zamkną się automatycznie gdy proces się zakończy
    }
}
