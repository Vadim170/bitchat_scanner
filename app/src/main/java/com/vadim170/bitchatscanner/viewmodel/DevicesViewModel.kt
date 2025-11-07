package com.vadim170.bitchatscanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vadim170.bitchatscanner.BleScannerService
import com.vadim170.bitchatscanner.DeviceSummary
import com.vadim170.bitchatscanner.repository.ScannerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DevicesScreenState(
    val devices: List<DeviceSummary> = emptyList(),
    val isLoading: Boolean = true
)

class DevicesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScannerRepository.getInstance(application)
    
    private val _uiState = MutableStateFlow(DevicesScreenState())
    val uiState: StateFlow<DevicesScreenState> = _uiState.asStateFlow()
    
    init {
        loadInitialDevices()
        observeDevicesUpdates()
        startPeriodicRefresh()
    }
    
    private fun loadInitialDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.loadDevices()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
    
    private fun observeDevicesUpdates() {
        viewModelScope.launch {
            repository.devices.collect { devices ->
                // Сортируем устройства по времени последнего обнаружения (свежие сверху)
                val sortedDevices = devices.sortedByDescending { it.lastSeen }
                _uiState.value = _uiState.value.copy(
                    devices = sortedDevices,
                    isLoading = false
                )
            }
        }
    }
    
    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(2000) // Обновляем каждые 2 секунды
                // Обновляем устройства только если сканер работает
                if (isServiceRunning(BleScannerService::class.java)) {
                    repository.refreshDevices()
                }
            }
        }
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getApplication<Application>().getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}