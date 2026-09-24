package com.competra.center.presentation.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.preference.PreferenceManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.competra.resources.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay

/**
 * Спутниковые тайлы Esri с глобальным покрытием.
 * Esri использует порядок координат z/y/x вместо стандартного z/x/y,
 * поэтому переопределяем построение URL.
 */
private val ESRI_SATELLITE = object : XYTileSource(
    "Esri Satellite",
    0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex)
}

/**
 * Топографические тайлы OpenTopoMap с глобальным покрытием.
 */
private val OPEN_TOPO = XYTileSource(
    "OpenTopoMap",
    0, 17, 256, ".png",
    arrayOf(
        "https://a.tile.opentopomap.org/",
        "https://b.tile.opentopomap.org/",
        "https://c.tile.opentopomap.org/"
    )
)

/**
 * Доступные типы карт для отображения на [MapPickerScreen].
 *
 * @param label Название, отображаемое пользователю.
 * @param tileSource Источник тайлов osmdroid.
 */
private enum class MapType(val label: String, val tileSource: ITileSource) {
    OSM("OpenStreetMap", TileSourceFactory.MAPNIK),
    SATELLITE("Спутник", ESRI_SATELLITE),
    TOPO("Топография", OPEN_TOPO),
    TRANSPORT("Транспорт", TileSourceFactory.PUBLIC_TRANSPORT),
}

/**
 * Экран выбора координат места старта на карте OpenStreetMap.
 *
 * Пользователь перемещает карту так, чтобы перекрестие оказалось на нужной точке,
 * затем нажимает "Зафиксировать". Выбранные координаты передаются через [onConfirm].
 *
 * Координаты округляются до 5 знаков после запятой.
 *
 * @param initLat Начальная широта для позиционирования карты (0.0 — не задана).
 * @param initLon Начальная долгота для позиционирования карты (0.0 — не задана).
 * @param onConfirm Вызывается при нажатии "Зафиксировать" с координатами центра карты.
 */
@Composable
fun MapPickerScreen(
    initLat: Double = 0.0,
    initLon: Double = 0.0,
    onConfirm: (lat: Double, lon: Double) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    var currentMapType by remember { mutableStateOf(MapType.OSM) }
    var layerMenuExpanded by remember { mutableStateOf(false) }

    val hasStoredCoords = initLat != 0.0 || initLon != 0.0

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    Configuration.getInstance().load(
                        ctx,
                        PreferenceManager.getDefaultSharedPreferences(ctx)
                    )
                    MapView(ctx).also { mapView ->
                        mapViewRef.value = mapView
                        mapView.setTileSource(currentMapType.tileSource)
                        mapView.setMultiTouchControls(true)
                        // Атрибуция источника тайлов обязательна (OSM Tile Usage Policy); подпись
                        // берётся у текущего tileSource, поэтому меняется при переключении слоя.
                        mapView.overlays.add(CopyrightOverlay(ctx))
                        mapView.controller.setZoom(15.0)

                        val center = when {
                            hasStoredCoords -> GeoPoint(initLat, initLon)
                            else -> resolveCurrentLocation(ctx)
                        }
                        mapView.controller.setCenter(center)
                    }
                },
                update = { mapView ->
                    mapView.setTileSource(currentMapType.tileSource)
                }
            )

            // Перекрестие в центре карты
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val arm = size.minDimension / 2f
                val strokeWidth = 3.dp.toPx()
                drawLine(
                    color = Color.Red,
                    start = Offset(cx - arm, cy),
                    end = Offset(cx + arm, cy),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.Red,
                    start = Offset(cx, cy - arm),
                    end = Offset(cx, cy + arm),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            // Переключатель типа карты
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                FilledIconButton(onClick = { layerMenuExpanded = true }) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.map_24dp),
                        contentDescription = "Тип карты"
                    )
                }
                DropdownMenu(
                    expanded = layerMenuExpanded,
                    onDismissRequest = { layerMenuExpanded = false }
                ) {
                    MapType.entries.forEach { mapType ->
                        DropdownMenuItem(
                            text = { Text(mapType.label) },
                            onClick = {
                                currentMapType = mapType
                                layerMenuExpanded = false
                            },
                            trailingIcon = {
                                if (mapType == currentMapType) {
                                    RadioButton(selected = true, onClick = null)
                                }
                            }
                        )
                    }
                }
            }

            // Кнопка фиксации координат
            Button(
                onClick = {
                    val mapView = mapViewRef.value ?: return@Button
                    val center = mapView.mapCenter
                    val factor = 100_000.0
                    val lat = kotlin.math.round(center.latitude * factor) / factor
                    val lon = kotlin.math.round(center.longitude * factor) / factor
                    onConfirm(lat, lon)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .fillMaxWidth()
            ) {
                Text("Зафиксировать")
            }
        }
    }

    // Управление lifecycle MapView: onResume / onPause / onDetach
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef.value?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef.value?.onDetach()
        }
    }
}

/**
 * Возвращает [GeoPoint] с текущим местоположением устройства.
 * Если разрешение не выдано или местоположение недоступно — возвращает точку по умолчанию (Москва).
 */
private fun resolveCurrentLocation(context: Context): GeoPoint {
    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) return DEFAULT_LOCATION

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

    return location?.let { GeoPoint(it.latitude, it.longitude) } ?: DEFAULT_LOCATION
}

/** Координаты по умолчанию (Москва) на случай, если геолокация недоступна. */
private val DEFAULT_LOCATION = GeoPoint(55.75222, 37.61556)
