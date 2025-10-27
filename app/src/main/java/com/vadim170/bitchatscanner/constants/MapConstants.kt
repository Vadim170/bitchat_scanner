package com.vadim170.bitchatscanner.constants

/**
 * Константы для работы с картами и отображением устройств
 */
object MapConstants {
    /** Время в миллисекундах, в течение которого устройство считается "недавно обнаруженным" */
    const val RECENT_DETECTION_THRESHOLD_MS = 60_000L // 1 минута
    
    /** Минимальный размер карты в градусах (примерно 400 метров) */
    const val MIN_MAP_SIZE_DEGREES = 0.004
    
    /** Отступ для bounding box карты в пикселях */
    const val MAP_BOUNDING_BOX_PADDING = 50
    
    /** Отступ для полноэкранной карты */
    const val FULLSCREEN_MAP_PADDING = 100
    
    /** Количество точек для отрисовки круга на карте */
    const val CIRCLE_POINTS_COUNT = 32
    
    /** Упрощённое количество точек для маленьких карточек */
    const val CIRCLE_POINTS_COUNT_SIMPLE = 16
    
    /** Ширина обводки круга */
    const val CIRCLE_STROKE_WIDTH = 2f
    
    /** Уровень зума для одной точки */
    const val SINGLE_POINT_ZOOM = 16.0
    
    /** Количество метров на градус широты */
    const val METERS_PER_DEGREE_LAT = 111_320.0

    /** Минимальный шаг улучшения радиуса (м) для перерисовки */
    const val MIN_RADIUS_IMPROVEMENT_METERS = 1.0

    /** Количество знаков при нормализации радиуса (0.1 м) */
    const val RADIUS_NORMALIZATION_SCALE = 10.0
}
