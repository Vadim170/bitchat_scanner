package com.vadim170.bitchatscanner.utils

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest declares BLUETOOTH_SCAN without neverForLocation, so precise
 * location must be part of the required set on every supported API level;
 * otherwise Android 12+ starts the scan but never delivers a result.
 */
class PermissionUtilsTest {
    private val supportedSdks = 26..36

    @Test
    fun preciseLocationIsRequiredOnEverySupportedApiLevel() {
        for (sdk in supportedSdks) {
            val required = PermissionUtils.requiredPermissionsFor(sdk)
            assertTrue(
                "API $sdk must require ACCESS_FINE_LOCATION: $required",
                Manifest.permission.ACCESS_FINE_LOCATION in required,
            )
            assertTrue(
                "API $sdk must request coarse together with precise: $required",
                Manifest.permission.ACCESS_COARSE_LOCATION in required,
            )
        }
    }

    @Test
    fun android12AndAboveAddTheNewBluetoothRuntimePermissions() {
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            PermissionUtils.requiredPermissionsFor(31),
        )
    }

    @Test
    fun legacyReleasesOnlyNeedTheLocationPair() {
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            PermissionUtils.requiredPermissionsFor(30),
        )
    }

    @Test
    fun backgroundLocationAndForegroundServicePermissionsAreNeverRequested() {
        for (sdk in supportedSdks) {
            val required = PermissionUtils.requiredPermissionsFor(sdk)
            assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in required)
            assertFalse(Manifest.permission.FOREGROUND_SERVICE in required)
            assertFalse(Manifest.permission.BLUETOOTH_ADVERTISE in required)
        }
    }
}
