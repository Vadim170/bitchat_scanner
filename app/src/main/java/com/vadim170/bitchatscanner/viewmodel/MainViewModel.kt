package com.vadim170.bitchatscanner.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vadim170.bitchatscanner.BleScannerManager
import com.vadim170.bitchatscanner.BleScannerService
import com.vadim170.bitchatscanner.repository.ScannerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainScreenState(
    val logLines: List<String> = emptyList(),
    val isScannerRunning: Boolean = false,
    val scanningMode: ScanningMode = ScanningMode.NONE,
    val notifyEnabled: Boolean = false,
    val showClearDialog: Boolean = false
)

enum class ScanningMode {
    NONE,       // Сканирование не активно
    IN_APP,     // Сканирование внутри приложения
    SERVICE     // Сканирование через service
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScannerRepository.getInstance(application)
    private val scannerManager = BleScannerManager.getInstance(application)
    
    private val _uiState = MutableStateFlow(MainScreenState())
    val uiState: StateFlow<MainScreenState> = _uiState.asStateFlow()
    
    private var broadcastReceiver: BroadcastReceiver? = null
    
    init {
        loadInitialData()
        setupBroadcastReceiver()
        loadPreferences()
        checkScannerState()
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            repository.loadInitialLogs()
            repository.logLines.collect { logs ->
                _uiState.value = _uiState.value.copy(logLines = logs)
            }
        }
    }
    
    private fun setupBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BleScannerManager.ACTION_LOG_LINE -> {
                        val line = intent.getStringExtra(BleScannerManager.EXTRA_LINE) ?: return
                        repository.addLogLine(line)
                        // Обновляем устройства при новом обнаружении (только если это обнаружение устройства)
                        if (line.contains(", RSSI ")) {
                            viewModelScope.launch {
                                repository.refreshDevices()
                            }
                        }
                    }
                    BleScannerManager.ACTION_SCANNER_STARTED -> {
                        updateScannerState()
                    }
                    BleScannerManager.ACTION_SCANNER_STOPPED -> {
                        updateScannerState()
                    }
                }
            }
        }
        
        val intentFilter = IntentFilter().apply {
            addAction(BleScannerManager.ACTION_LOG_LINE)
            addAction(BleScannerManager.ACTION_SCANNER_STARTED)
            addAction(BleScannerManager.ACTION_SCANNER_STOPPED)
        }
        
        ContextCompat.registerReceiver(
            getApplication(),
            broadcastReceiver!!,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    
    private fun loadPreferences() {
        val prefs = getApplication<Application>().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val notifyEnabled = prefs.getBoolean("notify", false)
        _uiState.value = _uiState.value.copy(notifyEnabled = notifyEnabled)
    }
    
    private fun checkScannerState() {
        updateScannerState()
    }
    
    private fun updateScannerState() {
        val isServiceRunning = isServiceRunning(BleScannerService::class.java)
        val isScanning = scannerManager.isScanning()
        val currentMode = scannerManager.getCurrentMode()
        
        val scanningMode = when {
            isServiceRunning && currentMode == BleScannerManager.ScanMode.SERVICE -> ScanningMode.SERVICE
            isScanning && currentMode == BleScannerManager.ScanMode.IN_APP -> ScanningMode.IN_APP
            else -> ScanningMode.NONE
        }
        
        _uiState.value = _uiState.value.copy(
            isScannerRunning = isScanning,
            scanningMode = scanningMode
        )
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    /**
     * Запускает сканирование через foreground service
     */
    fun startScanner() {
        // Обновляем состояние немедленно для быстрой реакции UI
        _uiState.value = _uiState.value.copy(
            isScannerRunning = true,
            scanningMode = ScanningMode.SERVICE
        )
        
        val intent = Intent(getApplication(), BleScannerService::class.java)
        ContextCompat.startForegroundService(getApplication(), intent)
        
        // Проверяем реальное состояние через небольшую задержку
        viewModelScope.launch {
            delay(500)
            updateScannerState()
        }
    }
    
    /**
     * Останавливает сканирование (либо service, либо in-app)
     */
    fun stopScanner() {
        // Обновляем состояние немедленно для быстрой реакции UI
        _uiState.value = _uiState.value.copy(
            isScannerRunning = false,
            scanningMode = ScanningMode.NONE
        )
        
        // Останавливаем service если он запущен
        if (isServiceRunning(BleScannerService::class.java)) {
            getApplication<Application>().stopService(Intent(getApplication(), BleScannerService::class.java))
        }
        
        // Останавливаем in-app сканирование через manager
        scannerManager.stopScanning()
        
        // Проверяем реальное состояние через небольшую задержку
        viewModelScope.launch {
            delay(500)
            updateScannerState()
        }
    }
    
    /**
     * Запускает сканирование внутри приложения (без service)
     */
    fun startInAppScanning() {
        // Останавливаем service если он запущен
        if (isServiceRunning(BleScannerService::class.java)) {
            getApplication<Application>().stopService(Intent(getApplication(), BleScannerService::class.java))
        }
        
        // Запускаем in-app сканирование
        val success = scannerManager.startScanning(BleScannerManager.ScanMode.IN_APP)
        
        _uiState.value = _uiState.value.copy(
            isScannerRunning = success,
            scanningMode = if (success) ScanningMode.IN_APP else ScanningMode.NONE
        )
    }
    
    fun setNotifyEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notifyEnabled = enabled)
        val prefs = getApplication<Application>().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notify", enabled).apply()
    }
    
    fun showClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = true)
    }
    
    fun hideClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = false)
    }
    
    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            hideClearDialog()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        broadcastReceiver?.let { receiver ->
            getApplication<Application>().unregisterReceiver(receiver)
        }
        // Останавливаем in-app сканирование при закрытии ViewModel
        if (scannerManager.getCurrentMode() == BleScannerManager.ScanMode.IN_APP) {
            scannerManager.stopScanning()
        }
    }
}