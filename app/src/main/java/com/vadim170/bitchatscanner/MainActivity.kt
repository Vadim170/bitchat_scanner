package com.vadim170.bitchatscanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitchatScannerTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Храним настройку уведомлений в SharedPreferences
    val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    var notifyEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("notify", false))
    }

    // Лента лога, приходящая из сервиса Broadcast'ом
    val logLines = remember { mutableStateListOf<String>() }

    // Регистрируем/снимаем BroadcastReceiver по жизненному циклу композиции
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == BleScannerService.ACTION_LOG_LINE) {
                    val line = i.getStringExtra(BleScannerService.EXTRA_LINE) ?: return
                    // Добавляем в начало списка (новое сверху)
                    logLines.add(0, line)
                    // ограничим размер списка, чтобы не распухал UI
                    if (logLines.size > 500) logLines.removeLast()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BleScannerService.ACTION_LOG_LINE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Runtime-разрешения
    val requestPermsLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            // Можно показать тост/снек, но UI и так позволяет повторять попытку
        }

    fun requestAllPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                // До Android 12 скан BLE требует location
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        requestPermsLauncher.launch(perms)
    }

    fun startScanner() {
        val it = Intent(context, BleScannerService::class.java)
        ContextCompat.startForegroundService(context, it)
    }

    fun stopScanner() {
        context.stopService(Intent(context, BleScannerService::class.java))
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("BitChat Scanner") }) },
        modifier = Modifier.fillMaxSize()
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { startScanner() }) { Text("Старт сканера") }
                Button(onClick = { stopScanner() }) { Text("Стоп") }
                Button(onClick = { requestAllPermissions() }) { Text("Разрешения") }
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
                        prefs.edit().putBoolean("notify", it).apply()
                    }
                )
                Text("Уведомлять об обнаружениях", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.height(16.dp))
            Text("Журнал обнаружений", style = MaterialTheme.typography.titleMedium)
            Divider(Modifier.padding(vertical = 8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                items(logLines) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                    Divider()
                }
            }
        }
    }
}