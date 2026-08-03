package com.vadim170.bitchatscanner

import com.vadim170.bitchatscanner.utils.RssiUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class RssiUtilsTest {
    @Test
    fun radiusUsesExpectedSignalBoundaries() {
        assertEquals(5.0, RssiUtils.calculateRadiusFromRSSI(-49), 0.0)
        assertEquals(10.0, RssiUtils.calculateRadiusFromRSSI(-50), 0.0)
        assertEquals(20.0, RssiUtils.calculateRadiusFromRSSI(-60), 0.0)
        assertEquals(50.0, RssiUtils.calculateRadiusFromRSSI(-70), 0.0)
        assertEquals(100.0, RssiUtils.calculateRadiusFromRSSI(-80), 0.0)
    }

    @Test
    fun signalColorsMoveFromGreenToYellowToRed() {
        assertEquals(0x4000FF00, RssiUtils.getFillColorForRSSI(-40))
        assertEquals(0x40FFFF00, RssiUtils.getFillColorForRSSI(-60))
        assertEquals(0x40FF0000, RssiUtils.getFillColorForRSSI(-80))
        assertEquals(0x8000AA00.toInt(), RssiUtils.getStrokeColorForRSSI(-40))
        assertEquals(0x80AAAA00.toInt(), RssiUtils.getStrokeColorForRSSI(-60))
        assertEquals(0x80AA0000.toInt(), RssiUtils.getStrokeColorForRSSI(-80))
    }
}
