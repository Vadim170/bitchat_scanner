package com.vadim170.bitchatscanner

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BleScannerService : Service() {

    companion object {
        const val ACTION_LOG_LINE = "com.vadim170.bitchatscanner.LOG_LINE"
        const val EXTRA_LINE = "line"
        private const val CH_ID = "scan"
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    private val logFile by lazy { File(filesDir, "bitchat_scan.csv") }
    private val notifyIntent by lazy { PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE) }

    private val callback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleResult)
        }
        override fun onScanFailed(errorCode: Int) { log("scan_failed,$errorCode") }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleResult(r: ScanResult) {
        val record = r.scanRecord
        val hasService = record?.serviceUuids?.contains(ParcelUuid(BitchatBle.SERVICE_UUID)) == true
        val hasServiceData = record?.serviceData?.keys?.any { it.uuid == BitchatBle.SERVICE_UUID } == true
        if (!hasService && !hasServiceData) return

        val now = sdf.format(Date())
        val addr = r.device.address ?: "unknown"
        val name = r.device.name ?: record?.deviceName ?: ""   // <-- null-safe
        val rssi = r.rssi
        val line = "$now,$addr,$rssi,${name.replace(",", " ")}"

        appendLog(line)
        sendLine(line)
        maybeNotify(addr, rssi, name)
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()

        // Было: startForeground(1, baseNotif("Сканирование BLE…"))
        ServiceCompat.startForeground(
            this,
            1,
            baseNotif("Сканирование BLE…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        if (!logFile.exists()) {
            logFile.writeText("timestamp,address,rssi,name\n")
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) return

        startScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        stopScan()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScan() {
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = mgr.adapter ?: return
        if (!adapter.isEnabled) {
            log("bluetooth_disabled")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BitchatBle.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        scanner.startScan(listOf(filter), settings, callback)
        log("scan_started")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopScan() {
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = mgr.adapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        scanner.stopScan(callback)
        log("scan_stopped")
    }

    private fun appendLog(line: String) {
        logFile.appendText(line + "\n")
    }

    private fun sendLine(line: String) {
        sendBroadcast(Intent(ACTION_LOG_LINE).putExtra(EXTRA_LINE, line))
    }

    private fun maybeNotify(addr: String, rssi: Int, name: String?) {
        val enabled = getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("notify", false)
        if (!enabled) return
        val text = "${name ?: addr} (RSSI $rssi)"
        val n = NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Обнаружен узел bitchat")
            .setContentText(text)
            .setContentIntent(notifyIntent)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(addr.hashCode(), n)
    }

    private fun baseNotif(text: String) = NotificationCompat.Builder(this, CH_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle("Bitchat Scanner")
        .setContentText(text)
        .setContentIntent(notifyIntent)
        .build()

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH_ID, "Scanning", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun log(s: String) {
        val line = "${sdf.format(Date())},$s"
        appendLog(line); sendLine(line)
    }
}