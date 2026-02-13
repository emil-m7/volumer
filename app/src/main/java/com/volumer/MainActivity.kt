package com.volumer

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.volumer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy {
        getSharedPreferences(BootReceiver.PREFS_NAME, MODE_PRIVATE)
    }

    private var isServiceRunning: Boolean
        get() = prefs.getBoolean(BootReceiver.KEY_SERVICE_RUNNING, false)
        set(value) = prefs.edit().putBoolean(BootReceiver.KEY_SERVICE_RUNNING, value).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissions()
        updateServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        updateTimerStatus()
    }

    private fun setupUI() {
        binding.toggleServiceButton.setOnClickListener {
            if (isServiceRunning) {
                stopMonitoringService()
            } else {
                if (checkAllPermissions()) {
                    startMonitoringService()
                } else {
                    checkPermissions()
                }
            }
        }

        binding.cancelTimerButton.setOnClickListener {
            cancelScheduledRestore()
        }
    }

    private fun checkPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        // Проверка разрешения на уведомления (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Проверка разрешения Do Not Disturb
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            showDndPermissionDialog()
            return
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun checkAllPermissions(): Boolean {
        // Проверка разрешения на уведомления (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        // Проверка разрешения Do Not Disturb
        val notificationManager = getSystemService(NotificationManager::class.java)
        return notificationManager.isNotificationPolicyAccessGranted
    }

    private fun showDndPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Требуется разрешение")
            .setMessage("Для управления режимом звонка необходимо разрешение \"Доступ к режиму Не беспокоить\"")
            .setPositiveButton("Открыть настройки") { _, _ ->
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startMonitoringService() {
        val serviceIntent = Intent(this, VolumeMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isServiceRunning = true
        updateServiceStatus()
        Toast.makeText(this, "Мониторинг запущен", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoringService() {
        val serviceIntent = Intent(this, VolumeMonitorService::class.java)
        stopService(serviceIntent)
        isServiceRunning = false
        updateServiceStatus()
        cancelScheduledRestore()
        Toast.makeText(this, "Мониторинг остановлен", Toast.LENGTH_SHORT).show()
    }

    private fun cancelScheduledRestore() {
        WorkManager.getInstance(this).cancelUniqueWork("volume_restore_work")
        updateTimerStatus()
        Toast.makeText(this, "Таймер отменён", Toast.LENGTH_SHORT).show()
    }

    private fun updateServiceStatus() {
        // Простая проверка - можно улучшить через SharedPreferences
        binding.serviceStatusText.text = if (isServiceRunning) {
            "Статус: Активен"
        } else {
            "Статус: Остановлен"
        }

        binding.toggleServiceButton.text = if (isServiceRunning) {
            "Остановить мониторинг"
        } else {
            "Запустить мониторинг"
        }
    }

    private fun updateTimerStatus() {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData("volume_restore_work")
            .observe(this) { workInfos ->
                val workInfo = workInfos?.firstOrNull()
                when (workInfo?.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> {
                        binding.timerStatusText.text = "Таймер: Активен (громкость восстановится через 1.5 ч)"
                        binding.cancelTimerButton.isEnabled = true
                    }
                    else -> {
                        binding.timerStatusText.text = "Таймер: Не активен"
                        binding.cancelTimerButton.isEnabled = false
                    }
                }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Все разрешения получены
                updateServiceStatus()
            } else {
                Toast.makeText(
                    this,
                    "Необходимы разрешения для работы приложения",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
