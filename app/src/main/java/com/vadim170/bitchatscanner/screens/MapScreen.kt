package com.vadim170.bitchatscanner.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vadim170.bitchatscanner.LocationPoint
import com.vadim170.bitchatscanner.R
import com.vadim170.bitchatscanner.constants.MapConstants
import com.vadim170.bitchatscanner.repository.ScannerRepository
import com.vadim170.bitchatscanner.utils.MapUtils
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView

/**
 * 
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allLocations by remember { mutableStateOf<List<LocationPoint>>(emptyList()) }
    

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }
    

    LaunchedEffect(Unit) {
        val repository = ScannerRepository.getInstance(context)
        repository.loadDevices()
        

        repository.devices.collect { devices ->
            val allDeviceLocations = mutableListOf<LocationPoint>()
            
            devices.forEach { device ->
                val deviceLocations = repository.getDeviceLocations(device.address)
                allDeviceLocations.addAll(deviceLocations)
            }
            
            allLocations = allDeviceLocations
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.all_detections_map)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (allLocations.isNotEmpty()) {

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        createFullScreenMap(ctx, allLocations)
                    },
                    update = { mapView ->
                        updateFullScreenMap(mapView, allLocations)
                    }
                )
                

                val filteredCount = MapUtils.filterLocationsByMinRadius(allLocations).size
                Text(
                    text = stringResource(R.string.total_detections, filteredCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            } else {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_devices_found),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 */
private fun createFullScreenMap(context: android.content.Context, locations: List<LocationPoint>): MapView {
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        

        setMultiTouchControls(true)
        setFlingEnabled(true)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        

        zoomController.setVisibility(
            org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT
        )
        
        setupMapViewport(this, locations)
        drawDetectionCircles(this, locations)
    }
}

/**
 */
private fun updateFullScreenMap(mapView: MapView, locations: List<LocationPoint>) {
    mapView.overlays.clear()
    drawDetectionCircles(mapView, locations)
    mapView.invalidate()
}

/**
 */
private fun setupMapViewport(mapView: MapView, locations: List<LocationPoint>) {
    val mapController = mapView.controller
    val center = MapUtils.calculateCenter(locations)
    mapController.setCenter(center)
    
    if (locations.size == 1) {
        mapController.setZoom(MapConstants.SINGLE_POINT_ZOOM)
    } else {
        val expandedBoundingBox = MapUtils.calculateExpandedBoundingBox(locations)
        mapView.post {
            mapView.zoomToBoundingBox(
                expandedBoundingBox, 
                true, 
                MapConstants.FULLSCREEN_MAP_PADDING
            )
        }
    }
}

/**
 */
private fun drawDetectionCircles(mapView: MapView, locations: List<LocationPoint>) {
    val filteredLocations = MapUtils.filterLocationsByMinRadius(locations)
    
    filteredLocations.forEach { location ->
        val circle = MapUtils.createDetectionCircle(
            location, 
            MapConstants.CIRCLE_POINTS_COUNT
        )
        mapView.overlays.add(circle)
    }
}
