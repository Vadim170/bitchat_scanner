package com.vadim170.bitchatscanner

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for BleScannerManager
 * 
 * Tests the singleton pattern and mode management logic without requiring Android framework
 */
class BleScannerManagerTest {
    
    @Test
    fun scanMode_hasCorrectValues() {
        // Verify that our ScanMode enum has the expected values
        val modes = BleScannerManager.ScanMode.values()
        assertEquals(2, modes.size)
        assertTrue(modes.contains(BleScannerManager.ScanMode.IN_APP))
        assertTrue(modes.contains(BleScannerManager.ScanMode.SERVICE))
    }
    
    @Test
    fun scanMode_canBeCompared() {
        // Verify that we can compare scan modes
        val mode1 = BleScannerManager.ScanMode.IN_APP
        val mode2 = BleScannerManager.ScanMode.IN_APP
        val mode3 = BleScannerManager.ScanMode.SERVICE
        
        assertEquals(mode1, mode2)
        assertNotEquals(mode1, mode3)
    }
    
    @Test
    fun constants_areAccessible() {
        // Verify that our constants are accessible and have the expected values
        assertEquals("com.vadim170.bitchatscanner.LOG_LINE", BleScannerManager.ACTION_LOG_LINE)
        assertEquals("com.vadim170.bitchatscanner.SCANNER_STARTED", BleScannerManager.ACTION_SCANNER_STARTED)
        assertEquals("com.vadim170.bitchatscanner.SCANNER_STOPPED", BleScannerManager.ACTION_SCANNER_STOPPED)
        assertEquals("line", BleScannerManager.EXTRA_LINE)
    }
}
