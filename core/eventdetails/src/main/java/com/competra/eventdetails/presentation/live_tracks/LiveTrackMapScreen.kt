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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.competra.domain.models.livetrack.LiveTrackStatus
import com.competra.domain.models.livetrack.REPLAY_TAIL_MS
import com.competra.domain.models.livetrack.ReplayRunnerState
import com.competra.domain.models.livetrack.ReplayTimeMode
import com.competra.domain.models.livetrack.ReplayTrack
import com.competra.domain.models.livetrack.SPEED_COLOR_STEPS
import com.competra.domain.models.livetrack.TrackSpeedProfile
import com.competra.domain.models.livetrack.ViewerTrack
import com.competra.domain.models.livetrack.ViewerTrackPoint
import com.competra.domain.models.orienteering.ControlPointRole
import com.competra.domain.models.orienteering.DistanceMap
import com.competra.resources.R
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

/** Прозрачность полного трека за хвостом при просмотре (0–255). */
private const val FAINT_TRACK_ALPHA = 0x66

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun trackColor(state: LiveTrackMapState, track: ViewerTrack): Int =
    TRACK_COLORS[(state.colorIndex[track.sessionId] ?: 0) % TRACK_COLORS.size]

/** Цвет ступени скорости: от красного (0) через жёлтый к зелёному ([SPEED_COLOR_STEPS] − 1). */
private fun speedColor(level: Int): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(120f * level / (SPEED_COLOR_STEPS - 1), 0.9f, 0.9f))

/**
 * Карта онлайн-треков дистанции для зрителя: растр карты по трём углам поверх OSM, КП, треки
 * участников (разрывы дольше 30 с не соединяются), маркеры с номером (серые — нет данных больше
 * минуты), фильтр по группам и «хвост» за 5 минут. После финиша всех — архив. Завершённые треки
 * можно раскрасить по скорости участника (красный — медленно, зелёный — быстро) и просмотреть
 * ползунком: маркеры движутся по трекам от старта до финиша — по общим часам или с общего старта.
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

    val onAction: (LiveTrackMapAction) -> Unit = { viewModel.onAction(it) }

    // Нижняя навигация на этом экране спрятана (MainScreen), поэтому системные панели и вырез экрана отступаем сами.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        ) {
            if (maxWidth > maxHeight) {
                LandscapeLayout(state, viewModel.focus, sidePanelWidth = (maxWidth * 0.4f).coerceIn(280.dp, 400.dp), onAction)
            } else {
                PortraitLayout(state, viewModel.focus, onAction)
            }
        }
    }
}

/** Вертикально: шапка, фильтры, карта, панель просмотра и список участников под картой. */
@Composable
private fun PortraitLayout(state: LiveTrackMapState, focus: SharedFlow<TrackFocus>, onAction: (LiveTrackMapAction) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        MapHeader(state, onAction)
        MapFilters(state, onAction)
        MapArea(state, focus, modifier = Modifier.fillMaxWidth().weight(0.62f))
        val range = state.replayRange
        if (state.replay && range != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                ReplayControls(state, range, onAction)
                ReplayOptions(state, onAction)
            }
        }
        HorizontalDivider()
        ParticipantList(state = state, modifier = Modifier.weight(0.38f), onAction = onAction)
    }
}

/**
 * Горизонтально карта занимает всю высоту справа, а шапка, фильтры, настройки просмотра и список
 * участников — в панели слева. Ползунок просмотра — под картой, чтобы был длинным.
 */
@Composable
private fun LandscapeLayout(
    state: LiveTrackMapState,
    focus: SharedFlow<TrackFocus>,
    sidePanelWidth: Dp,
    onAction: (LiveTrackMapAction) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.width(sidePanelWidth).fillMaxHeight()) {
            MapHeader(state, onAction)
            MapFilters(state, onAction)
            if (state.replay && state.replayRange != null) {
                ReplayOptions(state, onAction, modifier = Modifier.padding(top = 4.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            ParticipantList(state = state, modifier = Modifier.weight(1f), onAction = onAction)
        }
        VerticalDivider()
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            MapArea(state, focus, modifier = Modifier.fillMaxWidth().weight(1f))
            val range = state.replayRange
            if (state.replay && range != null) ReplayControls(state, range, onAction)
        }
    }
}

