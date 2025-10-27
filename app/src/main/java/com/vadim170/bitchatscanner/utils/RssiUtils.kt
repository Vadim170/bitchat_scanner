package com.vadim170.bitchatscanner.utils

/**
 * Утилиты для работы с RSSI (Received Signal Strength Indicator)
 */
object RssiUtils {
    /**
     * Вычисляет приблизительный радиус обнаружения на основе силы сигнала RSSI
     * 
     * @param rssi Сила сигнала в dBm
     * @return Радиус в метрах
     */
    fun calculateRadiusFromRSSI(rssi: Int): Double {
        return when {
            rssi > -50 -> 5.0   // Очень близко - 5 метров
            rssi > -60 -> 10.0  // Близко - 10 метров  
            rssi > -70 -> 20.0  // Средне - 20 метров
            rssi > -80 -> 50.0  // Далеко - 50 метров
            else -> 100.0       // Очень далеко - 100 метров
        }
    }
    
    /**
     * Определяет цвет заливки круга на основе RSSI
     * 
     * @param rssi Сила сигнала в dBm
     * @return Цвет в формате ARGB (Int)
     */
    fun getFillColorForRSSI(rssi: Int): Int {
        return when {
            rssi > -50 -> 0x4000FF00  // Зеленый (полупрозрачный)
            rssi > -70 -> 0x40FFFF00  // Желтый (полупрозрачный)
            else -> 0x40FF0000        // Красный (полупрозрачный)
        }
    }
    
    /**
     * Определяет цвет обводки круга на основе RSSI
     * 
     * @param rssi Сила сигнала в dBm
     * @return Цвет в формате ARGB (Int)
     */
    fun getStrokeColorForRSSI(rssi: Int): Int {
        return when {
            rssi > -50 -> 0x8000AA00.toInt()  // Темно-зеленый
            rssi > -70 -> 0x80AAAA00.toInt()  // Темно-желтый
            else -> 0x80AA0000.toInt()        // Темно-красный
        }
    }
}
