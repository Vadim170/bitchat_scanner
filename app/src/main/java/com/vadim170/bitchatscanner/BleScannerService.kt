package com.vadim170.bitchatscanner

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Foreground service для BLE сканирования в фоновом режиме.
 * Использует BleScannerManager для выполнения фактического сканирования.
 */
class BleScannerService : Service() {

    companion object {
        private const val CH_ID = "scan"
    }

    private val notifyIntent by lazy {
        PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val scannerManager by lazy { BleScannerManager.getInstance(applicationContext) }
    
    // Receiver для обнаружений устройств и отправки уведомлений
    private val detectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BleScannerManager.ACTION_LOG_LINE) {
                val line = intent.getStringExtra(BleScannerManager.EXTRA_LINE) ?: return
                // Если это обнаружение устройства, показываем уведомление
                if (line.contains(", RSSI ")) {
                    parseAndNotify(line)
                }
            }
        }
    }
    
    /**
     * Парсит строку обнаружения и показывает уведомление при необходимости
     */
    private fun parseAndNotify(line: String) {
        val parts = line.split(",")
        if (parts.size < 3) return
        
        val addr = parts[1].trim()
        val rssiPart = parts.find { it.contains("RSSI") }?.trim() ?: return
        val rssi = rssiPart.substringAfter("RSSI").trim().toIntOrNull() ?: return
        val name = parts.find { !it.contains(":") && !it.contains("RSSI") && !it.contains("lat=") && !it.contains("lon=") && !it.contains("sd=") }?.trim()
        
        maybeNotify(addr, rssi, name)
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createChannel()

            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

            ServiceCompat.startForeground(
                this,
                1,
                baseNotif("Сканирование BLE…"),
                types
            )

            FirebaseCrashlytics.getInstance().log("BleScannerService onCreate: service started")

            // Регистрируем receiver для обнаружений
            val intentFilter = IntentFilter(BleScannerManager.ACTION_LOG_LINE)
            ContextCompat.registerReceiver(
                this,
                detectionReceiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            
            // Запускаем сканирование через manager в режиме SERVICE
            scannerManager.startScanning(BleScannerManager.ScanMode.SERVICE)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            throw e
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Останавливаем сканирование через manager
        scannerManager.stopScanning()
        
        // Отключаем receiver
        try {
            unregisterReceiver(detectionReceiver)
        } catch (e: Exception) {
            // Игнорируем ошибки при отключении
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun maybeNotify(addr: String, rssi: Int, name: String?) {
        val enabled = getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("notify", false)
        if (!enabled) return
        val text = "${name ?: addr} (RSSI $rssi)"
        val n = NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Обнаружен узел BitChat")
            .setContentText(text)
            .setContentIntent(notifyIntent)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(addr.hashCode(), n)
    }

    private fun baseNotif(text: String) = NotificationCompat.Builder(this, CH_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle("BitChat Scanner")
        .setContentText(text)
        .setContentIntent(notifyIntent)
        .build()

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ID, "Scanning", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
