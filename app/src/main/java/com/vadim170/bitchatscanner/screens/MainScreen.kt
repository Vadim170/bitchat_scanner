package com.vadim170.bitchatscanner.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vadim170.bitchatscanner.R
import com.vadim170.bitchatscanner.components.DeviceCard
import com.vadim170.bitchatscanner.utils.PermissionUtils
import com.vadim170.bitchatscanner.viewmodel.DevicesViewModel
import com.vadim170.bitchatscanner.viewmodel.MainViewModel

/**
 * Главный экран приложения
 * Отображает список обнаруженных устройств и управление сканером
 * 
 * @param onNavigateToLogs Переход к экрану логов
 * @param onNavigateToMap Переход к экрану общей карты
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToLogs: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val mainViewModel: MainViewModel = viewModel()
    val devicesViewModel: DevicesViewModel = viewModel()
    
    val mainUiState by mainViewModel.uiState.collectAsState()
    val devicesUiState by devicesViewModel.uiState.collectAsState()
    
    var showDropdownMenu by remember { mutableStateOf(false) }

    // Лаунчер для повторного запроса разрешений (если потребуется)
    val requestPermsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op */ }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    // Кнопка карты
                    IconButton(onClick = onNavigateToMap) {
                        Icon(
                            Icons.Default.LocationOn, 
                            contentDescription = stringResource(R.string.map)
                        )
                    }
                    
                    // Меню
                    IconButton(onClick = { showDropdownMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert, 
                            contentDescription = stringResource(R.string.menu)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showDropdownMenu,
                        onDismissRequest = { showDropdownMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.detection_log)) },
                            onClick = {
                                showDropdownMenu = false
                                onNavigateToLogs()
                            }
                        )
                    }
                }
            ) 
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 8.dp)
                .fillMaxSize()
        ) {
            // Управление сканером
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (mainUiState.isScannerRunning) {
                    Button(onClick = { mainViewModel.stopScanner() }) { 
                        Text(stringResource(R.string.stop_scanner)) 
                    }
                } else {
                    Button(onClick = { mainViewModel.startScanner() }) { 
                        Text(stringResource(R.string.start_scanner)) 
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Переключатель уведомлений
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Switch(
                    checked = mainUiState.notifyEnabled,
                    onCheckedChange = { mainViewModel.setNotifyEnabled(it) }
                )
                Text(
                    text = stringResource(R.string.notify_detections), 
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(Modifier.height(16.dp))

            // Список устройств
            when {
                devicesUiState.isLoading -> {
                    Text(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                devicesUiState.devices.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_devices_found),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                else -> {
                    val listState = rememberLazyListState()
                    
                    // Отслеживаем видимые элементы для оптимизации загрузки карт
                    val visibleKeys by remember(listState) {
                        derivedStateOf {
                            listState.layoutInfo.visibleItemsInfo
                                .mapNotNull { it.key as? String }
                                .toSet()
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = devicesUiState.devices,
                            key = { device -> device.address }
                        ) { device ->
                            DeviceCard(
                                device = device,
                                showMap = visibleKeys.contains(device.address)
                            )
                        }
                    }
                }
            }
        }
    }
}
