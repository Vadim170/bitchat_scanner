package com.vadim170.bitchatscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vadim170.bitchatscanner.screens.LogsScreen
import com.vadim170.bitchatscanner.screens.MainScreen
import com.vadim170.bitchatscanner.screens.MapScreen
import com.vadim170.bitchatscanner.screens.PermissionScreen
import com.vadim170.bitchatscanner.ui.theme.BitchatScannerTheme
import com.vadim170.bitchatscanner.utils.PermissionUtils

/**
 */
class MainActivity : ComponentActivity() {
    private val scanCoordinator by lazy {
        BleScanCoordinator.getInstance(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            BitchatScannerTheme {
                RootScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        scanCoordinator.onHostStarted()
    }

    override fun onResume() {
        super.onResume()
        // A permission dialog may complete while this activity remains
        // visible. Retry a persisted Start intent after that lifecycle edge.
        scanCoordinator.onHostResumed()
    }

    override fun onStop() {
        scanCoordinator.onHostStopped()
        super.onStop()
    }
}

/**
 */
enum class Screen {
    Main,
    Logs,
    Map
}

/**
 */
@Composable
private fun RootScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPerms by remember { 
        mutableStateOf(PermissionUtils.hasAllRequiredPermissions(context)) 
    }
    var currentScreen by remember { mutableStateOf(Screen.Main) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPerms = PermissionUtils.hasAllRequiredPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }


    val requestPermsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPerms = PermissionUtils.hasAllRequiredPermissions(context)
    }

    if (!hasPerms) {

        PermissionScreen(
            onGrantAll = {
                requestPermsLauncher.launch(PermissionUtils.getAllPermissionsToRequest())
            }
        )
    } else {

        when (currentScreen) {
            Screen.Main -> MainScreen(
                onNavigateToLogs = { currentScreen = Screen.Logs },
                onNavigateToMap = { currentScreen = Screen.Map }
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
