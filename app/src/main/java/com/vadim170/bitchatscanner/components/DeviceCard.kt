package com.vadim170.bitchatscanner.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vadim170.bitchatscanner.DeviceSummary
import com.vadim170.bitchatscanner.LocationPoint
import com.vadim170.bitchatscanner.R
import com.vadim170.bitchatscanner.constants.MapConstants
import com.vadim170.bitchatscanner.repository.ScannerRepository
import com.vadim170.bitchatscanner.utils.MapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 
 */
@Composable
fun DeviceCard(device: DeviceSummary, showMap: Boolean) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }


    var locations by remember(device.address) { mutableStateOf<List<LocationPoint>>(emptyList()) }
    var isLoadingLocations by remember(device.address) { mutableStateOf(false) }
    var lastLoadedTimestamp by remember(device.address) { mutableStateOf<Long?>(null) }
    var locationLoadError by remember(device.address) { mutableStateOf<Throwable?>(null) }


    LaunchedEffect(device.address, showMap, device.lastSeen) {
        if (showMap && !isLoadingLocations && lastLoadedTimestamp != device.lastSeen) {
            isLoadingLocations = true
            locationLoadError = null
            try {
                val deviceLocations = withContext(Dispatchers.IO) {
                    ScannerRepository.getInstance(context).getDeviceLocations(device.address)
                }
                

                val hasChanges = locationsHaveChanged(locations, deviceLocations)
                
                if (hasChanges) {
                    locations = deviceLocations
                }
                lastLoadedTimestamp = device.lastSeen
            } catch (t: Throwable) {
                locationLoadError = t
            } finally {
                isLoadingLocations = false
            }
        }
    }


    val isRecentlyDetected = System.currentTimeMillis() - device.lastSeen < MapConstants.RECENT_DETECTION_THRESHOLD_MS


    val cardColor = if (isRecentlyDetected) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = device.address,
                style = MaterialTheme.typography.titleMedium
            )


            if (!device.name.isNullOrEmpty()) {
                Text(
                    text = stringResource(R.string.device_name, device.name),
                    style = MaterialTheme.typography.bodyMedium
                )
            }


            Text(
                text = stringResource(R.string.last_detection, sdf.format(Date(device.lastSeen))),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.first_detection, sdf.format(Date(device.firstSeen))),
                style = MaterialTheme.typography.bodySmall
            )


            Text(
                text = stringResource(R.string.rssi, device.rssi),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.detection_count, device.detectionCount),
                style = MaterialTheme.typography.bodySmall
            )


            if (!device.serviceDataHex.isNullOrEmpty()) {
                Text(
                    text = stringResource(
                        R.string.service_data,
                        "${device.serviceDataHex.take(16)}${if (device.serviceDataHex.length > 16) "…" else ""}"
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }


            if (device.lat != null && device.lon != null) {
                Spacer(modifier = Modifier.height(8.dp))

                DeviceMapView(
                    locations = locations,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    isVisible = showMap,
                    isLoading = isLoadingLocations,
                    hasError = locationLoadError != null
                )

                if (showMap && locations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    val filteredCount = MapUtils.filterLocationsByMinRadius(locations).size
                    Text(
                        text = stringResource(R.string.points_count, filteredCount),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * 
 */
private fun locationsHaveChanged(
    oldLocations: List<LocationPoint>,
    newLocations: List<LocationPoint>
): Boolean {

    if (oldLocations.size != newLocations.size) {
        return true
    }
    

    if (oldLocations.isEmpty() && newLocations.isNotEmpty()) {
        return true
    }
    

    if (oldLocations.isEmpty() && newLocations.isEmpty()) {
        return false
    }
    

    val oldByCoords = oldLocations.groupBy { "${it.lat},${it.lon}" }
    val newByCoords = newLocations.groupBy { "${it.lat},${it.lon}" }
    

    if (newByCoords.keys != oldByCoords.keys) {
        return true
    }
    

    for ((coords, newPoints) in newByCoords) {
        val oldPoints = oldByCoords[coords] ?: continue
        

        val oldMinRadius = oldPoints.minOfOrNull { point ->
            calculateNormalizedRadius(point)
        } ?: Double.MAX_VALUE
        

        val newMinRadius = newPoints.minOfOrNull { point ->
            calculateNormalizedRadius(point)
        } ?: Double.MAX_VALUE
        

        if (newMinRadius + MapConstants.MIN_RADIUS_IMPROVEMENT_METERS <= oldMinRadius) {
            return true
        }
    }
    

    return false
}

private fun calculateNormalizedRadius(point: LocationPoint): Double {
    val rssiRadius = com.vadim170.bitchatscanner.utils.RssiUtils.calculateRadiusFromRSSI(point.rssi)
    val gpsAccuracy = point.accuracy ?: 10f
    val totalRadius = rssiRadius + gpsAccuracy.toDouble()
    val scale = MapConstants.RADIUS_NORMALIZATION_SCALE
    return (totalRadius * scale).roundToInt() / scale
}
