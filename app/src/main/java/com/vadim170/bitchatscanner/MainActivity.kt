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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vadim170.bitchatscanner.ui.theme.BitchatScannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
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
                onNavigateToDevices = { currentScreen = Screen.Devices }
            )
            Screen.Devices -> DevicesScreen(
                onNavigateBack = { currentScreen = Screen.Main }
            )
        }
    }
}

enum class Screen {
    Main, Devices
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
private fun MainScreen(onNavigateToDevices: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    var notifyEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("notify", false)) }
    var showClearDialog by remember { mutableStateOf(false) }

    val logLines = remember { mutableStateListOf<String>() }
    val db = remember { DetectionDbHelper(context) }

    // 1) При входе загружаем историю из БД (последние 1000)
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val rows = withContext(Dispatchers.IO) { db.latest(1000) }
        val initial = rows.map { row ->
            buildString {
                append(sdf.format(Date(row.timestamp)))
                append(",")
                append(row.address)
                append(", RSSI ")
                append(row.rssi)
                if (!row.name.isNullOrEmpty()) {
                    append(", "); append(row.name)
                }
                if (row.lat != null && row.lon != null) {
                    append(", "); append("lat="); append(row.lat)
                    append(", lon="); append(row.lon)
                }
                if (!row.serviceDataHex.isNullOrEmpty()) {
                    append(", sd="); append(row.serviceDataHex.take(16)); append("…")
                }
            }
        }
        logLines.clear()
        logLines.addAll(initial)
    }

    // 2) Подписываемся на новые строки от сервиса
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == BleScannerService.ACTION_LOG_LINE) {
                    val line = i.getStringExtra(BleScannerService.EXTRA_LINE) ?: return
                    logLines.add(0, line)
                    if (logLines.size > 1000) logLines.removeLast()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BleScannerService.ACTION_LOG_LINE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Лаунчер для прав (если нужно вручную)
    val requestPermsLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* no-op */ }

    fun requestAllPermissions() {
        requestPermsLauncher.launch(allPermissionsToRequest())
    }

    fun startScanner() {
        val it = Intent(context, BleScannerService::class.java)
        ContextCompat.startForegroundService(context, it)
    }

    fun stopScanner() {
        context.stopService(Intent(context, BleScannerService::class.java))
    }

    fun clearLog() {
        coroutineScope.launch(Dispatchers.IO) {
            db.clearAll()
            withContext(Dispatchers.Main) {
                logLines.clear()
            }
        }
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("BitChat Scanner") },
                actions = {
                    IconButton(onClick = onNavigateToDevices) {
                        Icon(Icons.Default.List, contentDescription = "Список устройств")
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
                Button(onClick = { startScanner() }) { Text("Старт сканера") }
                Button(onClick = { stopScanner() }) { Text("Стоп") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { showClearDialog = true }) { Text("Очистить лог") }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Switch(
                    checked = notifyEnabled,
                    onCheckedChange = {
                        notifyEnabled = it
                        prefs.edit { putBoolean("notify", it) }
                    }
                )
                Text("Уведомлять об обнаружениях", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.height(16.dp))
            Text("Журнал обнаружений", style = MaterialTheme.typography.titleMedium)
            Divider(Modifier.padding(vertical = 8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logLines) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                    Divider()
                }
            }
        }
    }

    // Диалоговое окно подтверждения очистки лога
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Подтверждение") },
            text = { Text("Вы уверены, что хотите очистить весь журнал обнаружений? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearLog()
                        showClearDialog = false
                    }
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicesScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val devices = remember { mutableStateListOf<DeviceSummary>() }
    val db = remember { DetectionDbHelper(context) }

    // Загружаем устройства при входе на экран
    LaunchedEffect(Unit) {
        val deviceList = withContext(Dispatchers.IO) { db.getAllDevices() }
        devices.clear()
        devices.addAll(deviceList)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обнаруженные устройства") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
            if (devices.isEmpty()) {
                Text(
                    "Устройства не найдены",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Text(
                    "Найдено устройств: ${devices.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices) { device ->
                        DeviceCard(device = device)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceSummary) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                text = "RSSI: ${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall
            )
            
            if (device.lat != null && device.lon != null) {
                Text(
                    text = "Координаты: ${String.format("%.6f", device.lat)}, ${String.format("%.6f", device.lon)}",
                    style = MaterialTheme.typography.bodySmall
                )
                
                if (device.accuracy != null) {
                    Text(
                        text = "Точность: ${String.format("%.1f", device.accuracy)} м",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
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
        }
    }
}