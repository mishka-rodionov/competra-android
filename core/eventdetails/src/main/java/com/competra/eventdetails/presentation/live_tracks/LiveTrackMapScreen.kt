package com.competra.eventdetails.presentation.live_tracks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.preference.PreferenceManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleStartEffect
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.competra.domain.models.livetrack.LiveTrackStatus
import com.competra.domain.models.livetrack.SPEED_COLOR_STEPS
import com.competra.domain.models.livetrack.TrackSpeedProfile
import com.competra.domain.models.livetrack.ViewerTrack
import com.competra.domain.models.orienteering.ControlPointRole
import com.competra.domain.models.orienteering.DistanceMap
import com.competra.utils.orienteering.toPace
import kotlinx.coroutines.flow.SharedFlow
import org.koin.androidx.compose.koinViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.GroundOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.util.concurrent.TimeUnit

/** Растр карты дистанции ужимается до этого размера по большей стороне — память телефона. */
private const val MAX_RASTER_PX = 4096

/** Цвета участников: контрастные на топокарте и не похожие на пурпурные КП. */
private val TRACK_COLORS = intArrayOf(
    0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFB8C00.toInt(), 0xFF00ACC1.toInt(),
    0xFF6D4C41.toInt(), 0xFF3949AB.toInt(), 0xFF7CB342.toInt(), 0xFF00897B.toInt(), 0xFFF4511E.toInt()
)
private const val STALE_COLOR = 0xFF9E9E9E.toInt()
private const val CONTROL_POINT_COLOR = 0xFFC000C0.toInt()

/** Подложка под линией скорости: жёлтый и светло-зелёный иначе теряются на топокарте. */
private const val SPEED_CASING_COLOR = 0x99000000.toInt()

/** Медленнее этого (м/с) темп не пишем — участник стоит. */
private const val STANDING_SPEED = 0.2

private fun trackColor(state: LiveTrackMapState, track: ViewerTrack): Int =
    TRACK_COLORS[(state.colorIndex[track.sessionId] ?: 0) % TRACK_COLORS.size]

/** Цвет ступени скорости: от красного (0) через жёлтый к зелёному ([SPEED_COLOR_STEPS] − 1). */
private fun speedColor(level: Int): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(120f * level / (SPEED_COLOR_STEPS - 1), 0.9f, 0.9f))

/**
 * Карта онлайн-треков дистанции для зрителя: растр карты по трём углам поверх OSM, КП, треки
 * участников (разрывы дольше 30 с не соединяются), маркеры с номером (серые — нет данных больше
 * минуты), фильтр по группам и «хвост» за 5 минут. После финиша всех — архив. Завершённые треки
 * можно раскрасить по скорости участника (красный — медленно, зелёный — быстро).
 *
 * @param eventId Идентификатор соревнования.
 * @param distanceId Серверный идентификатор дистанции.
 */
