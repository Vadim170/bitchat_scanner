package com.vadim170.bitchatscanner

import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.BluetoothLeScanner
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid

/**
 * The registrations owned by [BleScanCoordinator].  Keeping this small model
 * outside the coordinator gives tests a deterministic way to verify that a
 * lifecycle transition can never leave both registrations active.
 */
internal enum class ScanRegistration {
    NONE,
    CALLBACK,
    PENDING_INTENT,
}

internal enum class ScanOperation {
    STOP_CALLBACK,
    STOP_PENDING_INTENT,
    START_CALLBACK,
    START_PENDING_INTENT,
}

/**
 * Return the operations needed to move between registrations.  Stops are
 * deliberately emitted before starts so the Bluetooth stack never owns two
 * registrations for this app at the same time.
 */
internal fun planScanTransition(
    from: ScanRegistration,
    to: ScanRegistration,
): List<ScanOperation> {
    if (from == to) return emptyList()
    val operations = mutableListOf<ScanOperation>()
    if (from == ScanRegistration.CALLBACK) operations += ScanOperation.STOP_CALLBACK
    if (from == ScanRegistration.PENDING_INTENT) {
        operations += ScanOperation.STOP_PENDING_INTENT
    }
    if (to == ScanRegistration.CALLBACK) operations += ScanOperation.START_CALLBACK
    if (to == ScanRegistration.PENDING_INTENT) {
        operations += ScanOperation.START_PENDING_INTENT
    }
    return operations
}

/**
 * Strict protocol match shared by foreground callback and background
 * PendingIntent processing.  A null record is not a BitChat advertisement.
 */
internal fun matchesBitChatAdvertisement(
    record: ScanRecord?,
    serviceUuid: ParcelUuid = ParcelUuid(BitchatBle.SERVICE_UUID),
): Boolean {
    if (record == null) return false
    return record.serviceUuids?.contains(serviceUuid) == true ||
            record.serviceData?.keys?.any { it.uuid == serviceUuid.uuid } == true ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    record.serviceSolicitationUuids?.contains(serviceUuid) == true)
}

/**
 * Typed, API-compatible representation of a Bluetooth scan PendingIntent.
 * Android 13 introduced the class-safe Parcelable overload; older supported
 * releases use the deprecated generic overload.
 */
internal data class PendingScanPayload(
    val results: List<ScanResult>,
    val errorCode: Int?,
)

internal fun extractPendingScanPayload(intent: Intent): PendingScanPayload {
    val results = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
                ScanResult::class.java,
            )?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<ScanResult>(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
            )?.toList().orEmpty()
        }
    } catch (_: RuntimeException) {
        // A malformed or foreign broadcast must not crash the receiver.
        emptyList()
    }
    val errorCode = try {
        intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
            .takeIf { it >= 0 }
    } catch (_: RuntimeException) {
        null
    }
    return PendingScanPayload(results = results, errorCode = errorCode)
}

/** Pure notification gate used before touching Android's NotificationManager. */
internal fun notificationEligible(
    enabled: Boolean,
    permissionGranted: Boolean,
    nowMs: Long,
    lastNotifiedMs: Long?,
    cooldownMs: Long,
): Boolean {
    if (!enabled || !permissionGranted) return false
    return lastNotifiedMs == null ||
            (nowMs >= lastNotifiedMs && nowMs - lastNotifiedMs >= cooldownMs)
}
