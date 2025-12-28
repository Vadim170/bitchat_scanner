package com.vadim170.bitchatscanner

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Singleton manager для BLE-сканирования устройств BitChat.
 * Обеспечивает работу сканирования в двух режимах:
 * - IN_APP: сканирование когда приложение открыто, но сервис не запущен
 * - SERVICE: сканирование через foreground service в фоне
 * 
 * Гарантирует, что одновременно работает только один экземпляр сканера.
 */
class BleScannerManager private constructor(
    private val context: Context
) {
    
    /**
     * Режимы работы сканера
     */
    enum class ScanMode {
        IN_APP,   // Сканирование внутри приложения
        SERVICE   // Сканирование через foreground service
    }
    
    companion object {
        const val ACTION_LOG_LINE = "com.vadim170.bitchatscanner.LOG_LINE"
        const val ACTION_SCANNER_STARTED = "com.vadim170.bitchatscanner.SCANNER_STARTED"
        const val ACTION_SCANNER_STOPPED = "com.vadim170.bitchatscanner.SCANNER_STOPPED"
        const val EXTRA_LINE = "line"
        
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        
        @Volatile
        private var INSTANCE: BleScannerManager? = null
        
        fun getInstance(context: Context): BleScannerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BleScannerManager(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }
    
    private val db by lazy { DetectionDbHelper(context) }
    private val io = Executors.newSingleThreadExecutor()
    
    // Текущий активный режим сканирования
    @Volatile
    private var currentMode: ScanMode? = null
    
    // Callback для BLE сканера
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
            FirebaseCrashlytics.getInstance().log("BLE Scan Failed: errorCode=$errorCode")
        }
    }
    
    /**
     * Запускает сканирование в указанном режиме.
     * Если сканирование уже активно в другом режиме, сначала останавливает его.
     * 
     * @param mode Режим сканирования (IN_APP или SERVICE)
     * @return true если сканирование успешно запущено, false в противном случае
     */
    @Synchronized
    fun startScanning(mode: ScanMode): Boolean {
        // Если уже сканируем в этом режиме, ничего не делаем
        if (currentMode == mode) {
            FirebaseCrashlytics.getInstance().log("Scanning already active in mode: $mode")
            return true
        }
        
        // Если сканируем в другом режиме, останавливаем
        if (currentMode != null) {
            FirebaseCrashlytics.getInstance().log("Stopping scan in mode $currentMode, switching to $mode")
            stopScanning()
        }
        
        if (!hasScanPermission()) {
            sendLine("${sdf.format(Date())},no_scan_permission")
            FirebaseCrashlytics.getInstance().log("BleScannerManager: No scan permission")
            return false
        }
        
        if (!startScan()) {
            return false
        }
        
        currentMode = mode
        FirebaseCrashlytics.getInstance().log("BleScannerManager: Started scanning in mode $mode")
        return true
    }
    
    /**
     * Останавливает активное сканирование.
     */
    @Synchronized
    fun stopScanning() {
        if (currentMode == null) {
            return
        }
        
        if (hasScanPermission()) {
            stopScan()
        }
        
        FirebaseCrashlytics.getInstance().log("BleScannerManager: Stopped scanning in mode $currentMode")
        currentMode = null
    }
    
    /**
     * Проверяет, активно ли сканирование в данный момент.
     */
    @Synchronized
    fun isScanning(): Boolean = currentMode != null
    
    /**
     * Получает текущий режим сканирования, если оно активно.
     */
    @Synchronized
    fun getCurrentMode(): ScanMode? = currentMode
    
    /**
     * Обрабатывает результат сканирования BLE устройства
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleResult(r: ScanResult) {
        val record = r.scanRecord ?: return
        val svc = ParcelUuid(BitchatBle.SERVICE_UUID)

        // Учитываем все варианты: Service UUID list, Service Data keys, Service Solicitation UUIDs
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

        // Сохраняем в БД (и подрезаем историю до 1000)
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
                FirebaseCrashlytics.getInstance().log("Error saving detection to DB: $addr")
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }

        // Отправим строку для UI-логов
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
    }
    
    /**
     * Запускает BLE сканирование
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScan(): Boolean {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = mgr.adapter
        if (adapter == null) {
            sendLine("${sdf.format(Date())},bluetooth_not_available")
            return false
        }
        
        if (!adapter.isEnabled) {
            sendLine("${sdf.format(Date())},bluetooth_disabled")
            return false
        }
        
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            sendLine("${sdf.format(Date())},scanner_not_available")
            return false
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Без фильтров — отфильтруем в handleResult()
        scanner.startScan(null, settings, callback)
        sendLine("${sdf.format(Date())},scan_started")
        context.sendBroadcast(Intent(ACTION_SCANNER_STARTED))
        return true
    }
    
    /**
     * Останавливает BLE сканирование
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopScan() {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = mgr.adapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        scanner.stopScan(callback)
        sendLine("${sdf.format(Date())},scan_stopped")
        context.sendBroadcast(Intent(ACTION_SCANNER_STOPPED))
    }
    
    /**
     * Отправляет строку лога через broadcast
     */
    private fun sendLine(line: String) {
        context.sendBroadcast(Intent(ACTION_LOG_LINE).putExtra(EXTRA_LINE, line))
        // Логируем важные события в Crashlytics
        if (line.contains("scan_failed") || line.contains("bluetooth_disabled") || 
            line.contains("no_scan_permission") || line.contains("scan_started") || 
            line.contains("scan_stopped")) {
            FirebaseCrashlytics.getInstance().log(line)
        }
    }
    
    /**
     * Проверяет наличие разрешения на BLE сканирование
     */
    private fun hasScanPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
    
    /**
     * Возвращает «лучшую» lastKnown локацию (если есть разрешение), иначе null.
     */
    private fun getBestLastKnownLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            val loc = lm.getLastKnownLocation(p) ?: continue
            if (best == null || loc.time > best.time) best = loc
        }
        return best
    }
    
    /**
     * Освобождает ресурсы при завершении работы
     */
    fun cleanup() {
        stopScanning()
        io.shutdown()
    }
}
