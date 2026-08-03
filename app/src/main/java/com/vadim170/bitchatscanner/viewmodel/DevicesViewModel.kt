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
    val isLoading: Boolean = true
)

class DevicesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScannerRepository.getInstance(application)

    private val _uiState = MutableStateFlow(DevicesScreenState())
    val uiState: StateFlow<DevicesScreenState> = _uiState.asStateFlow()

    init {
        observeDevices()
        refresh()
    }

    private fun observeDevices() {
        viewModelScope.launch {
            repository.devices.collect { devices ->
                _uiState.value = _uiState.value.copy(
                    devices = devices.sortedByDescending { it.lastSeen },
                    isLoading = false
                )
            }
        }
    }

    /** Reloads persisted data when the main screen becomes visible again. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.loadDevices()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}
