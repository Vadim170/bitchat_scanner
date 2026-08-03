package com.vadim170.bitchatscanner.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Runtime permission matrix for BLE scanning and optional coordinate tagging. */
object PermissionUtils {

    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            // Android 11 and lower require location permission for BLE scan
            // results. Request the coarse/precise pair together.
            permissions += getLocationPermissions()
        }
        return permissions
    }

    /** Coarse + precise pair required by Android 12+ for a precise request. */
    fun getLocationPermissions(): List<String> = listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    fun getAllPermissionsToRequest(): Array<String> =
        getRequiredPermissions().distinct().toTypedArray()

    /** Optional on Android 12+: BLE remains usable without coordinate tagging. */
    fun getOptionalLocationPermissionsToRequest(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getLocationPermissions().toTypedArray()
        } else {
            emptyArray()
        }

    fun hasPreciseLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /** Optional notification permission requested only after the user enables notifications. */
    fun getNotificationPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    fun hasNotificationPermission(context: Context): Boolean =
        getNotificationPermission()?.let {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } ?: true

    fun hasAllRequiredPermissions(context: Context): Boolean =
        getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
}
