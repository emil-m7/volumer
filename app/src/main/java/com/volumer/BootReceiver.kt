package com.volumer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receiver для автозапуска сервиса после перезагрузки устройства.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking if service should start")

            // Проверяем, был ли сервис активен до перезагрузки
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val wasServiceRunning = prefs.getBoolean(KEY_SERVICE_RUNNING, false)

            if (wasServiceRunning) {
                Log.d(TAG, "Starting VolumeMonitorService after boot")
                val serviceIntent = Intent(context, VolumeMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
        const val PREFS_NAME = "volumer_prefs"
        const val KEY_SERVICE_RUNNING = "service_running"
    }
}
