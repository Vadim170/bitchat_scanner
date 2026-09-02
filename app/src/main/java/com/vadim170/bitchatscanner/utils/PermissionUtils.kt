package com.vadim170.bitchatscanner.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime permission matrix for BLE scanning.
 *
 * Precise location is required on every supported Android version. The
 * manifest deliberately declares `BLUETOOTH_SCAN` without the location opt-out
 * flag because detections are combined with the phone position, and on Android 12+
 * the Bluetooth stack only delivers scan results to such an app while
 * `ACCESS_FINE_LOCATION` is granted and Location Services are enabled. On
 * Android 8-11 the location grant is the classic BLE scan requirement.
 */
object PermissionUtils {

    /**
     * Pure matrix shared by the runtime helpers and unit tests.  The API 31
     * permission names are compile-time string constants, so referencing them
     * behind an explicit [sdkInt] check is safe on older devices.
     */
    @SuppressLint("InlinedApi")
    fun requiredPermissionsFor(sdkInt: Int): List<String> {
        val permissions = mutableListOf<String>()
        if (sdkInt >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        // Coarse and precise are requested together so Android 12+ offers the
        // precise option in its dialog; ACCESS_FINE_LOCATION is the grant that
        // actually has to succeed.
        permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
        return permissions
    }

    fun getRequiredPermissions(): List<String> = requiredPermissionsFor(Build.VERSION.SDK_INT)

    fun getAllPermissionsToRequest(): Array<String> =
        getRequiredPermissions().distinct().toTypedArray()

    fun hasPreciseLocationPermission(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** Optional notification permission requested only after the user enables notifications. */
    fun getNotificationPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    fun hasNotificationPermission(context: Context): Boolean =
        getNotificationPermission()?.let { isGranted(context, it) } ?: true

    fun hasAllRequiredPermissions(context: Context): Boolean =
        getRequiredPermissions().all { isGranted(context, it) }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
