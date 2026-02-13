package com.volumer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker для восстановления громкости через заданное время.
 * Использует WorkManager для надёжной работы в фоне.
 */
class VolumeRestoreWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Restoring volume...")

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Восстанавливаем режим звонка на нормальный
        try {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

            // Устанавливаем громкость звонка на 70% от максимума
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            val targetVolume = (maxVolume * 0.7).toInt()
            audioManager.setStreamVolume(
                AudioManager.STREAM_RING,
                targetVolume,
                AudioManager.FLAG_SHOW_UI
            )

            // Также восстанавливаем громкость уведомлений
            val maxNotificationVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            audioManager.setStreamVolume(
                AudioManager.STREAM_NOTIFICATION,
                (maxNotificationVolume * 0.7).toInt(),
                0
            )

            showNotification()
            Log.d(TAG, "Volume restored successfully")

            return Result.success()
        } catch (e: SecurityException) {
            Log.e(TAG, "No permission to change ringer mode", e)
            return Result.failure()
        }
    }

    private fun showNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Создаём канал уведомлений для Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Восстановление громкости",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о восстановлении громкости"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Громкость восстановлена")
            .setContentText("Звук звонка был автоматически включён")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "VolumeRestoreWorker"
        private const val CHANNEL_ID = "volume_restore_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
