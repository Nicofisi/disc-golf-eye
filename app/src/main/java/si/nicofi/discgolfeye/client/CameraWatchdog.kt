package si.nicofi.discgolfeye.client

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import si.nicofi.discgolfeye.shared.DeviceStatus

/**
 * Watchdog monitorujący status kamery i alertujący użytkownika
 */
class CameraWatchdog(private val context: Context) {

    companion object {
        const val BATTERY_LOW_THRESHOLD = 20
        const val BATTERY_CRITICAL_THRESHOLD = 10
        const val TEMP_WARNING_THRESHOLD = 42f
        const val TEMP_CRITICAL_THRESHOLD = 45f
        const val STORAGE_LOW_THRESHOLD_GB = 2f
        const val STORAGE_CRITICAL_THRESHOLD_GB = 1f

        private const val CHECK_INTERVAL_MS = 10_000L // 10 sekund
    }

    private var watchdogJob: Job? = null
    private var lastAlertTime = mutableMapOf<AlertType, Long>()

    // Cooldown między alertami tego samego typu (60 sekund)
    private val alertCooldownMs = 60_000L

    enum class AlertType {
        BATTERY_LOW,
        BATTERY_CRITICAL,
        TEMP_WARNING,
        TEMP_CRITICAL,
        STORAGE_LOW,
        STORAGE_CRITICAL,
        CONNECTION_LOST
    }

    data class WatchdogState(
        val isMonitoring: Boolean = false,
        val lastStatus: DeviceStatus? = null,
        val activeAlerts: Set<AlertType> = emptySet()
    )

    private var _state = WatchdogState()
    val state: WatchdogState get() = _state

    var onStateChanged: ((WatchdogState) -> Unit)? = null
    var onAlert: ((AlertType, String) -> Unit)? = null
    var onConnectionLost: (() -> Unit)? = null

    fun startMonitoring(
        scope: CoroutineScope,
        getStatus: suspend () -> Result<DeviceStatus>
    ) {
        if (watchdogJob?.isActive == true) return

        watchdogJob = scope.launch {
            _state = _state.copy(isMonitoring = true)
            onStateChanged?.invoke(_state)

            while (isActive) {
                val result = getStatus()

                result.fold(
                    onSuccess = { status ->
                        _state = _state.copy(lastStatus = status)
                        checkAlerts(status)
                        onStateChanged?.invoke(_state)
                    },
                    onFailure = {
                        // Reset status przy utracie połączenia
                        _state = _state.copy(lastStatus = null)
                        onStateChanged?.invoke(_state)
                        triggerAlert(AlertType.CONNECTION_LOST, "Utracono połączenie z kamerą!")
                        onConnectionLost?.invoke()
                    }
                )

                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun stopMonitoring() {
        watchdogJob?.cancel()
        watchdogJob = null
        _state = WatchdogState()
        onStateChanged?.invoke(_state)
    }

    private fun checkAlerts(status: DeviceStatus) {
        val activeAlerts = mutableSetOf<AlertType>()

        // Bateria
        when {
            status.batteryLevel <= BATTERY_CRITICAL_THRESHOLD -> {
                activeAlerts.add(AlertType.BATTERY_CRITICAL)
                triggerAlert(AlertType.BATTERY_CRITICAL, "⚠️ BATERIA KRYTYCZNA: ${status.batteryLevel}%!")
            }
            status.batteryLevel <= BATTERY_LOW_THRESHOLD -> {
                activeAlerts.add(AlertType.BATTERY_LOW)
                triggerAlert(AlertType.BATTERY_LOW, "🔋 Niski poziom baterii: ${status.batteryLevel}%")
            }
        }

        // Temperatura
        when {
            status.batteryTemp >= TEMP_CRITICAL_THRESHOLD -> {
                activeAlerts.add(AlertType.TEMP_CRITICAL)
                triggerAlert(AlertType.TEMP_CRITICAL, "🔥 PRZEGRZANIE: ${status.batteryTemp}°C!")
            }
            status.batteryTemp >= TEMP_WARNING_THRESHOLD -> {
                activeAlerts.add(AlertType.TEMP_WARNING)
                triggerAlert(AlertType.TEMP_WARNING, "🌡️ Wysoka temperatura: ${status.batteryTemp}°C")
            }
        }

        // Dysk
        when {
            status.storageFreeGb <= STORAGE_CRITICAL_THRESHOLD_GB -> {
                activeAlerts.add(AlertType.STORAGE_CRITICAL)
                triggerAlert(AlertType.STORAGE_CRITICAL, "💾 BRAK MIEJSCA: ${String.format("%.1f", status.storageFreeGb)}GB!")
            }
            status.storageFreeGb <= STORAGE_LOW_THRESHOLD_GB -> {
                activeAlerts.add(AlertType.STORAGE_LOW)
                triggerAlert(AlertType.STORAGE_LOW, "💾 Mało miejsca: ${String.format("%.1f", status.storageFreeGb)}GB")
            }
        }

        _state = _state.copy(activeAlerts = activeAlerts)
    }

    private fun triggerAlert(type: AlertType, message: String) {
        val now = System.currentTimeMillis()
        val lastTime = lastAlertTime[type] ?: 0L

        // Sprawdź cooldown
        if (now - lastTime < alertCooldownMs) return

        lastAlertTime[type] = now

        // Wibracja
        vibrate(
            when (type) {
                AlertType.BATTERY_CRITICAL, AlertType.TEMP_CRITICAL, AlertType.STORAGE_CRITICAL, AlertType.CONNECTION_LOST ->
                    longArrayOf(0, 300, 200, 300, 200, 300) // Mocna wibracja
                else ->
                    longArrayOf(0, 200, 100, 200) // Lekka wibracja
            }
        )

        // Callback
        onAlert?.invoke(type, message)

        // Toast
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun vibrate(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (e: Exception) {
            // Ignoruj błędy wibracji
        }
    }
}
