package com.vadim170.bitchatscanner.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val notifyEnabled: Boolean = false,
    val showClearDialog: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScannerRepository.getInstance(application)
    
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
                    BleScannerService.ACTION_LOG_LINE -> {
                        val line = intent.getStringExtra(BleScannerService.EXTRA_LINE) ?: return
                        repository.addLogLine(line)

                        if (line.contains(", RSSI ")) {
                            viewModelScope.launch {
                                repository.refreshDevices()
                            }
                        }
                    }
                    BleScannerService.ACTION_SCANNER_STARTED -> {
                        _uiState.value = _uiState.value.copy(isScannerRunning = true)
                    }
                    BleScannerService.ACTION_SCANNER_STOPPED -> {
                        _uiState.value = _uiState.value.copy(isScannerRunning = false)
                    }
                }
            }
        }
        
        val intentFilter = IntentFilter().apply {
            addAction(BleScannerService.ACTION_LOG_LINE)
            addAction(BleScannerService.ACTION_SCANNER_STARTED)
            addAction(BleScannerService.ACTION_SCANNER_STOPPED)
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

        val isServiceRunning = isServiceRunning(BleScannerService::class.java)
        _uiState.value = _uiState.value.copy(isScannerRunning = isServiceRunning)
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
    
    fun startScanner() {

        _uiState.value = _uiState.value.copy(isScannerRunning = true)
        
        val intent = Intent(getApplication(), BleScannerService::class.java)
        ContextCompat.startForegroundService(getApplication(), intent)
        

        viewModelScope.launch {
            delay(500)
            checkScannerState()
        }
    }
    
    fun stopScanner() {

        _uiState.value = _uiState.value.copy(isScannerRunning = false)
        
        getApplication<Application>().stopService(Intent(getApplication(), BleScannerService::class.java))
        

        viewModelScope.launch {
            delay(500)
            checkScannerState()
        }
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
    }
}