package com.vadim170.bitchatscanner

import android.Manifest
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission

/**
 * BroadcastReceiver для обработки результатов пассивного BLE сканирования.
 * Получает события от системы через PendingIntent при обнаружении устройств.
 */
class PassiveScanReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_FOUND = "com.vadim170.bitchatscanner.PASSIVE_SCAN_FOUND"
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        when (intent.action) {
            ACTION_FOUND -> {
                // Извлекаем результаты сканирования из intent
                val scanResults = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(
                        "android.bluetooth.le.extra.LIST_SCAN_RESULT",
                        ScanResult::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<ScanResult>(
                        "android.bluetooth.le.extra.LIST_SCAN_RESULT"
                    )
                }
                
                scanResults?.forEach { result ->
                    // Передаем результаты в BleScannerManager для обработки
                    BleScannerManager.getInstance(context).handlePassiveScanResult(result)
                }
            }
        }
    }
}
