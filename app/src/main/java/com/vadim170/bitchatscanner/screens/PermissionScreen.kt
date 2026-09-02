package com.vadim170.bitchatscanner.screens

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
import androidx.compose.material3.OutlinedButton
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
 * Prominent disclosure shown before the runtime permission dialog. Bluetooth
 * and precise location are both required on every supported Android version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    onGrantAll: () -> Unit,
    showSettingsFallback: Boolean = false,
    onOpenSettings: () -> Unit = {},
) {
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

            item {
                PermissionCard(
                    title = stringResource(R.string.permission_bluetooth_title),
                    subtitle = stringResource(R.string.permission_bluetooth_subtitle)
                )
            }

            item {
                PermissionCard(
                    title = stringResource(R.string.permission_location_title),
                    subtitle = stringResource(R.string.permission_location_subtitle)
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
            }

            item {
                Button(onClick = onGrantAll) {
                    Text(stringResource(R.string.grant_permissions))
                }
            }

            if (showSettingsFallback) {
                item {
                    Text(
                        text = stringResource(R.string.permissions_denied_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    OutlinedButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.open_app_settings))
                    }
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
