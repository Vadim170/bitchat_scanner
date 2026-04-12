package com.vadim170.bitchatscanner.utils

/**
 */
object RssiUtils {
    /**
     * 
     */
    fun calculateRadiusFromRSSI(rssi: Int): Double {
        return when {
            rssi > -50 -> 5.0
            rssi > -60 -> 10.0
            rssi > -70 -> 20.0
            rssi > -80 -> 50.0
            else -> 100.0
        }
    }
    
    /**
     * 
     */
    fun getFillColorForRSSI(rssi: Int): Int {
        return when {
            rssi > -50 -> 0x4000FF00
            rssi > -70 -> 0x40FFFF00
            else -> 0x40FF0000
        }
    }
    
    /**
     * 
     */
    fun getStrokeColorForRSSI(rssi: Int): Int {
        return when {
            rssi > -50 -> 0x8000AA00.toInt()
            rssi > -70 -> 0x80AAAA00.toInt()
            else -> 0x80AA0000.toInt()
        }
    }
}
