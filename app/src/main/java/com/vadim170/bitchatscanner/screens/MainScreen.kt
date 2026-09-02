package com.vadim170.bitchatscanner.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.vadim170.bitchatscanner.R
import com.vadim170.bitchatscanner.components.DeviceCard
import com.vadim170.bitchatscanner.BleScanCoordinator
import com.vadim170.bitchatscanner.utils.PermissionUtils
import com.vadim170.bitchatscanner.viewmodel.DevicesViewModel
import com.vadim170.bitchatscanner.viewmodel.MainViewModel

/**
 * 
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) mainViewModel.setNotificationsEnabled(true)
    }
    var showDropdownMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        mainViewModel.reloadFromStorage()
        devicesViewModel.refresh()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainViewModel.reloadFromStorage()
                devicesViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {

                    IconButton(onClick = onNavigateToMap) {
                        Icon(
                            Icons.Default.LocationOn, 
                            contentDescription = stringResource(R.string.map)
                        )
                    }
                    

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (mainUiState.scannerState == BleScanCoordinator.State.ACTIVE_VISIBLE ||
                    mainUiState.scannerState == BleScanCoordinator.State.PASSIVE_BACKGROUND ||
                    mainUiState.scannerState == BleScanCoordinator.State.SCANNING
                ) {
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
            Text(
                text = stringResource(R.string.scan_visibility_note),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = when (mainUiState.scannerState) {
                    BleScanCoordinator.State.ACTIVE_VISIBLE,
                    BleScanCoordinator.State.SCANNING -> stringResource(R.string.scan_state_visible)
                    BleScanCoordinator.State.PASSIVE_BACKGROUND ->
                        stringResource(R.string.scan_state_background)
                    BleScanCoordinator.State.PERMISSION_REQUIRED ->
                        stringResource(R.string.scan_state_permission_required)
                    BleScanCoordinator.State.BLUETOOTH_DISABLED ->
                        stringResource(R.string.scan_state_bluetooth_disabled)
                    BleScanCoordinator.State.LOCATION_DISABLED ->
                        stringResource(R.string.scan_state_location_disabled)
                    BleScanCoordinator.State.UNAVAILABLE ->
                        stringResource(R.string.scan_state_unavailable)
                    BleScanCoordinator.State.ERROR -> stringResource(R.string.scan_state_error)
                    BleScanCoordinator.State.STOPPED,
                    BleScanCoordinator.State.IDLE -> stringResource(R.string.scan_state_stopped)
                },
                style = MaterialTheme.typography.bodyMedium
            )

            if (mainUiState.scannerState == BleScanCoordinator.State.LOCATION_DISABLED) {
                // Android silently drops BLE results for a location-attributed
                // app while Location Services are off; send the user straight
                // to the system toggle. onResume retries the persisted session.
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    }
                ) {
                    Text(stringResource(R.string.open_location_settings))
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notifications_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.notifications_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = mainUiState.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            mainViewModel.setNotificationsEnabled(false)
                        } else {
                            val permission = PermissionUtils.getNotificationPermission()
                            if (permission != null &&
                                !PermissionUtils.hasNotificationPermission(context)
                            ) {
                                notificationPermissionLauncher.launch(permission)
                            } else {
                                mainViewModel.setNotificationsEnabled(true)
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))


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
