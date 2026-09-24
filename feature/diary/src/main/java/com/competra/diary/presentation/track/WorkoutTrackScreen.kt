package com.competra.diary.presentation.track

import android.preference.PreferenceManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.competra.designsystem.theme.Dimens
import com.competra.diary.data.track.WorkoutTrackAction
import com.competra.diary.presentation.common.formatWorkoutDuration
import com.competra.domain.diary.TrackCodec
import com.competra.resources.R
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Polyline
import java.io.File

@Composable
fun WorkoutTrackScreen(
    workoutId: Long,
    viewModel: WorkoutTrackViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(workoutId) {
        viewModel.load(workoutId)
    }

    LaunchedEffect(Unit) {
        viewModel.exportGpxEvent.collectLatest { (fileName, content) ->
            val file = File(context.cacheDir, fileName)
            file.writeText(content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = ShareCompat.IntentBuilder(context)
                .setType("application/gpx+xml")
                .addStream(uri)
                .setChooserTitle("Экспорт трека")
                .createChooserIntent()
            context.startActivity(intent)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            val workout = state.workout
            val encoded = workout?.workout?.trackEncoded

            if (workout != null && encoded != null) {
                FullTrackMap(startedAtMs = workout.workout.startedAt ?: 0L, trackEncoded = encoded)

                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(start = 72.dp, end = Dimens.SIZE_BASE.dp, top = Dimens.SIZE_BASE.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.SIZE_BASE.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        workout.workout.distanceMeters?.let {
                            StatColumn(label = "Дистанция", value = "%.2f км".format(it / 1000.0))
                        }
                        workout.workout.durationSeconds?.let {
                            StatColumn(label = "Время", value = formatWorkoutDuration(it))
                        }
                        workout.workout.elevationGainMeters?.let {
                            StatColumn(label = "Набор", value = "$it м")
                        }
                    }
                }
            }

            FilledIconButton(
                onClick = { viewModel.onAction(WorkoutTrackAction.BackClick) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Dimens.SIZE_BASE.dp)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back_24px),
                    contentDescription = null
                )
            }

            if (encoded != null) {
                FilledIconButton(
                    onClick = { viewModel.onAction(WorkoutTrackAction.ExportGpxClick) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = Dimens.SIZE_BASE.dp)
                        .padding(top = 100.dp)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_share_24px),
                        contentDescription = "Экспортировать GPX"
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Полноэкранная неинтерактивная карта — декодирует [TrackCodec] и рисует весь маршрут. */
@Composable
private fun FullTrackMap(startedAtMs: Long, trackEncoded: String) {
    val points = remember(trackEncoded) { TrackCodec.decode(startedAtMs, trackEncoded) }
    if (points.isEmpty()) return
    val geoPoints = remember(points) { points.map { GeoPoint(it.lat, it.lon) } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                // Атрибуция OSM обязательна по правилам использования тайлов (Tile Usage Policy).
                overlays.add(CopyrightOverlay(ctx))
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                overlays.add(Polyline().apply { setPoints(geoPoints) })
                post {
                    zoomToBoundingBox(BoundingBox.fromGeoPoints(geoPoints), false, 64)
                }
            }
        },
        onRelease = { mapView -> mapView.onDetach() }
    )
}
