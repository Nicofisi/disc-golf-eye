package si.nicofi.discgolfeye.server

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/**
 * Przechowuje preferencje kamery (która kamera była ostatnio używana)
 */
class CameraPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "discgolfeye_camera_prefs"
        private const val KEY_CAMERA_ID = "selected_camera_id"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedCameraId: String?
        get() = prefs.getString(KEY_CAMERA_ID, null)
        set(value) = prefs.edit().putString(KEY_CAMERA_ID, value).apply()
}

/**
 * Informacje o wykrytej kamerze
 */
data class CameraInfo(
    val id: String,
    val displayName: String,
    val isFront: Boolean,
    val focalLength: Float
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

                    val isFront = lensFacing == CameraCharacteristics.LENS_FACING_FRONT

                    // Stwórz czytelną nazwę
                    val displayName = if (isFront) {
                        "Przednia"
                    } else {
                        "Tylna ${focalLength.format()}mm"
                    }

                    cameras.add(CameraInfo(
                        id = cameraId,
                        displayName = displayName,
                        isFront = isFront,
                        focalLength = focalLength
                    ))
                }
            } catch (e: Exception) {
                // Fallback
            }

            // Sortuj: tylne po focal length (od największej = szerokokątna), przednia na końcu
            return cameras.sortedWith(compareBy({ it.isFront }, { -it.focalLength }))
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
