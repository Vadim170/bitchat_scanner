package com.vadim170.bitchatscanner.repository

import android.content.Context
import com.vadim170.bitchatscanner.DetectionDbHelper
import com.vadim170.bitchatscanner.DetectionRow
import com.vadim170.bitchatscanner.DeviceSummary
import com.vadim170.bitchatscanner.LocationPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ScannerRepository private constructor(context: Context) {
    private val db = DetectionDbHelper(context.applicationContext)
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    // Состояние для логов
    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    val logLines: Flow<List<String>> = _logLines.asStateFlow()
    
    // Состояние для устройств
    private val _devices = MutableStateFlow<List<DeviceSummary>>(emptyList())
    val devices: Flow<List<DeviceSummary>> = _devices.asStateFlow()
    
    companion object {
        @Volatile
        private var INSTANCE: ScannerRepository? = null
        
        fun getInstance(context: Context): ScannerRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScannerRepository(context).also { INSTANCE = it }
            }
        }
    }
    
    suspend fun loadInitialLogs() {
        withContext(Dispatchers.IO) {
            val rows = db.latest(1000)
            val initial = rows.map { row ->
                buildString {
                    append(sdf.format(Date(row.timestamp)))
                    append(",")
                    append(row.address)
                    append(", RSSI ")
                    append(row.rssi)
                    if (!row.name.isNullOrEmpty()) {
                        append(", "); append(row.name)
                    }
                    if (row.lat != null && row.lon != null) {
                        append(", "); append("lat="); append(row.lat)
                        append(", lon="); append(row.lon)
                    }
                    if (!row.serviceDataHex.isNullOrEmpty()) {
                        append(", sd="); append(row.serviceDataHex.take(16)); append("…")
                    }
                }
            }
            _logLines.value = initial
        }
    }
    
    suspend fun loadDevices() {
        withContext(Dispatchers.IO) {
            val deviceList = db.getAllDevices()
            _devices.value = deviceList
        }
    }
    
    suspend fun loadRecentDevices(hoursBack: Int = 1) {
        withContext(Dispatchers.IO) {
            val deviceList = db.getRecentDevices(hoursBack)
            _devices.value = deviceList
        }
    }
    
    fun addLogLine(line: String) {
        val currentLogs = _logLines.value.toMutableList()
        currentLogs.add(0, line)
        if (currentLogs.size > 1000) {
            currentLogs.removeLast()
        }
        _logLines.value = currentLogs
    }
    
    suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            db.clearAll()
            _logLines.value = emptyList()
            _devices.value = emptyList()
        }
    }
    
    // Метод для обновления устройств при новом обнаружении
    suspend fun refreshDevices() {
        loadDevices()
    }
    
    suspend fun getDeviceLocations(deviceAddress: String): List<LocationPoint> {
        return withContext(Dispatchers.IO) {
            db.getDeviceLocations(deviceAddress)
        }
    }
}