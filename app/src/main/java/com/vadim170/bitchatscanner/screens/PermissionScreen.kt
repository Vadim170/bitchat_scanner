package com.vadim170.bitchatscanner.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vadim170.bitchatscanner.R
import com.vadim170.bitchatscanner.components.PermissionCard

/**
 * Экран запроса разрешений
 * Показывается при первом запуске или когда отсутствуют необходимые разрешения
 * 
 * @param onGrantAll Callback для запроса всех разрешений
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(onGrantAll: () -> Unit) {
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) }
            ) 
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.permissions_description),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Bluetooth разрешения
            item {
                PermissionCard(
                    title = stringResource(R.string.permission_bluetooth_title),
                    subtitle = stringResource(R.string.permission_bluetooth_subtitle)
                )
            }
            
            // Разрешение на локацию
            item {
                PermissionCard(
                    title = stringResource(R.string.permission_location_title),
                    subtitle = stringResource(R.string.permission_location_subtitle)
                )
            }
            
            // Разрешение на уведомления (только для Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    PermissionCard(
                        title = stringResource(R.string.permission_notifications_title),
                        subtitle = stringResource(R.string.permission_notifications_subtitle)
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
            
            item {
                Button(onClick = onGrantAll) { 
                    Text(stringResource(R.string.grant_permissions)) 
                }
            }

            item {
                Text(
                    text = stringResource(R.string.privacy_notice),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
