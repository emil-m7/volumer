package com.volumer

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Наблюдатель за изменениями громкости.
 * Отслеживает:
 * - Снижение громкости звонков ниже порога
 * - Снижение громкости уведомлений ниже порога
 * - Переключение в беззвучный режим или вибрацию
 */
class VolumeObserver(
    private val context: Context,
    private val onVolumeLowered: (reason: String) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Сохраняем предыдущие значения для отслеживания изменений
    private var lastRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
    private var lastNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
    private var lastRingerMode = audioManager.ringerMode

    // Порог - считаем громкость низкой если она ниже 30% от максимума
    private val ringVolumeThreshold = (audioManager.getStreamMaxVolume(AudioManager.STREAM_RING) * 0.3).toInt()
    private val notificationVolumeThreshold = (audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) * 0.3).toInt()

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        checkVolumeChanges()
    }

    private fun checkVolumeChanges() {
        val currentRingerMode = audioManager.ringerMode
        val currentRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        val currentNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)

        Log.d(TAG, "Volume check: ringerMode=$currentRingerMode, ring=$currentRingVolume, notification=$currentNotificationVolume")

        var shouldTrigger = false
        var reason = ""

        // 1. Проверяем переключение в беззвучный режим или вибрацию
        if (currentRingerMode != AudioManager.RINGER_MODE_NORMAL &&
            lastRingerMode == AudioManager.RINGER_MODE_NORMAL) {
            shouldTrigger = true
            reason = when (currentRingerMode) {
                AudioManager.RINGER_MODE_SILENT -> "Включён беззвучный режим"
                AudioManager.RINGER_MODE_VIBRATE -> "Включён режим вибрации"
                else -> "Звук выключен"
            }
            Log.d(TAG, "Ringer mode changed to silent/vibrate")
        }

        // 2. Проверяем снижение громкости звонков ниже порога
        if (!shouldTrigger &&
            currentRingVolume < ringVolumeThreshold &&
            lastRingVolume >= ringVolumeThreshold) {
            shouldTrigger = true
            reason = "Громкость звонков снижена до $currentRingVolume"
            Log.d(TAG, "Ring volume lowered below threshold: $currentRingVolume < $ringVolumeThreshold")
        }

        // 3. Проверяем снижение громкости уведомлений ниже порога
        if (!shouldTrigger &&
            currentNotificationVolume < notificationVolumeThreshold &&
            lastNotificationVolume >= notificationVolumeThreshold) {
            shouldTrigger = true
            reason = "Громкость уведомлений снижена до $currentNotificationVolume"
            Log.d(TAG, "Notification volume lowered below threshold: $currentNotificationVolume < $notificationVolumeThreshold")
        }

        // 4. Проверяем если громкость звонков стала 0
        if (!shouldTrigger && currentRingVolume == 0 && lastRingVolume > 0) {
            shouldTrigger = true
            reason = "Громкость звонков выключена"
            Log.d(TAG, "Ring volume set to 0")
        }

        // Сохраняем текущие значения
        lastRingerMode = currentRingerMode
        lastRingVolume = currentRingVolume
        lastNotificationVolume = currentNotificationVolume

        if (shouldTrigger) {
            Log.d(TAG, "Triggering volume restore timer: $reason")
            onVolumeLowered(reason)
        }
    }

    companion object {
        private const val TAG = "VolumeObserver"
    }
}
