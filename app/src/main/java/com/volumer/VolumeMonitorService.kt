package com.volumer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Foreground сервис для постоянного отслеживания изменений громкости.
 * Работает в фоне и запускает таймер при выключении звука.
 */
class VolumeMonitorService : Service() {

    private var volumeObserver: VolumeObserver? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        startForeground(NOTIFICATION_ID, createNotification())
        startVolumeObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopVolumeObserver()
        Log.d(TAG, "Service destroyed")
    }

    private fun startVolumeObserver() {
        volumeObserver = VolumeObserver(this) {
            scheduleVolumeRestore()
        }

        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            volumeObserver!!
        )
        Log.d(TAG, "Volume observer registered")
    }

    private fun stopVolumeObserver() {
        volumeObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        volumeObserver = null
    }

    /**
     * Планирует восстановление громкости через 1.5 часа.
     * Использует WorkManager для надёжности - таймер сработает даже после перезагрузки.
     */
    private fun scheduleVolumeRestore() {
        val workRequest = OneTimeWorkRequestBuilder<VolumeRestoreWorker>()
            .setInitialDelay(RESTORE_DELAY_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,  // Заменяем предыдущий таймер
            workRequest
        )

        Log.d(TAG, "Volume restore scheduled in $RESTORE_DELAY_MINUTES minutes")

        // Обновляем уведомление с информацией о таймере
        val notification = createNotification(
            "Громкость будет восстановлена через ${RESTORE_DELAY_MINUTES / 60.0} ч"
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String = "Отслеживание громкости активно"): Notification {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Volumer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Мониторинг громкости",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Показывает статус отслеживания громкости"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "VolumeMonitorService"
        private const val NOTIFICATION_ID = 1000
        private const val CHANNEL_ID = "volume_monitor_channel"
        private const val WORK_NAME = "volume_restore_work"
        private const val WORK_TAG = "volume_restore"

        // 1.5 часа = 90 минут
        const val RESTORE_DELAY_MINUTES = 90L
    }
}
