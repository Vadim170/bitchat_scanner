package com.vadim170.bitchatscanner

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Contract tests for the merged manifest installed on the test device.
 *
 * These assertions intentionally inspect PackageManager rather than the source
 * manifest. This catches manifest merge regressions and stale declarations in
 * the APK that a source-only grep would miss.
 */
@RunWith(AndroidJUnit4::class)
class ManifestContractTest {
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    private fun packageInfo(): PackageInfo {
        @Suppress("DEPRECATION")
        return targetContext.packageManager.getPackageInfo(
            targetContext.packageName,
            PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PERMISSIONS,
        )
    }

    @Test
    fun appSupportsOnlyAndroid26AndAbove() {
        val minSdk = packageInfo().applicationInfo?.minSdkVersion ?: 0

        assertTrue("The hybrid scan path requires minSdk 26", minSdk >= 26)
    }

    @Test
    fun appDoesNotAllowBackupOfDetectionHistory() {
        val applicationInfo = packageInfo().applicationInfo

        assertNotNull("Application metadata must be present", applicationInfo)
        assertTrue(
            "BLE detection history must not be included in device backups",
            applicationInfo!!.flags and ApplicationInfo.FLAG_ALLOW_BACKUP == 0,
        )
    }

    @Test
    fun noScannerServiceIsRegistered() {
        val services = packageInfo().services.orEmpty().map { it.name }

        assertFalse(
            "The in-process scanner must not be registered as an Android service: $services",
            services.any { it == "com.vadim170.bitchatscanner.BleScannerService" },
        )
    }

    @Test
    fun backgroundScanReceiverIsPresentAndNotExported() {
        val receiver = packageInfo().receivers.orEmpty().firstOrNull {
            it.name == "com.vadim170.bitchatscanner.BleScanReceiver"
        }

        assertNotNull("The background PendingIntent receiver must be registered", receiver)
        assertFalse(
            "The background scan receiver must not be reachable by other applications",
            receiver!!.exported,
        )
    }

    @Test
    fun foregroundServiceAndAdvertisePermissionsAreNotRequested() {
        val info = packageInfo()
        val permissions = info.requestedPermissions.orEmpty().toSet()

        assertFalse(permissions.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertFalse(permissions.contains("android.permission.FOREGROUND_SERVICE_LOCATION"))
        assertFalse(permissions.contains("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"))
        assertFalse(permissions.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        assertFalse(permissions.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
    }

    @Test
    fun notificationsPermissionIsDeclaredForBackgroundScanStatus() {
        val permissions = packageInfo().requestedPermissions.orEmpty().toSet()

        assertTrue(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun scannerStillRequestsBleAndLocationPermissions() {
        val info = packageInfo()
        val permissions = info.requestedPermissions.orEmpty().toSet()

        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    @Test
    fun bleHardwareFeatureIsRequiredByTheApplication() {
        val feature = packageInfo().reqFeatures.orEmpty().firstOrNull {
            it.name == PackageManager.FEATURE_BLUETOOTH_LE
        }

        assertNotNull("BLE hardware feature must be declared", feature)
        assertTrue(
            "BLE hardware feature must be required",
            feature!!.flags and FeatureInfo.FLAG_REQUIRED != 0,
        )
    }

    @Test
    fun bluetoothScanDoesNotOptOutOfLocationAttribution() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val info = packageInfo()
        val permissions = info.requestedPermissions ?: return
        val scanIndex = permissions.indexOf(Manifest.permission.BLUETOOTH_SCAN)
        assertTrue("BLUETOOTH_SCAN must be declared", scanIndex >= 0)

        val flags = info.requestedPermissionsFlags ?: return
        assertNotNull("Permission flags must be available", flags)
        assertTrue(scanIndex < flags.size)
        assertTrue(
            "BLUETOOTH_SCAN must not use neverForLocation",
            flags[scanIndex] and PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION == 0,
        )
    }
}
