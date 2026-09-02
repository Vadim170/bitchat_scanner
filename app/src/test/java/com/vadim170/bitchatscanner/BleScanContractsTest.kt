package com.vadim170.bitchatscanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleScanContractsTest {
    @Test
    fun transitionStartsTheRequestedRegistration() {
        assertEquals(
            listOf(ScanOperation.START_CALLBACK),
            planScanTransition(ScanRegistration.NONE, ScanRegistration.CALLBACK),
        )
        assertEquals(
            listOf(ScanOperation.START_PENDING_INTENT),
            planScanTransition(ScanRegistration.NONE, ScanRegistration.PENDING_INTENT),
        )
    }

    @Test
    fun transitionStopsThePreviousRegistrationBeforeStartingTheNext() {
        assertEquals(
            listOf(ScanOperation.STOP_CALLBACK, ScanOperation.START_PENDING_INTENT),
            planScanTransition(ScanRegistration.CALLBACK, ScanRegistration.PENDING_INTENT),
        )
        assertEquals(
            listOf(ScanOperation.STOP_PENDING_INTENT, ScanOperation.START_CALLBACK),
            planScanTransition(ScanRegistration.PENDING_INTENT, ScanRegistration.CALLBACK),
        )
    }

    @Test
    fun stoppingEitherRegistrationLeavesNoActiveRegistration() {
        assertEquals(
            listOf(ScanOperation.STOP_CALLBACK),
            planScanTransition(ScanRegistration.CALLBACK, ScanRegistration.NONE),
        )
        assertEquals(
            listOf(ScanOperation.STOP_PENDING_INTENT),
            planScanTransition(ScanRegistration.PENDING_INTENT, ScanRegistration.NONE),
        )
    }

    @Test
    fun repeatedTransitionToTheSameRegistrationIsIdempotent() {
        assertEquals(
            emptyList<ScanOperation>(),
            planScanTransition(ScanRegistration.NONE, ScanRegistration.NONE),
        )
        assertEquals(
            emptyList<ScanOperation>(),
            planScanTransition(ScanRegistration.CALLBACK, ScanRegistration.CALLBACK),
        )
        assertEquals(
            emptyList<ScanOperation>(),
            planScanTransition(ScanRegistration.PENDING_INTENT, ScanRegistration.PENDING_INTENT),
        )
    }

    @Test
    fun everyRegistrationSwitchStopsBeforeStartingAndHasAtMostOneStart() {
        for (from in ScanRegistration.entries) {
            for (to in ScanRegistration.entries) {
                val operations = planScanTransition(from, to)
                val firstStart = operations.indexOfFirst { it.isStart() }
                val lastStop = operations.indexOfLast { it.isStop() }

                assertTrue("at most one scan registration may start: $from -> $to", operations.count { it.isStart() } <= 1)
                if (firstStart >= 0) {
                    assertTrue("the old registration must stop first: $from -> $to", lastStop < firstStart)
                }
            }
        }
    }

    @Test
    fun notificationRequiresTheFeatureAndPermission() {
        assertEquals(false, notificationEligible(false, true, 1_000L, null, 100L))
        assertEquals(false, notificationEligible(true, false, 1_000L, null, 100L))
        assertEquals(true, notificationEligible(true, true, 1_000L, null, 100L))
    }

    @Test
    fun notificationCooldownUsesAnInclusiveBoundary() {
        val lastNotified = 1_000L

        assertEquals(
            false,
            notificationEligible(true, true, lastNotified + 99L, lastNotified, 100L),
        )
        assertEquals(
            true,
            notificationEligible(true, true, lastNotified + 100L, lastNotified, 100L),
        )
    }

    @Test
    fun notificationDoesNotBypassCooldownForAClockThatMovesBackwards() {
        assertEquals(
            false,
            notificationEligible(true, true, 900L, 1_000L, 100L),
        )
    }

    private fun ScanOperation.isStart(): Boolean =
        this == ScanOperation.START_CALLBACK || this == ScanOperation.START_PENDING_INTENT

    private fun ScanOperation.isStop(): Boolean =
        this == ScanOperation.STOP_CALLBACK || this == ScanOperation.STOP_PENDING_INTENT
}
