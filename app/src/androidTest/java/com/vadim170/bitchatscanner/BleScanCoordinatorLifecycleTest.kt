package com.vadim170.bitchatscanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the coordinator's host-visibility contract on a real Android
 * runtime. The tests do not require a nearby BLE peripheral: a missing runtime
 * permission or unavailable adapter is a valid failed start, while the
 * lifecycle guarantees must hold in either case.
 */
@RunWith(AndroidJUnit4::class)
class BleScanCoordinatorLifecycleTest {
    private val coordinator by lazy {
        BleScanCoordinator.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
    }

    @Before
    fun resetCoordinator() {
        coordinator.onHostStopped()
        coordinator.stopSession()
    }

    @After
    fun stopCoordinator() {
        coordinator.onHostStopped()
        coordinator.stopSession()
    }

    @Test
    fun sessionCannotStartWhileHostIsHidden() {
        coordinator.onHostStopped()

        coordinator.startSession()

        // A hidden Start intent is persisted and chooses the passive
        // PendingIntent path. Hardware/permission failures are valid on a
        // test device without a usable BLE adapter, but it must never become
        // the visible callback state while the host is hidden.
        assertTrue(coordinator.isScanDesired())
        assertFalse(coordinator.state.value == BleScanCoordinator.State.ACTIVE_VISIBLE)
        coordinator.stopSession()
        assertFalse(coordinator.isScanDesired())
        assertEquals(BleScanCoordinator.State.STOPPED, coordinator.state.value)
    }

    @Test
    fun stoppingHostAlwaysStopsSessionAndPreventsRestart() {
        coordinator.onHostStarted()
        coordinator.startSession()

        coordinator.onHostStopped()

        assertFalse(coordinator.state.value == BleScanCoordinator.State.ACTIVE_VISIBLE)
        assertTrue(coordinator.isScanDesired())

        coordinator.stopSession()
        assertFalse(coordinator.isScanDesired())
        assertEquals(BleScanCoordinator.State.STOPPED, coordinator.state.value)
    }

    @Test
    fun returningToForegroundReclaimsPassiveRegistrationBeforeCallback() {
        coordinator.onHostStopped()
        coordinator.startSession()
        assertTrue(coordinator.isScanDesired())

        coordinator.onHostStarted()
        coordinator.onHostResumed()

        // When the adapter and permissions are available this is the
        // ACTIVE_VISIBLE callback state. If a test device cannot register BLE,
        // it may report a permission/error state, but it must never leave the
        // passive PendingIntent state while the host is visible.
        assertFalse(coordinator.state.value == BleScanCoordinator.State.PASSIVE_BACKGROUND)
    }

    @Test
    fun repeatedStartWhileScanningIsIdempotentWhenHardwareAllowsStart() {
        coordinator.onHostStarted()
        val firstStart = coordinator.startSession()
        val secondStart = coordinator.startSession()

        // On CI/emulators without BLE permissions or a usable adapter a start
        // can legitimately fail. Once a scan does start, the second call must
        // be accepted without registering another hardware scan.
        if (firstStart) {
            assertTrue(secondStart)
            assertTrue(coordinator.state.value == BleScanCoordinator.State.ACTIVE_VISIBLE)
        }
    }

    @Test
    fun repeatedStopIsIdempotent() {
        coordinator.onHostStarted()
        coordinator.startSession()

        coordinator.stopSession()
        coordinator.stopSession()

        assertEquals(BleScanCoordinator.State.STOPPED, coordinator.state.value)
    }
}
