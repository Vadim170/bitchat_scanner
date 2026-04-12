package com.vadim170.bitchatscanner

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class BleScannerService : Service() {

    companion object {
        private const val TAG = "BleScannerService"
        const val ACTION_LOG_LINE = "com.vadim170.bitchatscanner.LOG_LINE"
        const val ACTION_SCANNER_STARTED = "com.vadim170.bitchatscanner.SCANNER_STARTED"
        const val ACTION_SCANNER_STOPPED = "com.vadim170.bitchatscanner.SCANNER_STOPPED"
        const val EXTRA_LINE = "line"
        private const val CH_ID = "scan"
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    private val notifyIntent by lazy {
        PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val db by lazy { DetectionDbHelper(applicationContext) }
    private val io = Executors.newSingleThreadExecutor()

    private val callback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleResult)
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMsg = "${sdf.format(Date())},scan_failed,$errorCode"
            sendLine(errorMsg)
            Log.w(TAG, "BLE scan failed: errorCode=$errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleResult(r: ScanResult) {
        val record = r.scanRecord ?: return
        val svc = ParcelUuid(BitchatBle.SERVICE_UUID)


        val matches =
            (record.serviceUuids?.contains(svc) == true) ||
                    (record.serviceData?.keys?.any { it.uuid == BitchatBle.SERVICE_UUID } == true) ||
                    (record.serviceSolicitationUuids?.contains(svc) == true)

        if (!matches) return

        val nowMs = System.currentTimeMillis()
        val addr = r.device.address ?: "unknown"
        val name = r.device.name ?: record.deviceName ?: ""
        val rssi = r.rssi

        val sdBytes = record.serviceData?.get(svc)
        val serviceDataHex = sdBytes?.joinToString("") { "%02X".format(it) }

        val loc = getBestLastKnownLocation()
        val lat = loc?.latitude
        val lon = loc?.longitude
        val acc = loc?.accuracy
        val provider = loc?.provider


        io.execute {
            try {
                db.insertAndPrune(
                    DetectionRow(
                        timestamp = nowMs,
                        address = addr,
                        name = name,
                        rssi = rssi,
                        lat = lat,
                        lon = lon,
                        accuracy = acc,
                        provider = provider,
                        serviceDataHex = serviceDataHex
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error saving detection to DB: $addr", e)
            }
        }


        val lineHuman = buildString {
            append(sdf.format(Date(nowMs)))
            append(",")
            append(addr)
            append(", RSSI ")
            append(rssi)
            if (name.isNotEmpty()) { append(", "); append(name) }
            if (lat != null && lon != null) { append(", "); append("lat="); append(lat); append(", lon="); append(lon) }
            if (!serviceDataHex.isNullOrEmpty()) { append(", sd="); append(serviceDataHex.take(16)); append("…") }
        }
        sendLine(lineHuman)

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
                baseNotif(getString(R.string.notification_scanning)),
                types
            )

            Log.i(TAG, "Service started")

            if (!hasScanPermission()) {
                sendLine("${sdf.format(Date())},no_scan_permission")
                Log.w(TAG, "No scan permission")
                return
            }
            startScan()
        } catch (e: Exception) {
            Log.e(TAG, "Service startup failed", e)
            throw e
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (hasScanPermission()) stopScan()
        io.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScan() {
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = mgr.adapter ?: return
        if (!adapter.isEnabled) {
            sendLine("${sdf.format(Date())},bluetooth_disabled")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()


        scanner.startScan(callback)
        sendLine("${sdf.format(Date())},scan_started")
        sendBroadcast(Intent(ACTION_SCANNER_STARTED))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopScan() {
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = mgr.adapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        scanner.stopScan(callback)
        sendLine("${sdf.format(Date())},scan_stopped")
        sendBroadcast(Intent(ACTION_SCANNER_STOPPED))
    }

    private fun sendLine(line: String) {
        sendBroadcast(Intent(ACTION_LOG_LINE).putExtra(EXTRA_LINE, line))

        if (line.contains("scan_failed") || line.contains("bluetooth_disabled") || 
            line.contains("no_scan_permission") || line.contains("scan_started") || 
            line.contains("scan_stopped")) {
            Log.i(TAG, line)
        }
    }

    private fun maybeNotify(addr: String, rssi: Int, name: String?) {
        val enabled = getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("notify", false)
        if (!enabled) return
        val text = "${name ?: addr} (RSSI $rssi)"
        val n = NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.notification_node_detected))
            .setContentText(text)
            .setContentIntent(notifyIntent)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(addr.hashCode(), n)
    }

    private fun baseNotif(text: String) = NotificationCompat.Builder(this, CH_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle(getString(R.string.app_name))
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

    private fun hasScanPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED

    private fun getBestLastKnownLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            val loc = lm.getLastKnownLocation(p) ?: continue
            if (best == null || loc.time > best!!.time) best = loc
        }
        return best
    }
}
