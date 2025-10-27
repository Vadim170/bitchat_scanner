package com.vadim170.bitchatscanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import com.vadim170.bitchatscanner.ui.theme.BitchatScannerTheme
import com.vadim170.bitchatscanner.viewmodel.MainViewModel
import com.vadim170.bitchatscanner.viewmodel.DevicesViewModel
import com.vadim170.bitchatscanner.repository.ScannerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitchatScannerTheme {
                RootScreen()
            }
        }
    }
}

/** Экран выбора: если нет прав — показываем PermissionScreen, иначе — MainScreen. */
@Composable
private fun RootScreen() {
    val context = LocalContext.current
    var hasPerms by remember { mutableStateOf(hasAllRequiredPermissions(context)) }
    var currentScreen by remember { mutableStateOf(Screen.Main) }

    // Один лаунчер, который будет запрашивать все необходимые разрешения
    val requestPermsLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            hasPerms = hasAllRequiredPermissions(context)
        }

    if (!hasPerms) {
        PermissionScreen(
            onGrantAll = {
                requestPermsLauncher.launch(allPermissionsToRequest())
            }
        )
    } else {
        when (currentScreen) {
            Screen.Main -> MainScreen(
                onNavigateToLogs = { currentScreen = Screen.Logs },
                onNavigateToMap = { currentScreen = Screen.Map }
            )
            Screen.Devices -> DevicesScreen(
                onNavigateBack = { currentScreen = Screen.Main }
            )
            Screen.Logs -> LogsScreen(
                onNavigateBack = { currentScreen = Screen.Main }
            )
            Screen.Map -> MapScreen(
                onNavigateBack = { currentScreen = Screen.Main }
            )
        }
    }
}

enum class Screen {
    Main, Devices, Logs, Map
}

private fun requiredPermissions(): List<String> {
    val list = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list += Manifest.permission.BLUETOOTH_SCAN
        list += Manifest.permission.BLUETOOTH_CONNECT
    } else {
        list += Manifest.permission.ACCESS_FINE_LOCATION
    }
    if (!list.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
        list += Manifest.permission.ACCESS_FINE_LOCATION
    }
    return list
}

private fun optionalPermissions(): List<String> {
    val opt = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        opt += Manifest.permission.POST_NOTIFICATIONS
    }
    return opt
}

private fun allPermissionsToRequest(): Array<String> =
    (requiredPermissions() + optionalPermissions()).distinct().toTypedArray()

