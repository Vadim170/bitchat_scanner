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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import com.vadim170.bitchatscanner.ui.theme.BitchatScannerTheme
import com.vadim170.bitchatscanner.viewmodel.MainViewModel
import com.vadim170.bitchatscanner.viewmodel.DevicesViewModel
import com.vadim170.bitchatscanner.repository.ScannerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.*
import androidx.core.content.edit

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
                onNavigateToLogs = { currentScreen = Screen.Logs }
            )
            Screen.Devices -> DevicesScreen(
                onNavigateBack = { currentScreen = Screen.Main }
            )
            Screen.Logs -> LogsScreen(
                onNavigateBack = { currentScreen = Screen.Main }
            )
        }
    }
}

enum class Screen {
    Main, Devices, Logs
}

private fun requiredPermissions(): List<String> {
    val list = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list += Manifest.permission.BLUETOOTH_SCAN
        list += Manifest.permission.BLUETOOTH_CONNECT
    } else {
        // До Android 12 BLE-скан требует гео
        list += Manifest.permission.ACCESS_FINE_LOCATION
    }
    // Локация для геометок — требуем всегда
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
        topBar = { TopAppBar(title = { Text("BitChat Scanner — разрешения") }) },
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
                "Чтобы сканировать узлы и строить карту покрытия, нужны следующие разрешения:",
                style = MaterialTheme.typography.bodyLarge
            )

            PermCard(
                title = "Bluetooth (сканирование и подключение)",
                subtitle = "Нужно, чтобы искать устройства BitChat по BLE-сервису."
            )
            PermCard(
                title = "Геопозиция (точная)",
                subtitle = "Нужна для привязки обнаружений к координатам и построения карты покрытия сети."
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermCard(
                    title = "Уведомления",
                    subtitle = "Необязательно. Нужны только если хотите получать уведомления о новых узлах."
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onGrantAll) { Text("Выдать разрешения") }

            Text(
                "Мы не отправляем координаты никуда — всё хранится локально в приложении.",
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
private fun MainScreen(onNavigateToLogs: () -> Unit) {
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
                title = { Text("BitChat Scanner") },
                actions = {
                    IconButton(onClick = { showDropdownMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                    }
                    DropdownMenu(
                        expanded = showDropdownMenu,
                        onDismissRequest = { showDropdownMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Журнал обнаружений") },
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
                    Button(onClick = { mainViewModel.stopScanner() }) { Text("Стоп сканера") }
                } else {
                    Button(onClick = { mainViewModel.startScanner() }) { Text("Старт сканера") }
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
                Text("Уведомлять об обнаружениях", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.height(16.dp))

            // Список устройств вместо логов
            if (devicesUiState.isLoading) {
                Text(
                    "Загрузка...",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (devicesUiState.devices.isEmpty()) {
                Text(
                    "Устройства не найдены",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devicesUiState.devices) { device ->
                        DeviceCard(device = device)
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
                title = { Text("Журнал обнаружений") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    Button(onClick = { viewModel.showClearDialog() }) { 
                        Text("Очистить") 
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
            title = { Text("Подтверждение") },
            text = { Text("Вы уверены, что хотите очистить весь журнал обнаружений? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAllData() }
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.hideClearDialog() }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun DeviceCard(device: DeviceSummary) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    
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
                    text = "Имя: ${device.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Text(
                text = "Последнее обнаружение: ${sdf.format(Date(device.lastSeen))}",
                style = MaterialTheme.typography.bodySmall
            )
            
            Text(
                text = "Первое обнаружение: ${sdf.format(Date(device.firstSeen))}",
                style = MaterialTheme.typography.bodySmall
            )
            
            Text(
                text = "RSSI: ${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall
            )
            
            Text(
                text = "Обнаружений: ${device.detectionCount}",
                style = MaterialTheme.typography.bodySmall
            )
            
            if (!device.serviceDataHex.isNullOrEmpty()) {
                Text(
                    text = "Данные сервиса: ${device.serviceDataHex.take(16)}${if (device.serviceDataHex.length > 16) "…" else ""}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Карта с точками обнаружений
            if (device.lat != null && device.lon != null) {
                Spacer(modifier = Modifier.height(8.dp))
                DeviceMapCard(device = device)
            }
        }
    }
}

@Composable
private fun DeviceMapCard(device: DeviceSummary) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locations by remember { mutableStateOf<List<LocationPoint>>(emptyList()) }
    var isMapReady by remember { mutableStateOf(false) }
    
    // Инициализируем OSMdroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }
    
    // Загружаем координаты всех обнаружений для этого устройства
    LaunchedEffect(device.address) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val deviceLocations = ScannerRepository.getInstance(context).getDeviceLocations(device.address)
                withContext(Dispatchers.Main) {
                    locations = deviceLocations
                    println("DeviceMapCard: Loaded ${deviceLocations.size} locations for device ${device.address}")
                }
            }
        }
    }
    
    if (locations.isNotEmpty()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp)), // Закругляем края
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    
                    // МАКСИМАЛЬНО отключаем все жесты и взаимодействия
                    setMultiTouchControls(false)
                    setFlingEnabled(false)
                    isClickable = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    
                    // Отключаем zoom controls полностью
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    
                    // Полностью блокируем все сенсорные жесты OSMdroid
                    overlayManager.tilesOverlay.isEnabled = true  // карту оставляем
                    
                    // Переопределяем dispatchTouchEvent чтобы полностью игнорировать касания
                    setOnTouchListener { _, _ -> true } // Поглощаем все события
                    
                    // Дополнительно отключаем анимации и интерактивность
                    isHorizontalMapRepetitionEnabled = false
                    isVerticalMapRepetitionEnabled = false
                    
                    val mapController: IMapController = controller
                    
                    // Вычисляем центр и зону покрытия
                    val centerLat = locations.map { it.lat }.average()
                    val centerLon = locations.map { it.lon }.average()
                    val startPoint = GeoPoint(centerLat, centerLon)
                    
                    mapController.setCenter(startPoint)
                    
                    // Простая логика масштабирования
                    if (locations.size == 1) {
                        // Одна точка - показываем район (zoom 16)
                        mapController.setZoom(16.0)
                    } else {
                        // Несколько точек - используем bounding box с минимальным zoom 15
                        val geoPoints = locations.map { GeoPoint(it.lat, it.lon) }
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
                        
                        // Устанавливаем масштаб после создания карты
                        post {
                            zoomToBoundingBox(expandedBoundingBox, true, 50)
                        }
                    }
                    
                    // Рисуем круги вместо маркеров
                    locations.forEachIndexed { index, location ->
                        // Вычисляем радиус на основе RSSI и точности GPS
                        val rssiRadius = calculateRadiusFromRSSI(location.rssi)
                        val gpsAccuracy = location.accuracy ?: 10f
                        val finalRadius = maxOf(rssiRadius, gpsAccuracy.toDouble())
                        
                        // Создаем круг
                        val circle = Polygon()
                        val center = GeoPoint(location.lat, location.lon)
                        
                        // Генерируем точки для круга
                        val circlePoints = mutableListOf<GeoPoint>()
                        val numPoints = 32
                        for (i in 0..numPoints) {
                            val angle = 2 * Math.PI * i / numPoints
                            val latOffset = finalRadius * Math.cos(angle) / 111320.0 // примерно метры в градусы широты
                            val lonOffset = finalRadius * Math.sin(angle) / (111320.0 * Math.cos(Math.toRadians(location.lat)))
                            circlePoints.add(GeoPoint(location.lat + latOffset, location.lon + lonOffset))
                        }
                        circle.points = circlePoints
                        
                        // Настройка стиля круга - очень прозрачные цвета
                        circle.fillColor = when {
                            location.rssi > -50 -> 0x2000FF00 // Едва видимый зеленый (очень сильный сигнал)
                            location.rssi > -70 -> 0x20FFFF00 // Едва видимый желтый (средний сигнал)
                            else -> 0x20FF0000 // Едва видимый красный (слабый сигнал)
                        }
                        circle.strokeColor = when {
                            location.rssi > -50 -> 0x6000AA00
                            location.rssi > -70 -> 0x60AAAA00
                            else -> 0x60AA0000
                        }
                        circle.strokeWidth = 1f
                        
                        overlays.add(circle)
                        
                        // Маркеры убираем - только круги
                    }
                    
                    isMapReady = true
                }
            },
            update = { mapView ->
                // Очищаем и обновляем при изменении locations
                mapView.overlays.clear()
                
                if (locations.isNotEmpty()) {
                    locations.forEachIndexed { index, location ->
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
                            location.rssi > -50 -> 0x2000FF00
                            location.rssi > -70 -> 0x20FFFF00
                            else -> 0x20FF0000
                        }
                        circle.strokeColor = when {
                            location.rssi > -50 -> 0x6000AA00
                            location.rssi > -70 -> 0x60AAAA00
                            else -> 0x60AA0000
                        }
                        circle.strokeWidth = 1f
                        
                        mapView.overlays.add(circle)
                        
                        // Маркеры не добавляем - только круги
                    }
                }
                mapView.invalidate()
            }
        )
    } else {
        // Показываем сообщение если нет координат
        Text(
            text = "Нет данных о координатах для этого устройства",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp)
        )
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