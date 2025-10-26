package com.vadim170.bitchatscanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vadim170.bitchatscanner.DeviceSummary
import com.vadim170.bitchatscanner.repository.ScannerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DevicesScreenState(
    val devices: List<DeviceSummary> = emptyList(),
    val isLoading: Boolean = true,
    val showRecentOnly: Boolean = false
)

class DevicesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScannerRepository.getInstance(application)
    
    private val _uiState = MutableStateFlow(DevicesScreenState())
    val uiState: StateFlow<DevicesScreenState> = _uiState.asStateFlow()
    
    init {
        loadInitialDevices()
        observeDevicesUpdates()
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
                _uiState.value = _uiState.value.copy(
                    devices = devices,
                    isLoading = false
                )
            }
        }
    }
    
    fun toggleFilter() {
        val newShowRecentOnly = !_uiState.value.showRecentOnly
        _uiState.value = _uiState.value.copy(showRecentOnly = newShowRecentOnly)
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            if (newShowRecentOnly) {
                repository.loadRecentDevices(1) // последний час
            } else {
                repository.loadDevices() // все устройства
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}