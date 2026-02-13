package com.volumer

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Наблюдатель за изменениями громкости.
 * Отслеживает когда пользователь выключает звук звонка.
 */
class VolumeObserver(
    private val context: Context,
    private val onVolumeMuted: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var lastRingerMode = audioManager.ringerMode

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)

        val currentRingerMode = audioManager.ringerMode
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)

        Log.d(TAG, "Volume changed: ringerMode=$currentRingerMode, volume=$currentVolume")

        // Проверяем, был ли звук выключен (silent или vibrate режим)
        if (currentRingerMode != AudioManager.RINGER_MODE_NORMAL &&
            lastRingerMode == AudioManager.RINGER_MODE_NORMAL) {
            Log.d(TAG, "Sound was muted, scheduling restore")
            onVolumeMuted()
        }

        lastRingerMode = currentRingerMode
    }

    companion object {
        private const val TAG = "VolumeObserver"
    }
}
