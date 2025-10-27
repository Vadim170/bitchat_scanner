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
 * Компонент для отображения мини-карты с локациями устройства
 * 
 * @param locations Список локаций для отображения
 * @param modifier Модификатор для настройки размера и стиля
 * @param isVisible Флаг видимости карты (для оптимизации)
 * @param isLoading Флаг загрузки данных
 * @param hasError Флаг ошибки загрузки
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

                        // Отключаем интерактивность для мини-карты
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
 * Обновляет содержимое карты с учётом кэширования
 * Карта перерисовывается только если изменились координаты или уменьшилась минимальная сумма радиусов
 */
private fun updateMapContent(mapView: MapView, locations: List<LocationPoint>) {
    // Сравниваем с предыдущим состоянием
    val oldData = mapView.tag as? MapData
    val newData = calculateMapData(locations)
    
    // Проверяем, нужна ли перерисовка
    if (oldData != null && !shouldRedraw(oldData, newData)) {
        return // Изменений нет или радиус не уменьшился
    }

    mapView.tag = newData
    mapView.overlays.clear()

    if (locations.isEmpty()) {
        mapView.invalidate()
        return
    }

    val filteredLocations = MapUtils.filterLocationsByMinRadius(locations)
    val controller = mapView.controller
    
    // Устанавливаем центр и зум
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

    // Добавляем круги на карту
    filteredLocations.forEach { location ->
        val circle = MapUtils.createDetectionCircle(location, MapConstants.CIRCLE_POINTS_COUNT_SIMPLE)
        mapView.overlays.add(circle)
    }

    mapView.invalidate()
}

/**
 * Данные для кэширования состояния карты
 */
private data class MapData(
    val coordsHash: Int,  // Хеш координат
    val minRadiusByCoords: Map<String, Double>  // Минимальная сумма радиусов для каждой координаты
)

/**
 * Вычисляет данные карты: хеш координат и минимальные суммы радиусов
 */
private fun calculateMapData(locations: List<LocationPoint>): MapData {
    if (locations.isEmpty()) {
        return MapData(0, emptyMap())
    }
    
    // Группируем по координатам
    val groupedByCoords = locations.groupBy { "${it.lat},${it.lon}" }
    
    // Хешируем только координаты (не радиусы)
    val coordsHash = groupedByCoords.keys.sorted().hashCode()
    
    // Для каждой координаты находим минимальную сумму радиусов
    val minRadiusByCoords = groupedByCoords.mapValues { (_, points) ->
        points.minOf { point ->
            val rssiRadius = com.vadim170.bitchatscanner.utils.RssiUtils.calculateRadiusFromRSSI(point.rssi)
            val gpsAccuracy = point.accuracy?.toDouble() ?: 10.0
            normalizeRadius(rssiRadius + gpsAccuracy)  // СУММА радиусов с нормализацией
        }
    }
    
    return MapData(coordsHash, minRadiusByCoords)
}

/**
 * Проверяет, нужна ли перерисовка карты
 * Возвращает true если:
 * - Изменились координаты (появились новые или исчезли старые)
 * - Для любой координаты минимальная сумма радиусов уменьшилась
 */
private fun shouldRedraw(oldData: MapData, newData: MapData): Boolean {
    // Если изменились координаты - перерисовываем
    if (oldData.coordsHash != newData.coordsHash) {
        return true
    }
    
    // Проверяем, уменьшилась ли минимальная сумма для каких-либо координат
    for ((coords, newMinRadius) in newData.minRadiusByCoords) {
        val oldMinRadius = oldData.minRadiusByCoords[coords] ?: Double.MAX_VALUE
        
        // Если сумма уменьшилась - нужна перерисовка
        if (newMinRadius + MapConstants.MIN_RADIUS_IMPROVEMENT_METERS <= oldMinRadius) {
            return true
        }
    }
    
    return false
}

/**
 * Нормализует радиус с шагом 0.1 м, чтобы избежать шумовых перерисовок
 */
private fun normalizeRadius(radius: Double): Double {
    val scale = MapConstants.RADIUS_NORMALIZATION_SCALE
    return (radius * scale).roundToInt() / scale
}
