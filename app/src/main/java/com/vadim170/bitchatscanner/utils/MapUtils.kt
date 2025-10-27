package com.vadim170.bitchatscanner.utils

import com.vadim170.bitchatscanner.LocationPoint
import com.vadim170.bitchatscanner.constants.MapConstants
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import kotlin.math.cos
import kotlin.math.sin

/**
 * Утилиты для работы с картами OSMdroid
 */
object MapUtils {
    
    /**
     * Фильтрует локации, оставляя для каждой уникальной координаты только точку с минимальным радиусом
     * Радиус вычисляется как СУММА rssiRadius + gpsAccuracy
     * 
     * @param locations Список всех локаций
     * @return Отфильтрованный список локаций
     */
    fun filterLocationsByMinRadius(locations: List<LocationPoint>): List<LocationPoint> {
        return locations
            .groupBy { "${it.lat},${it.lon}" } // Группируем по координатам
            .mapValues { (_, pointsAtLocation) ->
                // Для каждой группы выбираем точку с минимальной СУММОЙ радиусов
                pointsAtLocation.minByOrNull { point ->
                    val rssiRadius = RssiUtils.calculateRadiusFromRSSI(point.rssi)
                    val gpsAccuracy = point.accuracy ?: 10f
                    rssiRadius + gpsAccuracy.toDouble()  // СУММА, не максимум!
                }!!
            }
            .values
            .toList()
    }
    
    /**
     * Вычисляет расширенный bounding box с минимальным размером
     * 
     * @param locations Список локаций
     * @return BoundingBox с учётом минимального размера
     */
    fun calculateExpandedBoundingBox(locations: List<LocationPoint>): BoundingBox {
        val geoPoints = locations.map { GeoPoint(it.lat, it.lon) }
        val boundingBox = BoundingBox.fromGeoPoints(geoPoints)
        
        val currentWidthDegrees = boundingBox.lonEast - boundingBox.lonWest
        val currentHeightDegrees = boundingBox.latNorth - boundingBox.latSouth
        
        return if (currentWidthDegrees < MapConstants.MIN_MAP_SIZE_DEGREES || 
                   currentHeightDegrees < MapConstants.MIN_MAP_SIZE_DEGREES) {
            val expandWidth = maxOf(0.0, (MapConstants.MIN_MAP_SIZE_DEGREES - currentWidthDegrees) / 2)
            val expandHeight = maxOf(0.0, (MapConstants.MIN_MAP_SIZE_DEGREES - currentHeightDegrees) / 2)
            
            BoundingBox(
                boundingBox.latNorth + expandHeight,
                boundingBox.lonEast + expandWidth,
                boundingBox.latSouth - expandHeight,
                boundingBox.lonWest - expandWidth
            )
        } else {
            boundingBox
        }
    }
    
    /**
     * Создаёт круг-полигон для отображения зоны обнаружения на карте
     * Радиус круга = rssiRadius + gpsAccuracy (сумма, не максимум)
     * 
     * @param location Локация с данными об обнаружении
     * @param numPoints Количество точек для построения круга (чем больше, тем плавнее)
     * @return Polygon представляющий круг
     */
    fun createDetectionCircle(location: LocationPoint, numPoints: Int = MapConstants.CIRCLE_POINTS_COUNT): Polygon {
        val rssiRadius = RssiUtils.calculateRadiusFromRSSI(location.rssi)
        val gpsAccuracy = location.accuracy ?: 10f
        val finalRadius = rssiRadius + gpsAccuracy.toDouble()  // СУММА радиусов
        
        val circle = Polygon()
        val circlePoints = mutableListOf<GeoPoint>()
        
        for (i in 0..numPoints) {
            val angle = 2 * Math.PI * i / numPoints
            val latOffset = finalRadius * cos(angle) / MapConstants.METERS_PER_DEGREE_LAT
            val lonOffset = finalRadius * sin(angle) / 
                (MapConstants.METERS_PER_DEGREE_LAT * cos(Math.toRadians(location.lat)))
            circlePoints.add(GeoPoint(location.lat + latOffset, location.lon + lonOffset))
        }
        circle.points = circlePoints
        
        // Настройка цветов на основе RSSI
        circle.fillColor = RssiUtils.getFillColorForRSSI(location.rssi)
        circle.strokeColor = RssiUtils.getStrokeColorForRSSI(location.rssi)
        circle.strokeWidth = MapConstants.CIRCLE_STROKE_WIDTH
        
        return circle
    }
    
    /**
     * Вычисляет центр для списка локаций
     * 
     * @param locations Список локаций
     * @return GeoPoint представляющий центр
     */
    fun calculateCenter(locations: List<LocationPoint>): GeoPoint {
        val centerLat = locations.map { it.lat }.average()
        val centerLon = locations.map { it.lon }.average()
        return GeoPoint(centerLat, centerLon)
    }
}