/** Карта с индикатором загрузки и шкалой скорости. */
@Composable
private fun MapArea(state: LiveTrackMapState, focus: SharedFlow<TrackFocus>, modifier: Modifier) {
    // osmdroid рисует тайлы и треки за пределами своего View — без обрезки они наезжают на соседние элементы.
    Box(modifier = modifier.clipToBounds()) {
        LiveTrackOsmMap(state = state, focus = focus, modifier = Modifier.fillMaxSize())
        if (state.isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        if (state.speedMode && state.canShowSpeed) {
            SpeedLegend(state, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
    }
}

/** Шапка со стрелкой «назад»: название дистанции и сводка по трекам. */
@Composable
private fun MapHeader(state: LiveTrackMapState, onAction: (LiveTrackMapAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onAction(LiveTrackMapAction.Back) }) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back_24px), contentDescription = "Назад")
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                state.distanceName.ifBlank { "Онлайн-треки" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
}

@Composable
private fun MapFilters(state: LiveTrackMapState, onAction: (LiveTrackMapAction) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        if (!state.replay) {
            item {
                FilterChip(
                    selected = state.tailOnly,
                    onClick = { onAction(LiveTrackMapAction.ToggleTail) },
                    label = { Text("Хвост 5 мин") }
                )
            }
        }
        if (state.canReplay || state.replay) {
            item {
                FilterChip(
                    selected = state.replay,
                    onClick = { onAction(LiveTrackMapAction.ToggleReplay) },
                    label = { Text("Просмотр") }
                )
            }
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
    val tracks = if (state.replay) state.visibleTracks.filter { it.sessionId in state.replayTracks } else state.visibleTracks
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(tracks, key = { it.sessionId }) { track ->
            val stale = track.isStale(state.serverTime)
            val selected = state.speedSelection?.sessionId == track.sessionId
            val replayTrack = state.replayTracks[track.sessionId]?.takeIf { state.replay }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onAction(LiveTrackMapAction.FocusTrack(track.sessionId)) }
                    .padding(start = if (replayTrack != null) 4.dp else 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (replayTrack != null) {
                    Checkbox(
                        checked = track.sessionId in state.checkedSessionIds,
                        onCheckedChange = { onAction(LiveTrackMapAction.ToggleChecked(track.sessionId)) }
                    )
                }
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
                    replayTrack?.let { replayStatusText(state, it) } ?: statusText(track, state.serverTime),
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

/** Управление просмотром: ▶/пауза, ползунок и время. */
@Composable
private fun ReplayControls(state: LiveTrackMapState, range: LongRange, onAction: (LiveTrackMapAction) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onAction(LiveTrackMapAction.TogglePlay) }) {
            Icon(
                painter = painterResource(if (state.isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px),
                contentDescription = if (state.isPlaying) "Пауза" else "Воспроизвести"
            )
        }
        // Ползунок — смещение от начала диапазона: Unix ms во Float теряет точность.
        Slider(
            value = (state.replayClampedPosition - range.first).toFloat(),
            onValueChange = { onAction(LiveTrackMapAction.SeekReplay(range.first + it.toLong())) },
            valueRange = 0f..(range.last - range.first).coerceAtLeast(1).toFloat(),
            modifier = Modifier.weight(1f)
        )
        Text(
            replayTimeText(state),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp)
        )
    }
}

/** Настройки просмотра: шкала времени и скорость воспроизведения. */
@Composable
private fun ReplayOptions(state: LiveTrackMapState, onAction: (LiveTrackMapAction) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        item {
            FilterChip(
                selected = state.replayMode == ReplayTimeMode.MASS_START,
                onClick = { onAction(LiveTrackMapAction.SetReplayMode(ReplayTimeMode.MASS_START)) },
                label = { Text("Общий старт") }
            )
        }
        item {
            FilterChip(
                selected = state.replayMode == ReplayTimeMode.REAL_TIME,
                onClick = { onAction(LiveTrackMapAction.SetReplayMode(ReplayTimeMode.REAL_TIME)) },
                label = { Text("Реальное время") }
            )
        }
        items(PLAYBACK_SPEEDS) { speed ->
            FilterChip(
                selected = state.playbackSpeed == speed,
                onClick = { onAction(LiveTrackMapAction.SetPlaybackSpeed(speed)) },
                label = { Text("×$speed") }
            )
        }
    }
}

