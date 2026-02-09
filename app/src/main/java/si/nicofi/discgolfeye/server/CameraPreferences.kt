package si.nicofi.discgolfeye.server

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.SizeF
import kotlin.math.atan
import kotlin.math.sqrt

/**
 * Przechowuje preferencje kamery (która kamera była ostatnio używana)
 */
class CameraPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "discgolfeye_camera_prefs"
        private const val KEY_CAMERA_ID = "selected_camera_id"
        private const val KEY_RECORD_AUDIO = "record_audio"
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_STORAGE_LIMIT = "storage_limit"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedCameraId: String?
        get() = prefs.getString(KEY_CAMERA_ID, null)
        set(value) = prefs.edit().putString(KEY_CAMERA_ID, value).apply()

    var recordAudio: Boolean
        get() = prefs.getBoolean(KEY_RECORD_AUDIO, true) // domyślnie z dźwiękiem
        set(value) = prefs.edit().putBoolean(KEY_RECORD_AUDIO, value).apply()

    var videoQuality: VideoQualityOption
        get() = VideoQualityOption.fromName(prefs.getString(KEY_VIDEO_QUALITY, VideoQualityOption.HD.name))
        set(value) = prefs.edit().putString(KEY_VIDEO_QUALITY, value.name).apply()

    var storageLimit: StorageLimitOption
        get() = StorageLimitOption.fromName(prefs.getString(KEY_STORAGE_LIMIT, StorageLimitOption.MIN_30.name))
        set(value) = prefs.edit().putString(KEY_STORAGE_LIMIT, value.name).apply()
}

enum class StorageLimitOption(val displayName: String, val minutes: Int) {
    MIN_5("5 minut", 5),
    MIN_15("15 minut", 15),
    MIN_30("30 minut", 30),
    HOUR_1("1 godzina", 60),
    HOUR_2("2 godziny", 120),
    HOUR_4("4 godziny", 240),
    HOUR_8("8 godzin", 480),
    UNLIMITED("Bez limitu", Int.MAX_VALUE);

    companion object {
        fun fromName(name: String?): StorageLimitOption {
            return entries.find { it.name == name } ?: MIN_30
        }
    }
}

enum class VideoQualityOption(val displayName: String, val shortName: String) {
    SD("480p (SD)", "480p"),
    HD("720p (HD)", "720p"),
    FHD("1080p (Full HD)", "1080p"),
    UHD("4K (Ultra HD)", "4K");

    companion object {
        fun fromName(name: String?): VideoQualityOption {
            return entries.find { it.name == name } ?: HD
        }
    }
}

/**
 * Informacje o wykrytej kamerze
 */
data class CameraInfo(
    val id: String,
    val displayName: String,
    val isFront: Boolean,
    val focalLength: Float,
    val fovDegrees: Float // Kąt widzenia w stopniach
) {
    companion object {
        fun detectCameras(context: Context): List<CameraInfo> {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameras = mutableListOf<CameraInfo>()

            try {
                for (cameraId in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val focalLength = focalLengths?.firstOrNull() ?: 0f

                    // Oblicz kąt widzenia (FOV) na podstawie rozmiaru sensora
                    val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    val fovDegrees = calculateFOV(focalLength, sensorSize)

                    val isFront = lensFacing == CameraCharacteristics.LENS_FACING_FRONT

                    // Stwórz czytelną nazwę z kątem widzenia
                    val fovLabel = when {
                        fovDegrees >= 100 -> "ultrawide"
                        fovDegrees >= 75 -> "wide"
                        fovDegrees >= 50 -> "standard"
                        fovDegrees > 0 -> "tele"
                        else -> "${focalLength.format()}mm"
                    }

                    val displayName = if (isFront) {
                        "Przednia ($fovLabel, ${fovDegrees.toInt()}°)"
                    } else {
                        "Tylna ($fovLabel, ${fovDegrees.toInt()}°)"
                    }

                    cameras.add(CameraInfo(
                        id = cameraId,
                        displayName = displayName,
                        isFront = isFront,
                        focalLength = focalLength,
                        fovDegrees = fovDegrees
                    ))
                }
            } catch (e: Exception) {
                // Fallback
            }

            // Sortuj: tylne po FOV (od największego = szerokokątna), przednia na końcu
            return cameras.sortedWith(compareBy({ it.isFront }, { -it.fovDegrees }))
        }

        private fun calculateFOV(focalLength: Float, sensorSize: SizeF?): Float {
            if (focalLength <= 0 || sensorSize == null) return 0f

            // Oblicz przekątną sensora
            val diagonalMm = sqrt(
                sensorSize.width * sensorSize.width +
                sensorSize.height * sensorSize.height
            )

            // FOV = 2 * arctan(d / 2f) gdzie d = przekątna sensora, f = ogniskowa
            val fovRadians = 2 * atan((diagonalMm / (2 * focalLength)).toDouble())
            return Math.toDegrees(fovRadians).toFloat()
        }

        private fun Float.format(): String = if (this == this.toLong().toFloat()) {
            this.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", this)
        }
    }
}

// Zachowaj dla kompatybilności wstecznej, ale już nie używane
enum class CameraLensType(val displayName: String) {
    BACK_DEFAULT("Tylna główna"),
    BACK_WIDE("Tylna szerokokątna"),
    BACK_TELEPHOTO("Tylna teleobiektyw"),
    FRONT("Przednia")
}