private fun hasAllRequiredPermissions(context: Context): Boolean =
    requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionScreen(onGrantAll: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.permissions_title)) }) },
        modifier = Modifier.fillMaxSize()
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.permissions_description),
                style = MaterialTheme.typography.bodyLarge
            )

            PermCard(
                title = stringResource(R.string.permission_bluetooth_title),
                subtitle = stringResource(R.string.permission_bluetooth_subtitle)
            )
            PermCard(
                title = stringResource(R.string.permission_location_title),
                subtitle = stringResource(R.string.permission_location_subtitle)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermCard(
                    title = stringResource(R.string.permission_notifications_title),
                    subtitle = stringResource(R.string.permission_notifications_subtitle)
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onGrantAll) { Text(stringResource(R.string.grant_permissions)) }

            Text(
                stringResource(R.string.privacy_notice),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PermCard(title: String, subtitle: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(onNavigateToLogs: () -> Unit, onNavigateToMap: () -> Unit) {
    val mainViewModel: MainViewModel = viewModel()
    val devicesViewModel: DevicesViewModel = viewModel()
    val mainUiState by mainViewModel.uiState.collectAsState()
    val devicesUiState by devicesViewModel.uiState.collectAsState()
    var showDropdownMenu by remember { mutableStateOf(false) }

    // Лаунчер для прав (если нужно вручную)
    val requestPermsLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* no-op */ }

    fun requestAllPermissions() {
        requestPermsLauncher.launch(allPermissionsToRequest())
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToMap) {
                        Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.map))
                    }
                    IconButton(onClick = { showDropdownMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
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
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(horizontal = 8.dp)
                .fillMaxSize()
        ) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Switch(
                    checked = mainUiState.notifyEnabled,
                    onCheckedChange = { mainViewModel.setNotifyEnabled(it) }
                )
                Text(stringResource(R.string.notify_detections), style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.height(16.dp))

            // Список устройств вместо логов
            if (devicesUiState.isLoading) {
                Text(
                    stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (devicesUiState.devices.isEmpty()) {
                Text(
                    stringResource(R.string.no_devices_found),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
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
                        key = { device -> device.address } // Ключ для стабильности при скролле
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicesScreen(onNavigateBack: () -> Unit) {
    // Убираем отдельный экран устройств, так как они теперь на главном экране
    onNavigateBack()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsScreen(onNavigateBack: () -> Unit) {
    val viewModel: MainViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detection_log)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Button(onClick = { viewModel.showClearDialog() }) { 
                        Text(stringResource(R.string.clear)) 
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.logLines) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                }
            }
        }
    }

    // Диалоговое окно подтверждения очистки лога
    if (uiState.showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearDialog() },
            title = { Text(stringResource(R.string.confirmation)) },
            text = { Text(stringResource(R.string.clear_log_message)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAllData() }
                ) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.hideClearDialog() }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DeviceCard(device: DeviceSummary, showMap: Boolean) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    // Состояния загрузки локаций устройства
    var locations by remember(device.address) { mutableStateOf<List<LocationPoint>>(emptyList()) }
    var isLoadingLocations by remember(device.address) { mutableStateOf(false) }
    var lastLoadedTimestamp by remember(device.address) { mutableStateOf<Long?>(null) }
    var locationLoadError by remember(device.address) { mutableStateOf<Throwable?>(null) }

    // Загружаем координаты только когда карточка действительно видима и появились новые данные
    LaunchedEffect(device.address, showMap, device.lastSeen) {
        if (showMap && !isLoadingLocations && lastLoadedTimestamp != device.lastSeen) {
            isLoadingLocations = true
            locationLoadError = null
            try {
                val deviceLocations = withContext(Dispatchers.IO) {
                    ScannerRepository.getInstance(context).getDeviceLocations(device.address)
                }
                locations = deviceLocations
                lastLoadedTimestamp = device.lastSeen
            } catch (t: Throwable) {
                locationLoadError = t
            } finally {
                isLoadingLocations = false
            }
        }
    }

    // Проверяем, видели ли устройство менее минуты назад
    val currentTime = System.currentTimeMillis()
    val oneMinuteAgo = currentTime - 60 * 1000 // 60 секунд
    val isRecentlyDetected = device.lastSeen > oneMinuteAgo

    // Выбираем цвет карточки
    val cardColor = if (isRecentlyDetected) {
        MaterialTheme.colorScheme.tertiaryContainer // Зеленоватый цвет
    } else {
        MaterialTheme.colorScheme.surfaceVariant // Обычный серый
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = device.address,
                style = MaterialTheme.typography.titleMedium
            )

            if (!device.name.isNullOrEmpty()) {
                Text(
                    text = stringResource(R.string.device_name, device.name),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = stringResource(R.string.last_detection, sdf.format(Date(device.lastSeen))),
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = stringResource(R.string.first_detection, sdf.format(Date(device.firstSeen))),
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = stringResource(R.string.rssi, device.rssi),
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = stringResource(R.string.detection_count, device.detectionCount),
                style = MaterialTheme.typography.bodySmall
            )

            if (!device.serviceDataHex.isNullOrEmpty()) {
                Text(
                    text = stringResource(
                        R.string.service_data,
                        "${device.serviceDataHex.take(16)}${if (device.serviceDataHex.length > 16) "…" else ""}"
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (device.lat != null && device.lon != null) {
                val mapShape = RoundedCornerShape(12.dp)
                val baseMapModifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)

                val mapModifier = baseMapModifier.clip(mapShape)

                val placeholderModifier = baseMapModifier
                    .clip(mapShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    !showMap -> {
                        Box(
                            modifier = placeholderModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.map_hidden_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    isLoadingLocations -> {
                        Box(
                            modifier = placeholderModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    locationLoadError != null -> {
                        Box(
                            modifier = placeholderModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.map_load_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    locations.isEmpty() -> {
                        Box(
                            modifier = placeholderModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_coordinates),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    else -> {
                        DeviceMapCard(
                            locations = locations,
                            modifier = mapModifier
                        )
                    }
                }

                if (showMap && locations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.points_count, locations.size),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceMapCard(
    locations: List<LocationPoint>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapViewHolder = remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mapViewHolder.value?.onDetach()
            mapViewHolder.value = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = context.packageName

            MapView(ctx).apply {
                mapViewHolder.value = this

                setTileSource(TileSourceFactory.MAPNIK)

                // Отключаем интерактивность
                setMultiTouchControls(false)
                setFlingEnabled(false)
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                overlayManager.tilesOverlay.isEnabled = true
                setOnTouchListener { _, _ -> true }
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false
                isTilesScaledToDpi = false

                updateMapContent(this, locations)
            }
        },
        update = { view ->
            updateMapContent(view, locations)
        }
    )
}

private fun updateMapContent(mapView: MapView, locations: List<LocationPoint>) {
    val newHash = locations.hashCode()
    if (mapView.tag == newHash) {
        return
    }

    mapView.tag = newHash
    mapView.overlays.clear()

    if (locations.isEmpty()) {
        mapView.invalidate()
        return
    }

    val controller: IMapController = mapView.controller
    val centerLat = locations.map { it.lat }.average()
    val centerLon = locations.map { it.lon }.average()
    val startPoint = GeoPoint(centerLat, centerLon)
    controller.setCenter(startPoint)

    if (locations.size == 1) {
        controller.setZoom(16.0)
    } else {
        val geoPoints = locations.map { GeoPoint(it.lat, it.lon) }
        val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints)

        val minSizeDegrees = 0.004
        val currentWidthDegrees = boundingBox.lonEast - boundingBox.lonWest
        val currentHeightDegrees = boundingBox.latNorth - boundingBox.latSouth

        val expandedBoundingBox = if (currentWidthDegrees < minSizeDegrees || currentHeightDegrees < minSizeDegrees) {
            val expandWidth = maxOf(0.0, (minSizeDegrees - currentWidthDegrees) / 2)
            val expandHeight = maxOf(0.0, (minSizeDegrees - currentHeightDegrees) / 2)

            org.osmdroid.util.BoundingBox(
                boundingBox.latNorth + expandHeight,
                boundingBox.lonEast + expandWidth,
                boundingBox.latSouth - expandHeight,
                boundingBox.lonWest - expandWidth
            )
        } else {
            boundingBox
        }

        mapView.post {
            mapView.zoomToBoundingBox(expandedBoundingBox, false, 50)
        }
    }

    locations.forEach { location ->
        val rssiRadius = calculateRadiusFromRSSI(location.rssi)
        val gpsAccuracy = location.accuracy ?: 10f
        val finalRadius = maxOf(rssiRadius, gpsAccuracy.toDouble())

        val circle = Polygon()
        val circlePoints = mutableListOf<GeoPoint>()
        val numPoints = 16
        for (i in 0..numPoints) {
            val angle = 2 * Math.PI * i / numPoints
            val latOffset = finalRadius * Math.cos(angle) / 111320.0
            val lonOffset = finalRadius * Math.sin(angle) / (111320.0 * Math.cos(Math.toRadians(location.lat)))
            circlePoints.add(GeoPoint(location.lat + latOffset, location.lon + lonOffset))
        }
        circle.points = circlePoints

        circle.fillColor = when {
            location.rssi > -50 -> 0x1000FF00
            location.rssi > -70 -> 0x10FFFF00
            else -> 0x10FF0000
        }
        circle.strokeColor = when {
            location.rssi > -50 -> 0x4000AA00
            location.rssi > -70 -> 0x40AAAA00
            else -> 0x40AA0000
        }
        circle.strokeWidth = 1f

        mapView.overlays.add(circle)
    }

    mapView.invalidate()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allLocations by remember { mutableStateOf<List<LocationPoint>>(emptyList()) }
    
    // Инициализируем OSMdroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }
    
    // Загружаем все координаты обнаружений
    LaunchedEffect(Unit) {
        val repository = ScannerRepository.getInstance(context)
        // Получаем все устройства и их локации
        repository.loadDevices()
        
        // Собираем устройства из StateFlow
        repository.devices.collect { devices ->
            val allDeviceLocations = mutableListOf<LocationPoint>()
            
            devices.forEach { device ->
                val deviceLocations = repository.getDeviceLocations(device.address)
                allDeviceLocations.addAll(deviceLocations)
            }
            
            allLocations = allDeviceLocations
            println("MapScreen: Loaded ${allDeviceLocations.size} total locations from ${devices.size} devices")
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.all_detections_map)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            if (allLocations.isNotEmpty()) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f), // Заполняет доступное пространство
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            
                            // Включаем все интерактивные функции для этой карты
                            setMultiTouchControls(true)
                            setFlingEnabled(true)
                            isClickable = true
                            isFocusable = true
                            isFocusableInTouchMode = true
                            
                            // Показываем zoom controls
                            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
                            
                            val mapController: IMapController = controller
                            
                            // Вычисляем центр и зону покрытия
                            val centerLat = allLocations.map { it.lat }.average()
                            val centerLon = allLocations.map { it.lon }.average()
                            val startPoint = GeoPoint(centerLat, centerLon)
                            
                            mapController.setCenter(startPoint)
                            
                            // Автоматический масштаб для всех точек
                            if (allLocations.size == 1) {
                                mapController.setZoom(16.0)
                            } else {
                                val geoPoints = allLocations.map { GeoPoint(it.lat, it.lon) }
                                val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints)
                                
                                // Расширяем bounding box чтобы обеспечить минимальный размер ~400м
                                val minSizeDegrees = 0.004 // примерно 400 метров в градусах
                                val currentWidthDegrees = boundingBox.lonEast - boundingBox.lonWest
                                val currentHeightDegrees = boundingBox.latNorth - boundingBox.latSouth
                                
                                val expandedBoundingBox = if (currentWidthDegrees < minSizeDegrees || currentHeightDegrees < minSizeDegrees) {
                                    val expandWidth = maxOf(0.0, (minSizeDegrees - currentWidthDegrees) / 2)
                                    val expandHeight = maxOf(0.0, (minSizeDegrees - currentHeightDegrees) / 2)
                                    
                                    org.osmdroid.util.BoundingBox(
                                        boundingBox.latNorth + expandHeight,
                                        boundingBox.lonEast + expandWidth,
                                        boundingBox.latSouth - expandHeight,
                                        boundingBox.lonWest - expandWidth
                                    )
                                } else {
                                    boundingBox
                                }
                                
                                post {
                                    zoomToBoundingBox(expandedBoundingBox, true, 100)
                                }
                            }
                            
                            // Рисуем круги для всех обнаружений
                            allLocations.forEach { location ->
                                val rssiRadius = calculateRadiusFromRSSI(location.rssi)
                                val gpsAccuracy = location.accuracy ?: 10f
                                val finalRadius = maxOf(rssiRadius, gpsAccuracy.toDouble())
                                
                                val circle = Polygon()
                                val center = GeoPoint(location.lat, location.lon)
                                
                                // Генерируем точки для круга
                                val circlePoints = mutableListOf<GeoPoint>()
                                val numPoints = 32
                                for (i in 0..numPoints) {
                                    val angle = 2 * Math.PI * i / numPoints
                                    val latOffset = finalRadius * Math.cos(angle) / 111320.0
                                    val lonOffset = finalRadius * Math.sin(angle) / (111320.0 * Math.cos(Math.toRadians(location.lat)))
                                    circlePoints.add(GeoPoint(location.lat + latOffset, location.lon + lonOffset))
                                }
                                circle.points = circlePoints
                                
                                // Настройка стиля круга - более прозрачные цвета для наложения
                                circle.fillColor = when {
                                    location.rssi > -50 -> 0x1000FF00 // Еще более прозрачные для наложения
                                    location.rssi > -70 -> 0x10FFFF00
                                    else -> 0x10FF0000
                                }
                                circle.strokeColor = when {
                                    location.rssi > -50 -> 0x4000AA00
                                    location.rssi > -70 -> 0x40AAAA00
                                    else -> 0x40AA0000
                                }
                                circle.strokeWidth = 1f
                                
                                overlays.add(circle)
                            }
                        }
                    },
                    update = { mapView ->
                        // Обновляем карту при изменении данных
                        mapView.overlays.clear()
                        
                        allLocations.forEach { location ->
                            val rssiRadius = calculateRadiusFromRSSI(location.rssi)
                            val gpsAccuracy = location.accuracy ?: 10f
                            val finalRadius = maxOf(rssiRadius, gpsAccuracy.toDouble())
                            
                            val circle = Polygon()
                            val center = GeoPoint(location.lat, location.lon)
                            
                            val circlePoints = mutableListOf<GeoPoint>()
                            val numPoints = 32
                            for (i in 0..numPoints) {
                                val angle = 2 * Math.PI * i / numPoints
                                val latOffset = finalRadius * Math.cos(angle) / 111320.0
                                val lonOffset = finalRadius * Math.sin(angle) / (111320.0 * Math.cos(Math.toRadians(location.lat)))
                                circlePoints.add(GeoPoint(location.lat + latOffset, location.lon + lonOffset))
                            }
                            circle.points = circlePoints
                            
                            circle.fillColor = when {
                                location.rssi > -50 -> 0x1000FF00
                                location.rssi > -70 -> 0x10FFFF00
                                else -> 0x10FF0000
                            }
                            circle.strokeColor = when {
                                location.rssi > -50 -> 0x4000AA00
                                location.rssi > -70 -> 0x40AAAA00
                                else -> 0x40AA0000
                            }
                            circle.strokeWidth = 1f
                            
                            mapView.overlays.add(circle)
                        }
                        mapView.invalidate()
                    }
                )
                
                // Информация о количестве обнаружений
                Text(
                    text = stringResource(R.string.total_detections, allLocations.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                // Показываем сообщение если нет данных
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_devices_found),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

// Функция для вычисления радиуса на основе RSSI
private fun calculateRadiusFromRSSI(rssi: Int): Double {
    return when {
        rssi > -50 -> 5.0   // Очень близко - 5 метров
        rssi > -60 -> 10.0  // Близко - 10 метров  
        rssi > -70 -> 20.0  // Средне - 20 метров
        rssi > -80 -> 50.0  // Далеко - 50 метров
        else -> 100.0       // Очень далеко - 100 метров
    }
}