@Composable
fun LiveTrackMapScreen(
    eventId: String,
    distanceId: Long,
    viewModel: LiveTrackMapViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LifecycleStartEffect(eventId, distanceId) {
        viewModel.start(eventId, distanceId)
        onStopOrDispose { viewModel.stop() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MapHeader(state)
            MapFilters(state) { viewModel.onAction(it) }
            // osmdroid рисует тайлы и треки за пределами своего View — без обрезки они наезжают на список.
            Box(modifier = Modifier.fillMaxWidth().weight(0.62f).clipToBounds()) {
                LiveTrackOsmMap(state = state, focus = viewModel.focus, modifier = Modifier.fillMaxSize())
                if (state.isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                if (state.speedMode && state.canShowSpeed) {
                    SpeedLegend(state, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
            }
            HorizontalDivider()
            ParticipantList(state = state, modifier = Modifier.weight(0.38f)) { viewModel.onAction(it) }
        }
    }
}

@Composable
private fun MapHeader(state: LiveTrackMapState) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(state.distanceName.ifBlank { "Онлайн-треки" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        val active = state.tracks.count { it.isActive }
        Text(
            when {
                state.isLoading -> "Загрузка…"
                active > 0 -> "На дистанции: $active • треков: ${state.tracks.size}"
                state.tracks.isEmpty() -> "Пока нет треков"
                else -> "Архив треков: ${state.tracks.size}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.isConnectionLost) {
            Text(
                "Нет связи с сервером треков — показаны последние данные",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun MapFilters(state: LiveTrackMapState, onAction: (LiveTrackMapAction) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        item {
            FilterChip(
                selected = state.tailOnly,
                onClick = { onAction(LiveTrackMapAction.ToggleTail) },
                label = { Text("Хвост 5 мин") }
            )
        }
        if (state.canShowSpeed || state.speedMode) {
            item {
                FilterChip(
                    selected = state.speedMode,
                    onClick = { onAction(LiveTrackMapAction.ToggleSpeedMode) },
                    label = { Text("Скорость") }
                )
            }
        }
        items(state.groups) { group ->
            FilterChip(
                selected = group in state.selectedGroups,
                onClick = { onAction(LiveTrackMapAction.ToggleGroup(group)) },
                label = { Text(group) }
            )
        }
    }
}

@Composable
private fun ParticipantList(state: LiveTrackMapState, modifier: Modifier, onAction: (LiveTrackMapAction) -> Unit) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(state.visibleTracks, key = { it.sessionId }) { track ->
            val stale = track.isStale(state.serverTime)
            val selected = state.speedSelection?.sessionId == track.sessionId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onAction(LiveTrackMapAction.FocusTrack(track.sessionId)) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(Color(if (stale) STALE_COLOR else trackColor(state, track)), CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    val number = track.startNumber?.let { "№$it " }.orEmpty()
                    Text("$number${track.displayName}", style = MaterialTheme.typography.bodyLarge)
                    track.groupName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    statusText(track, state.serverTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (track.isActive && !stale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Шкала скорости поверх карты. Шкала у каждого участника своя, поэтому темп на концах подписан,
 * только когда на карте один раскрашенный трек.
 */
@Composable
private fun SpeedLegend(state: LiveTrackMapState, modifier: Modifier) {
    val profile: TrackSpeedProfile? = state.mapTracks.mapNotNull { state.speedProfileOf(it) }.singleOrNull()
    val gradient = remember { (0 until SPEED_COLOR_STEPS).map { Color(speedColor(it)) } }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Box(
                modifier = Modifier
                    .width(168.dp)
                    .height(8.dp)
                    .background(Brush.horizontalGradient(gradient), RoundedCornerShape(4.dp))
            )
            Row(modifier = Modifier.width(168.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(profile?.let { paceText(it.slowSpeed) } ?: "медленнее", style = MaterialTheme.typography.labelSmall)
                Text(profile?.let { paceText(it.fastSpeed) } ?: "быстрее", style = MaterialTheme.typography.labelSmall)
            }
            if (state.speedSelection == null && profile == null) {
                Text(
                    "Нажмите на участника — только его трек",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Темп «М:СС /км» по скорости в м/с. */
private fun paceText(speed: Double): String =
    if (speed < STANDING_SPEED) "стоит" else "${(1000 / speed / 60).toPace()} /км"

private fun statusText(track: ViewerTrack, serverTime: Long): String = when (track.status) {
    LiveTrackStatus.ACTIVE -> if (track.isStale(serverTime)) {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(serverTime - (track.lastPointAt ?: track.startedAt)).coerceAtLeast(1)
        "нет данных $minutes мин"
    } else {
        "на дистанции"
    }
    LiveTrackStatus.FINISHED -> "финиш"
    LiveTrackStatus.STOPPED -> "трек остановлен"
    LiveTrackStatus.TIMED_OUT -> "трек закрыт"
}

/** Наши слои на карте — чтобы при обновлении заменять только их. */
private class OverlayHolder {
    var ground: GroundOverlay? = null
    var groundBitmap: Bitmap? = null
    val dynamic = mutableListOf<Overlay>()
    var initialZoomDone = false
}

@Composable
private fun LiveTrackOsmMap(state: LiveTrackMapState, focus: SharedFlow<TrackFocus>, modifier: Modifier) {
    val context = LocalContext.current
    val raster by produceState<Bitmap?>(initialValue = null, state.map?.url) {
        value = state.map?.url?.let { loadRaster(context, it) }
    }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(focus) {
        focus.collect { (track, wholeTrack) ->
            val mapView = mapViewRef.value ?: return@collect
            val last = track.points.lastOrNull() ?: return@collect
            if (wholeTrack && track.points.size > 1) {
                mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(track.points.map { GeoPoint(it.lat, it.lon) }), true, 48)
            } else {
                mapView.controller.animateTo(GeoPoint(last.lat, last.lon))
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // load() сам выставляет User-Agent = имя пакета — OSM требует, чтобы приложение представлялось.
            Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                overlays.add(CopyrightOverlay(ctx))
                tag = OverlayHolder()
                mapViewRef.value = this
            }
        },
        update = { mapView -> render(mapView, state, raster) },
        onRelease = { mapView ->
            mapViewRef.value = null
            mapView.onDetach()
        }
    )
}

/** Перерисовывает наши слои: растр (только если сменился), КП, треки и маркеры. */
private fun render(mapView: MapView, state: LiveTrackMapState, raster: Bitmap?) {
    val holder = mapView.tag as OverlayHolder
    val corners = state.map?.corners()

    if (raster !== holder.groundBitmap) {
        holder.ground?.let { mapView.overlays.remove(it) }
        holder.ground = null
        holder.groundBitmap = raster
        if (raster != null && corners != null) {
            holder.ground = GroundOverlay().apply {
                image = raster
                setPosition(
                    GeoPoint(corners.topLeft.latitude, corners.topLeft.longitude),
                    GeoPoint(corners.topRight.latitude, corners.topRight.longitude),
                    GeoPoint(corners.bottomRight.latitude, corners.bottomRight.longitude),
                    GeoPoint(corners.bottomLeft.latitude, corners.bottomLeft.longitude)
                )
            }.also { mapView.overlays.add(0, it) }
        }
    }

    mapView.overlays.removeAll(holder.dynamic)
    holder.dynamic.clear()
    holder.dynamic += controlPointOverlays(mapView, state)
    state.mapTracks.forEach { track -> holder.dynamic += trackOverlays(mapView, state, track) }
    mapView.overlays.addAll(holder.dynamic)

    if (!holder.initialZoomDone) {
        initialBounds(state)?.let { bounds ->
            holder.initialZoomDone = true
            mapView.post { mapView.zoomToBoundingBox(bounds, false, 48) }
        }
    }
    mapView.invalidate()
}

private fun controlPointOverlays(mapView: MapView, state: LiveTrackMapState): List<Overlay> =
    state.controlPoints.flatMap { cp ->
        val center = GeoPoint(cp.latitude ?: return@flatMap emptyList(), cp.longitude ?: return@flatMap emptyList())
        // Финиш — двойная окружность, как на спортивной карте.
        val radii = if (cp.role == ControlPointRole.FINISH) listOf(22.0, 32.0) else listOf(30.0)
        radii.map { radius ->
            Polygon(mapView).apply {
                points = Polygon.pointsAsCircle(center, radius)
                fillPaint.color = android.graphics.Color.TRANSPARENT
                outlinePaint.color = CONTROL_POINT_COLOR
                outlinePaint.strokeWidth = 5f
                infoWindow = null
            }
        }
    }

private fun trackOverlays(mapView: MapView, state: LiveTrackMapState, track: ViewerTrack): List<Overlay> {
    val color = trackColor(state, track)
    val since = if (state.tailOnly) (track.lastPointAt ?: state.serverTime) - TAIL_WINDOW_MS else null
    val profile = state.speedProfileOf(track)
    val lines = if (profile != null) {
        speedLines(mapView, track, profile, since)
    } else {
        track.segments(since = since).filter { it.size > 1 }.map { segment -> trackLine(mapView, segment.map { GeoPoint(it.lat, it.lon) }, color, 7f) }
    }
    val last = track.points.lastOrNull() ?: return lines
    val markerColor = if (track.isStale(state.serverTime)) STALE_COLOR else color
    val marker = Marker(mapView).apply {
        position = GeoPoint(last.lat, last.lon)
        icon = markerIcon(mapView.context, markerColor, track.startNumber?.toString() ?: track.displayName.take(1), faded = !track.isActive)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        title = track.displayName
        snippet = listOfNotNull(track.groupName, statusText(track, state.serverTime)).joinToString(" • ")
    }
    return lines + marker
}

/** Трек по скорости: тёмная подложка по отрезкам трека и поверх неё — куски цвета своей ступени. */
private fun speedLines(mapView: MapView, track: ViewerTrack, profile: TrackSpeedProfile, since: Long?): List<Overlay> {
    val casing = track.segments(since = since).filter { it.size > 1 }
        .map { segment -> trackLine(mapView, segment.map { GeoPoint(it.lat, it.lon) }, SPEED_CASING_COLOR, 11f) }
    val chunks = profile.chunks.mapNotNull { chunk ->
        val points = if (since == null) chunk.points else chunk.points.filter { it.t >= since }
        if (points.size < 2) null else trackLine(mapView, points.map { GeoPoint(it.lat, it.lon) }, speedColor(chunk.level), 7f)
    }
    return casing + chunks
}

private fun trackLine(mapView: MapView, points: List<GeoPoint>, color: Int, width: Float): Polyline =
    Polyline(mapView).apply {
        setPoints(points)
        outlinePaint.color = color
        outlinePaint.strokeWidth = width
        outlinePaint.strokeCap = Paint.Cap.ROUND
        infoWindow = null
    }

/** Кружок цвета участника с его номером. */
private fun markerIcon(context: Context, color: Int, label: String, faded: Boolean): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (30 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = if (faded) 150 else 255 }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
    }
    val radius = size / 2f - density
    canvas.drawCircle(size / 2f, size / 2f, radius, fill)
    canvas.drawCircle(size / 2f, size / 2f, radius, stroke)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = (if (label.length > 2) 10 else 12) * density
    }
    canvas.drawText(label, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2, text)
    return BitmapDrawable(context.resources, bitmap)
}

/** Начальный кадр: карта дистанции, иначе КП, иначе треки. */
private fun initialBounds(state: LiveTrackMapState): BoundingBox? {
    val map: DistanceMap? = state.map
    val points: List<GeoPoint> = when {
        map != null -> map.corners().let { c ->
            listOf(c.topLeft, c.topRight, c.bottomRight, c.bottomLeft).map { GeoPoint(it.latitude, it.longitude) }
        }
        state.controlPoints.isNotEmpty() -> state.controlPoints.mapNotNull { cp ->
            cp.latitude?.let { lat -> cp.longitude?.let { lon -> GeoPoint(lat, lon) } }
        }
        else -> state.tracks.flatMap { t -> t.points.map { GeoPoint(it.lat, it.lon) } }
    }
    return if (points.size < 2) null else BoundingBox.fromGeoPoints(points)
}

/** Растр карты дистанции, ужатый до [MAX_RASTER_PX]; `null` — не удалось загрузить. */
private suspend fun loadRaster(context: Context, url: String): Bitmap? = runCatching {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(MAX_RASTER_PX)
        .allowHardware(false)
        .build()
    (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
}.getOrNull()
