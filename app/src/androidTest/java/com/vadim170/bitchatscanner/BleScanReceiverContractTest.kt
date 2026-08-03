package com.vadim170.bitchatscanner

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Contracts shared by the foreground callback and the background receiver. */
@RunWith(AndroidJUnit4::class)
class BleScanReceiverContractTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val coordinator by lazy { BleScanCoordinator.getInstance(context) }
    private val database by lazy { DetectionDbHelper(context) }

    @Before
    fun resetScannerAndDatabase() {
        coordinator.onHostStopped()
        coordinator.stopSession()
        database.clearAll()
    }

    @After
    fun cleanUpScannerAndDatabase() {
        coordinator.onHostStopped()
        coordinator.stopSession()
        database.clearAll()
    }

    @Test
    fun nullAndMalformedPendingIntentExtrasAreIgnored() {
        val empty = extractPendingScanPayload(Intent())
        assertTrue(empty.results.isEmpty())
        assertNull(empty.errorCode)

        val malformed = Intent().putExtra(
            BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
            "not a parcelable scan-result list",
        )
        val malformedPayload = extractPendingScanPayload(malformed)
        assertTrue(malformedPayload.results.isEmpty())
        assertNull(malformedPayload.errorCode)
    }

    @Test
    fun pendingIntentErrorCodeIsPreservedAndNegativeSentinelIsIgnored() {
        val error = Intent().putExtra(
            BluetoothLeScanner.EXTRA_ERROR_CODE,
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED,
        )
        assertEquals(
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED,
            extractPendingScanPayload(error).errorCode,
        )

        val noError = Intent().putExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
        assertNull(extractPendingScanPayload(noError).errorCode)
    }

    @Test
    fun receiverFinishesMalformedAndErrorBroadcasts() {
        coordinator.onHostStopped()
        coordinator.startSession()

        val emptyFinished = CountDownLatch(1)
        coordinator.handlePendingIntent(Intent()) { emptyFinished.countDown() }
        assertTrue(
            "receiver did not finish empty broadcast",
            emptyFinished.await(5, TimeUnit.SECONDS),
        )

        val malformedFinished = CountDownLatch(1)
        coordinator.handlePendingIntent(
            Intent().putExtra(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
                "malformed",
            ),
        ) { malformedFinished.countDown() }
        assertTrue(
            "receiver did not finish malformed broadcast",
            malformedFinished.await(5, TimeUnit.SECONDS),
        )

        val errorFinished = CountDownLatch(1)
        coordinator.handlePendingIntent(
            Intent().putExtra(
                BluetoothLeScanner.EXTRA_ERROR_CODE,
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR,
            ),
        ) { errorFinished.countDown() }
        assertTrue(
            "receiver did not finish error broadcast",
            errorFinished.await(5, TimeUnit.SECONDS),
        )
        assertEquals(BleScanCoordinator.State.ERROR, coordinator.state.value)
    }

    @Test
    fun strictMatcherRejectsNullMalformedAndForeignAdvertisements() {
        assertFalse(matchesBitChatAdvertisement(null))
        assertFalse(matchesBitChatAdvertisement(parseScanRecord(byteArrayOf(1, 0x06))))

        val foreignRecord = parseScanRecord(
            serviceDataAdvertisement(UUID.randomUUID(), byteArrayOf(0x01)),
        )
        assertFalse(matchesBitChatAdvertisement(foreignRecord))
    }

    @Test
    fun strictMatcherAcceptsBitChatServiceDataAdvertisement() {
        val record = parseScanRecord(
            serviceDataAdvertisement(BitchatBle.SERVICE_UUID, byteArrayOf(0x42, 0x17)),
        )
        assertTrue(matchesBitChatAdvertisement(record))
        assertTrue(coordinator.acceptsResult(assumeScanResult(record)))
    }

    @Test
    fun validBackgroundResultIsPersistedWithoutLocation() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        assumeNotNull(adapter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assumeTrue(
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
            )
        }

        val record = parseScanRecord(
            serviceDataAdvertisement(BitchatBle.SERVICE_UUID, byteArrayOf(0x10, 0x20)),
        )
        val device = try {
            adapter!!.getRemoteDevice("02:00:00:00:00:01")
        } catch (_: IllegalArgumentException) {
            return
        }
        val result = ScanResult(device, record, -61, SystemClock.elapsedRealtimeNanos())

        // A hidden start persists the desired state and is the path used by
        // the PendingIntent receiver. A missing adapter/permission may make
        // registration fail, but does not clear the desired intent.
        coordinator.onHostStopped()
        coordinator.startSession()
        assertTrue(coordinator.isScanDesired())

        val finished = CountDownLatch(1)
        val intent = Intent().putParcelableArrayListExtra(
            BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
            arrayListOf(result),
        )
        coordinator.handlePendingIntent(intent) { finished.countDown() }

        assertTrue("receiver work did not finish", finished.await(5, TimeUnit.SECONDS))
        val deadline = SystemClock.uptimeMillis() + 5_000L
        var rows = emptyList<DetectionRow>()
        while (SystemClock.uptimeMillis() < deadline) {
            rows = database.latest().filter { it.address == "02:00:00:00:00:01" }
            if (rows.isNotEmpty()) break
            Thread.sleep(50L)
        }

        assertTrue("valid background result was not persisted", rows.isNotEmpty())
        val row = rows.first()
        assertEquals("02:00:00:00:00:01", row.address)
        assertNull("background scans must not read location", row.lat)
        assertNull("background scans must not read location", row.lon)
        assertNull("background scans must not read location", row.accuracy)
        assertNull("background scans must not read location", row.provider)
    }

    private fun assumeScanResult(record: ScanRecord): ScanResult {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        assumeNotNull(adapter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assumeTrue(
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
            )
        }
        return ScanResult(
            adapter!!.getRemoteDevice("02:00:00:00:00:02"),
            record,
            -42,
            SystemClock.elapsedRealtimeNanos(),
        )
    }

    private fun serviceDataAdvertisement(uuid: UUID, data: ByteArray): ByteArray {
        val uuidBytes = bleUuidBytes(uuid)
        val adLength = 1 + uuidBytes.size + data.size
        return byteArrayOf(adLength.toByte(), 0x21) + uuidBytes + data
    }

    /**
     * ScanRecord.parseFromBytes is package-private in the Android SDK. The
     * platform itself uses it to decode callback records, so use reflection
     * only in this instrumentation fixture; unsupported runtimes skip the
     * fixture rather than weakening production visibility.
     */
    private fun parseScanRecord(bytes: ByteArray): ScanRecord {
        val method = try {
            ScanRecord::class.java.getDeclaredMethod("parseFromBytes", ByteArray::class.java)
                .apply { isAccessible = true }
        } catch (_: Exception) {
            assumeTrue("ScanRecord parser is unavailable on this runtime", false)
            error("unreachable")
        }
        val record = try {
            method.invoke(null, bytes) as? ScanRecord
        } catch (_: Exception) {
            null
        }
        assumeNotNull(record)
        return record!!
    }

    /** Match BluetoothUuid.uuidToBytes: little-endian LSB followed by MSB. */
    private fun bleUuidBytes(uuid: UUID): ByteArray {
        fun littleEndian(value: Long): ByteArray = ByteArray(Long.SIZE_BYTES) { index ->
            (value ushr (index * Byte.SIZE_BITS)).toByte()
        }
        return littleEndian(uuid.leastSignificantBits) +
                littleEndian(uuid.mostSignificantBits)
    }
}
