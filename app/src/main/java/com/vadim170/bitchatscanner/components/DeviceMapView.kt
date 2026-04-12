package com.vadim170.bitchatscanner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vadim170.bitchatscanner.LocationPoint
import com.vadim170.bitchatscanner.R
import com.vadim170.bitchatscanner.constants.MapConstants
import com.vadim170.bitchatscanner.utils.MapUtils
import kotlin.math.roundToInt
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView

/**
 * 
 */
@Composable
fun DeviceMapView(
    locations: List<LocationPoint>,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    isLoading: Boolean = false,
    hasError: Boolean = false
) {
    val context = LocalContext.current
    val mapViewHolder = remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mapViewHolder.value?.onDetach()
            mapViewHolder.value = null
        }
    }

    when {
        !isVisible -> {
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.map_hidden_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        isLoading -> {
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        hasError -> {
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.map_load_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        locations.isEmpty() -> {
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_coordinates),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        else -> {
            AndroidView(
                modifier = modifier,
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = context.packageName

                    MapView(ctx).apply {
                        mapViewHolder.value = this
                        setTileSource(TileSourceFactory.MAPNIK)


                        setMultiTouchControls(false)
                        setFlingEnabled(false)
                        isClickable = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        overlayManager.tilesOverlay.isEnabled = true
                        setOnTouchListener { _, _ -> true }
                        isHorizontalMapRepetitionEnabled = false
                        isVerticalMapRepetitionEnabled = false
                        isTilesScaledToDpi = false

                        updateMapContent(this, locations)
                    }
                },
                update = { view ->
                    updateMapContent(view, locations)
                }
            )
        }
    }
}

/**
 */
private fun updateMapContent(mapView: MapView, locations: List<LocationPoint>) {

    val oldData = mapView.tag as? MapData
    val newData = calculateMapData(locations)
    

    if (oldData != null && !shouldRedraw(oldData, newData)) {
        return
    }

    mapView.tag = newData
    mapView.overlays.clear()

    if (locations.isEmpty()) {
        mapView.invalidate()
        return
    }

    val filteredLocations = MapUtils.filterLocationsByMinRadius(locations)
    val controller = mapView.controller
    

    if (filteredLocations.size == 1) {
        val center = MapUtils.calculateCenter(filteredLocations)
        controller.setCenter(center)
        controller.setZoom(MapConstants.SINGLE_POINT_ZOOM)
    } else {
        val expandedBoundingBox = MapUtils.calculateExpandedBoundingBox(filteredLocations)
        mapView.post {
            mapView.zoomToBoundingBox(expandedBoundingBox, false, MapConstants.MAP_BOUNDING_BOX_PADDING)
        }
    }


    filteredLocations.forEach { location ->
        val circle = MapUtils.createDetectionCircle(location, MapConstants.CIRCLE_POINTS_COUNT_SIMPLE)
        mapView.overlays.add(circle)
    }

    mapView.invalidate()
}

/**
 */
private data class MapData(
    val coordsHash: Int,
    val minRadiusByCoords: Map<String, Double>
)

/**
 */
private fun calculateMapData(locations: List<LocationPoint>): MapData {
    if (locations.isEmpty()) {
        return MapData(0, emptyMap())
    }
    

    val groupedByCoords = locations.groupBy { "${it.lat},${it.lon}" }
    

    val coordsHash = groupedByCoords.keys.sorted().hashCode()
    

    val minRadiusByCoords = groupedByCoords.mapValues { (_, points) ->
        points.minOf { point ->
            val rssiRadius = com.vadim170.bitchatscanner.utils.RssiUtils.calculateRadiusFromRSSI(point.rssi)
            val gpsAccuracy = point.accuracy?.toDouble() ?: 10.0
            normalizeRadius(rssiRadius + gpsAccuracy)
        }
    }
    
    return MapData(coordsHash, minRadiusByCoords)
}

/**
 */
private fun shouldRedraw(oldData: MapData, newData: MapData): Boolean {

    if (oldData.coordsHash != newData.coordsHash) {
        return true
    }
    

    for ((coords, newMinRadius) in newData.minRadiusByCoords) {
        val oldMinRadius = oldData.minRadiusByCoords[coords] ?: Double.MAX_VALUE
        

        if (newMinRadius + MapConstants.MIN_RADIUS_IMPROVEMENT_METERS <= oldMinRadius) {
            return true
        }
    }
    
    return false
}

/**
 */
private fun normalizeRadius(radius: Double): Double {
    val scale = MapConstants.RADIUS_NORMALIZATION_SCALE
    return (radius * scale).roundToInt() / scale
}
