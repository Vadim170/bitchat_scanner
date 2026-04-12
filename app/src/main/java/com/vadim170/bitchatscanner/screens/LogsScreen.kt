package com.vadim170.bitchatscanner.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vadim170.bitchatscanner.R
import com.vadim170.bitchatscanner.viewmodel.MainViewModel
import androidx.compose.material3.MaterialTheme

/**
 * 
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onNavigateBack: () -> Unit) {
    val viewModel: MainViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detection_log)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = stringResource(R.string.back)
                        )
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.logLines) { line ->
                    Text(
                        text = line, 
                        style = MaterialTheme.typography.bodySmall
                    )
                    HorizontalDivider()
                }
            }
        }
    }


    if (uiState.showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearDialog() },
            title = { Text(stringResource(R.string.confirmation)) },
            text = { Text(stringResource(R.string.clear_log_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllData() }) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideClearDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
