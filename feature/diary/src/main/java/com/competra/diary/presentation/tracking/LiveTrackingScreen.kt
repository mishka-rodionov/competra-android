package com.competra.diary.presentation.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.preference.PreferenceManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.competra.designsystem.theme.Dimens
import com.competra.diary.data.tracking.LiveTrackingAction
import com.competra.diary.data.tracking.LiveTrackingState
import com.competra.domain.models.diary.SportType
import com.competra.resources.R
import com.competra.ui.WorkoutTrackingStatus
import org.koin.androidx.compose.koinViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun LiveTrackingScreen(
    sportType: SportType,
    viewModel: LiveTrackingViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(sportType) {
        viewModel.start(sportType)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        LiveTrackingContent(state, viewModel::onAction)
    }

    if (state.isStopDialogVisible) {
        StopTrackingDialog(
            onDismiss = { viewModel.onAction(LiveTrackingAction.CancelStopDialogClick) },
            onSave = { viewModel.onAction(LiveTrackingAction.ConfirmStopClick) },
            onDiscard = { viewModel.onAction(LiveTrackingAction.DiscardClick) }
        )
    }
}

@Composable
private fun LiveTrackingContent(state: LiveTrackingState, onAction: (LiveTrackingAction) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val locationMarkerRef = remember { mutableStateOf<Marker?>(null) }
    val polyline = remember { Polyline() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
                MapView(ctx).also { mapView ->
                    mapViewRef.value = mapView
                    mapView.setTileSource(TileSourceFactory.MAPNIK)
                    mapView.setMultiTouchControls(true)
                    // Атрибуция OSM обязательна по правилам использования тайлов (Tile Usage Policy).
                    mapView.overlays.add(CopyrightOverlay(ctx))
                    mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    mapView.overlays.add(polyline)

                    val marker = Marker(mapView).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Вы здесь"
                    }
                    mapView.overlays.add(marker)
                    locationMarkerRef.value = marker

                    // Позиционируем карту и маркер по текущему местоположению сразу при
                    // открытии экрана — не дожидаясь первого GPS-фикса от сервиса трекинга.
                    val initialCenter = resolveCurrentLocation(ctx)
                    marker.position = initialCenter
                    mapView.controller.setZoom(17.0)
                    mapView.controller.setCenter(initialCenter)
                }
            }
        )

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(Dimens.SIZE_BASE.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SIZE_BASE.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn(label = "Время", value = formatElapsed(state.elapsedSeconds))
                StatColumn(label = "Дистанция", value = "%.2f км".format(state.distanceMeters / 1000.0))
                StatColumn(label = "Скорость", value = "%.1f км/ч".format(state.speedMetersPerSecond * 3.6))
                StatColumn(label = "Набор", value = "${state.elevationGainMeters} м")
            }
        }

        FloatingActionButton(
            onClick = {
                val target = state.points.lastOrNull()?.let { GeoPoint(it.lat, it.lon) }
                    ?: resolveCurrentLocation(context)
                mapViewRef.value?.controller?.animateTo(target)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = Dimens.SIZE_BASE.dp)
                .padding(bottom = 88.dp)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_location_on_24px),
                contentDescription = "Моё местоположение"
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Dimens.SIZE_BASE.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
        ) {
            if (state.status == WorkoutTrackingStatus.PAUSED) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    onClick = { onAction(LiveTrackingAction.ResumeClick) }
                ) { Text("Продолжить") }
            } else {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    onClick = { onAction(LiveTrackingAction.PauseClick) }
                ) { Text("Пауза") }
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                onClick = { onAction(LiveTrackingAction.StopClick) }
            ) { Text("Стоп") }
        }
    }

    LaunchedEffect(state.points.size) {
        val geoPoints = state.points.map { GeoPoint(it.lat, it.lon) }
        polyline.setPoints(geoPoints)
        geoPoints.lastOrNull()?.let { last ->
            locationMarkerRef.value?.position = last
            mapViewRef.value?.controller?.animateTo(last)
        }
        mapViewRef.value?.invalidate()
    }

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

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * Текущее местоположение устройства для позиционирования карты (по образцу
 * `resolveCurrentLocation` из `feature/center`'s `MapPickerScreen.kt`). Разрешение на геолокацию
 * уже запрошено при старте приложения (`AppRoot.RequestInitialPermissions`) — здесь только
 * проверка на случай, если пользователь его отклонил.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopTrackingDialog(onDismiss: () -> Unit, onSave: () -> Unit, onDiscard: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(Dimens.SIZE_BASE.dp)
                .fillMaxWidth()
        ) {
            Text(text = "Завершить тренировку?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                onClick = onSave
            ) { Text("Сохранить") }
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                onClick = onDiscard
            ) { Text("Отменить тренировку") }
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                onClick = onDismiss
            ) { Text("Продолжить тренировку") }
            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
        }
    }
}
