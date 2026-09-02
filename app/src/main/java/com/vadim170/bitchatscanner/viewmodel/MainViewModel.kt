package com.vadim170.bitchatscanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vadim170.bitchatscanner.BleScanCoordinator
import com.vadim170.bitchatscanner.repository.ScannerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainScreenState(
    val logLines: List<String> = emptyList(),
    val scannerState: BleScanCoordinator.State = BleScanCoordinator.State.STOPPED,
    /** Kept for callers that only need a simple running/not-running signal. */
    val isScannerRunning: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val showClearDialog: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScannerRepository.getInstance(application)
    private val scanCoordinator = BleScanCoordinator.getInstance(application)

    private val _uiState = MutableStateFlow(
        MainScreenState(notificationsEnabled = scanCoordinator.notificationsEnabled())
    )
    val uiState: StateFlow<MainScreenState> = _uiState.asStateFlow()

    init {
        observeStoredLogs()
        observeScanner()
    }

    private fun observeStoredLogs() {
        viewModelScope.launch {
            repository.loadInitialLogs()
            repository.logLines.collect { logs ->
                _uiState.value = _uiState.value.copy(logLines = logs)
            }
        }
    }

    private fun observeScanner() {
        viewModelScope.launch {
            scanCoordinator.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    scannerState = state,
                    isScannerRunning = state == BleScanCoordinator.State.ACTIVE_VISIBLE ||
                            state == BleScanCoordinator.State.PASSIVE_BACKGROUND ||
                            state == BleScanCoordinator.State.SCANNING,
                )
            }
        }

        viewModelScope.launch {
            scanCoordinator.events.collect { event ->
                repository.addLogLine(event.line)
                if (event.line.contains(", RSSI ")) {
                    repository.refreshDevices()
                }
            }
        }
    }

    /** Persist a user Start intent; the coordinator selects callback or PI mode. */
    fun startScanner() {
        scanCoordinator.startSession()
    }

    fun stopScanner() {
        scanCoordinator.stopSession()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        scanCoordinator.setNotificationsEnabled(enabled)
        _uiState.value = _uiState.value.copy(notificationsEnabled = enabled)
    }

    /** Reloads rows that may have been written by the background receiver. */
    fun reloadFromStorage() {
        viewModelScope.launch {
            repository.loadInitialLogs()
            repository.refreshDevices()
            _uiState.value = _uiState.value.copy(
                notificationsEnabled = scanCoordinator.notificationsEnabled(),
            )
        }
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
}
