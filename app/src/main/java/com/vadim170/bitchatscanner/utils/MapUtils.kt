package com.vadim170.bitchatscanner.utils

import com.vadim170.bitchatscanner.LocationPoint
import com.vadim170.bitchatscanner.constants.MapConstants
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import kotlin.math.cos
import kotlin.math.sin

/**
 */
object MapUtils {
    
    /**
     * 
     */
    fun filterLocationsByMinRadius(locations: List<LocationPoint>): List<LocationPoint> {
        return locations
            .groupBy { "${it.lat},${it.lon}" }
            .mapValues { (_, pointsAtLocation) ->

                pointsAtLocation.minByOrNull { point ->
                    val rssiRadius = RssiUtils.calculateRadiusFromRSSI(point.rssi)
                    val gpsAccuracy = point.accuracy ?: 10f
                    rssiRadius + gpsAccuracy.toDouble()
                }!!
            }
            .values
            .toList()
    }
    
    /**
     * 
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
     * 
     */
    fun createDetectionCircle(location: LocationPoint, numPoints: Int = MapConstants.CIRCLE_POINTS_COUNT): Polygon {
        val rssiRadius = RssiUtils.calculateRadiusFromRSSI(location.rssi)
        val gpsAccuracy = location.accuracy ?: 10f
        val finalRadius = rssiRadius + gpsAccuracy.toDouble()
        
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
        

        circle.fillColor = RssiUtils.getFillColorForRSSI(location.rssi)
        circle.strokeColor = RssiUtils.getStrokeColorForRSSI(location.rssi)
        circle.strokeWidth = MapConstants.CIRCLE_STROKE_WIDTH
        
        return circle
    }
    
    /**
     * 
     */
    fun calculateCenter(locations: List<LocationPoint>): GeoPoint {
        val centerLat = locations.map { it.lat }.average()
        val centerLon = locations.map { it.lon }.average()
        return GeoPoint(centerLat, centerLon)
    }
}