/** Время на ползунке: время суток или «+М:СС» от старта. */
private fun replayTimeText(state: LiveTrackMapState): String = when (state.replayMode) {
    ReplayTimeMode.REAL_TIME -> CLOCK_FORMAT.format(Instant.ofEpochMilli(state.replayClampedPosition))
    ReplayTimeMode.MASS_START -> "+${durationText(state.replayClampedPosition)}"
}

/** Состояние участника в момент просмотра для списка. */
private fun replayStatusText(state: LiveTrackMapState, replayTrack: ReplayTrack): String {
    val t = replayTrack.timeAt(state.replayClampedPosition, state.replayMode)
    return when (replayTrack.positionAt(t).state) {
        ReplayRunnerState.NOT_STARTED -> "старт ${CLOCK_FORMAT.format(Instant.ofEpochMilli(replayTrack.startAt))}"
        ReplayRunnerState.FINISHED -> "финиш ${durationText(replayTrack.duration)}"
        ReplayRunnerState.NO_DATA -> "${durationText(t - replayTrack.startAt)} • нет данных"
        ReplayRunnerState.RUNNING -> durationText(t - replayTrack.startAt)
    }
}

/** Длительность «М:СС» или «Ч:ММ:СС». */
private fun durationText(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
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

/**
 * Наши слои на карте — чтобы при обновлении заменять только их. [static] (КП, треки) пересобираются,
 * только когда изменились данные; [frame] (хвосты и маркеры просмотра) — на каждом кадре.
 */
private class OverlayHolder {
    var ground: GroundOverlay? = null
    var groundBitmap: Bitmap? = null
    val static = mutableListOf<Overlay>()
    var staticState: LiveTrackMapState? = null
    val frame = mutableListOf<Overlay>()
    val icons = HashMap<String, BitmapDrawable>()
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
        focus.collect { (track, wholeTrack, point) ->
            val mapView = mapViewRef.value ?: return@collect
            if (wholeTrack && track.points.size > 1) {
                mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(track.points.map { GeoPoint(it.lat, it.lon) }), true, 48)
            } else {
                mapView.controller.animateTo(GeoPoint(point.lat, point.lon))
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

    mapView.overlays.removeAll(holder.frame)
    holder.frame.clear()
    if (staticChanged(holder.staticState, state)) {
        holder.staticState = state
        mapView.overlays.removeAll(holder.static)
        holder.static.clear()
        holder.static += controlPointOverlays(mapView, state)
        if (state.replay) {
            state.replayShown.forEach { holder.static += faintTrackOverlays(mapView, state, it) }
        } else {
            state.mapTracks.forEach { track -> holder.static += trackOverlays(mapView, state, track, holder) }
        }
        mapView.overlays.addAll(holder.static)
    }
    if (state.replay) {
        val frames = state.replayShown.map { replayFrame(mapView, state, it, holder) }
        // Маркеры поверх всех хвостов.
        holder.frame += frames.flatMap { it.first } + frames.mapNotNull { it.second }
        mapView.overlays.addAll(holder.frame)
    }

    if (!holder.initialZoomDone) {
        initialBounds(state)?.let { bounds ->
            holder.initialZoomDone = true
            mapView.post { mapView.zoomToBoundingBox(bounds, false, 48) }
        }
    }
    mapView.invalidate()
}

/** Изменилось ли что-то, кроме позиции и скорости воспроизведения. Списки сравниваются сначала по ссылке — это дёшево. */
private fun staticChanged(previous: LiveTrackMapState?, state: LiveTrackMapState): Boolean {
    if (previous == null) return true
    fun LiveTrackMapState.withoutPlayback() = copy(replayPosition = 0, isPlaying = false, playbackSpeed = 0)
    return previous.withoutPlayback() != state.withoutPlayback()
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

private fun trackOverlays(mapView: MapView, state: LiveTrackMapState, track: ViewerTrack, holder: OverlayHolder): List<Overlay> {
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
        icon = holder.cachedMarkerIcon(mapView.context, markerColor, markerLabel(track), faded = !track.isActive)
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

/** Полный трек участника за хвостом при просмотре — бледный (в режиме скорости — бледный градиент). */
private fun faintTrackOverlays(mapView: MapView, state: LiveTrackMapState, replayTrack: ReplayTrack): List<Overlay> {
    val profile = state.speedProfileOf(replayTrack.track)
    if (profile != null) {
        return profile.chunks.mapNotNull { chunk ->
            val points = chunk.points.filter { it.t in replayTrack.startAt..replayTrack.finishAt }
            if (points.size < 2) null else trackLine(mapView, points.toGeoPoints(), withAlpha(speedColor(chunk.level), FAINT_TRACK_ALPHA), 5f)
        }
    }
    val color = withAlpha(trackColor(state, replayTrack.track), FAINT_TRACK_ALPHA)
    return replayTrack.track.segments().filter { it.size > 1 }.map { trackLine(mapView, it.toGeoPoints(), color, 5f) }
}

/** Кадр просмотра для участника: яркий хвост за последние [REPLAY_TAIL_MS] и маркер (до старта — ничего). */
private fun replayFrame(mapView: MapView, state: LiveTrackMapState, replayTrack: ReplayTrack, holder: OverlayHolder): Pair<List<Overlay>, Marker?> {
    val track = replayTrack.track
    val t = replayTrack.timeAt(state.replayClampedPosition, state.replayMode)
    val position = replayTrack.positionAt(t)
    if (position.state == ReplayRunnerState.NOT_STARTED) return emptyList<Overlay>() to null
    val color = trackColor(state, track)
    val tail = replayTrack.tail(t)
    val profile = state.speedProfileOf(track)
    val lines = if (profile != null) {
        val now = minOf(t, replayTrack.finishAt)
        val casing = tail.filter { it.size > 1 }.map { trackLine(mapView, it.toGeoPoints(), SPEED_CASING_COLOR, 11f) }
        casing + profile.chunks.mapNotNull { chunk ->
            val points = chunk.points.filter { it.t > now - REPLAY_TAIL_MS && it.t <= now && it.t >= replayTrack.startAt }
            if (points.size < 2) null else trackLine(mapView, points.toGeoPoints(), speedColor(chunk.level), 7f)
        }
    } else {
        tail.filter { it.size > 1 }.map { trackLine(mapView, it.toGeoPoints(), color, 7f) }
    }
    val markerColor = if (position.state == ReplayRunnerState.NO_DATA) STALE_COLOR else color
    val marker = Marker(mapView).apply {
        this.position = GeoPoint(position.point.lat, position.point.lon)
        icon = holder.cachedMarkerIcon(mapView.context, markerColor, markerLabel(track), faded = position.state == ReplayRunnerState.FINISHED)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        title = track.displayName
        snippet = listOfNotNull(track.groupName, replayStatusText(state, replayTrack)).joinToString(" • ")
    }
    return lines to marker
}

private fun List<ViewerTrackPoint>.toGeoPoints(): List<GeoPoint> = map { GeoPoint(it.lat, it.lon) }

private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

private fun markerLabel(track: ViewerTrack): String = track.startNumber?.toString() ?: track.displayName.take(1)

/** Иконки маркеров кэшируются: при просмотре маркеры пересоздаются на каждом кадре. */
private fun OverlayHolder.cachedMarkerIcon(context: Context, color: Int, label: String, faded: Boolean): BitmapDrawable =
    icons.getOrPut("$color|$label|$faded") { markerIcon(context, color, label, faded) }

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
    (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
}.getOrNull()
