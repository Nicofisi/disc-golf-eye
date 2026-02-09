package si.nicofi.discgolfeye.server

import android.content.Context
import android.content.SharedPreferences

/**
 * Przechowuje preferencje kamery (która kamera była ostatnio używana)
 */
class CameraPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "discgolfeye_camera_prefs"
        private const val KEY_CAMERA_ID = "selected_camera_id"
        private const val KEY_CAMERA_LENS = "selected_camera_lens"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedCameraId: String?
        get() = prefs.getString(KEY_CAMERA_ID, null)
        set(value) = prefs.edit().putString(KEY_CAMERA_ID, value).apply()

    var selectedCameraLens: Int
        get() = prefs.getInt(KEY_CAMERA_LENS, CameraLensType.BACK_DEFAULT.ordinal)
        set(value) = prefs.edit().putInt(KEY_CAMERA_LENS, value).apply()

    fun getSelectedLensType(): CameraLensType {
        return CameraLensType.entries.getOrElse(selectedCameraLens) { CameraLensType.BACK_DEFAULT }
    }

    fun setSelectedLensType(type: CameraLensType) {
        selectedCameraLens = type.ordinal
    }
}

enum class CameraLensType(val displayName: String) {
    BACK_DEFAULT("Tylna główna"),
    BACK_WIDE("Tylna szerokokątna"),
    BACK_TELEPHOTO("Tylna teleobiektyw"),
    FRONT("Przednia")
}
