package com.competra.eventdetails.presentation.live_tracks

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.data.navigation.Navigation
import com.competra.domain.models.livetrack.LiveTrackAccumulator
import com.competra.domain.models.livetrack.ReplayTimeMode
import com.competra.domain.models.livetrack.ReplayTrack
import com.competra.domain.models.livetrack.TrackSpeedProfile
import com.competra.domain.models.livetrack.ViewerTrack
import com.competra.domain.models.livetrack.ViewerTrackPoint
import com.competra.domain.models.livetrack.mergedByParticipant
import com.competra.domain.models.livetrack.replayRange
import com.competra.domain.models.livetrack.speedProfile
import com.competra.domain.models.livetrack.toReplay
import com.competra.domain.models.orienteering.ControlPoint
import com.competra.domain.models.orienteering.DistanceMap
import com.competra.domain.repository.livetrack.LiveTrackViewerRepository
import com.competra.domain.repository.orienteering.OrienteeringCompetitionRemoteRepository
import com.competra.ui.BaseAction
import com.competra.ui.BaseState
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Опрос, пока на дистанции есть участники. */
private const val LIVE_POLL_MS = 5_000L

/** Опрос, когда все финишировали (вдруг кто-то ещё стартует). */
private const val IDLE_POLL_MS = 30_000L

/** Пауза после ошибки сети. */
private const val ERROR_RETRY_MS = 10_000L

/** «Хвост» — последние столько минут трека. */
val TAIL_WINDOW_MS: Long = TimeUnit.MINUTES.toMillis(5)

/** Шаг анимации просмотра. */
private const val REPLAY_FRAME_MS = 100L

/** Скорости воспроизведения (во сколько раз быстрее реального времени). */
val PLAYBACK_SPEEDS = listOf(1, 10, 30, 60)

/** Скорость воспроизведения по умолчанию: часовая гонка — за две минуты. */
private const val DEFAULT_PLAYBACK_SPEED = 30

/** Перезапрос результатов, если появился финишировавший без результата, — не чаще. */
private val RESULTS_RELOAD_MS = TimeUnit.MINUTES.toMillis(1)

/**
 * Состояние карты онлайн-треков дистанции.
 *
 * @property distanceName Название дистанции.
 * @property map Геопривязанная карта дистанции или `null` (тогда только подложка OSM).
 * @property controlPoints КП с координатами.
 * @property tracks Треки дистанции, по одному на участника (сессии склеены).
 * @property colorIndex Стабильный номер цвета участника по id сессии.
 * @property serverTime Время сервера для «нет данных N мин».
 * @property selectedGroups Показываемые группы; пусто — все.
 * @property tailOnly Показывать только последние [TAIL_WINDOW_MS] трека.
 * @property isConnectionLost Последний запрос не удался (данные на экране могут устареть).
 * @property speedMode Завершённые треки раскрашены по скорости (красный — медленно, зелёный — быстро).
 * @property speedProfiles Раскраска по скорости завершённых треков по id сессии.
 * @property selectedSessionId Участник, выбранный в режиме скорости: показывается только его трек.
 * @property replay Режим просмотра завершённых треков ползунком.
 * @property replayTracks Завершённые треки, обрезанные по старту и финишу из результатов, по id сессии.
 * @property replayMode Шкала времени просмотра.
 * @property replayPosition Позиция ползунка: Unix ms в реальном времени, ms от старта при общем старте.
 * @property isPlaying Идёт воспроизведение.
 * @property playbackSpeed Скорость воспроизведения из [PLAYBACK_SPEEDS].
 * @property checkedSessionIds Отмеченные для сравнения участники; пусто — все показанные.
 */
data class LiveTrackMapState(
    val isLoading: Boolean = true,
    val distanceName: String = "",
    val map: DistanceMap? = null,
    val controlPoints: List<ControlPoint> = emptyList(),
    val tracks: List<ViewerTrack> = emptyList(),
    val colorIndex: Map<String, Int> = emptyMap(),
    val serverTime: Long = 0,
    val selectedGroups: Set<String> = emptySet(),
    val tailOnly: Boolean = false,
    val isConnectionLost: Boolean = false,
    val speedMode: Boolean = false,
    val speedProfiles: Map<String, TrackSpeedProfile> = emptyMap(),
    val selectedSessionId: String? = null,
    val replay: Boolean = false,
    val replayTracks: Map<String, ReplayTrack> = emptyMap(),
    val replayMode: ReplayTimeMode = ReplayTimeMode.MASS_START,
    val replayPosition: Long = 0,
    val isPlaying: Boolean = false,
    val playbackSpeed: Int = DEFAULT_PLAYBACK_SPEED,
    val checkedSessionIds: Set<String> = emptySet()
) : BaseState {

    /** Группы, встречающиеся среди треков. */
    val groups: List<String> get() = tracks.mapNotNull { it.groupName }.distinct().sorted()

    /** Треки с учётом фильтра групп. */
    val visibleTracks: List<ViewerTrack>
        get() = if (selectedGroups.isEmpty()) tracks else tracks.filter { it.groupName in selectedGroups }

    /** Кто-то ещё на дистанции — режим «онлайн», иначе архив. */
    val isLive: Boolean get() = tracks.any { it.isActive }

    /** Есть завершённые треки, которые можно раскрасить по скорости. */
    val canShowSpeed: Boolean get() = visibleTracks.any { it.sessionId in speedProfiles }

    /** Выбранный участник в режиме скорости, если он не скрыт фильтром групп (при просмотре — не используется). */
    val speedSelection: ViewerTrack?
        get() = if (speedMode && !replay) visibleTracks.firstOrNull { it.sessionId == selectedSessionId } else null

    /** Есть завершённые треки, которые можно просмотреть ползунком. */
    val canReplay: Boolean get() = visibleTracks.any { it.sessionId in replayTracks }

    /** Треки просмотра: завершённые показанные, а если кто-то отмечен — только отмеченные. */
    val replayShown: List<ReplayTrack>
        get() {
            val finished = visibleTracks.mapNotNull { replayTracks[it.sessionId] }
            return finished.filter { it.track.sessionId in checkedSessionIds }.ifEmpty { finished }
        }

    /** Диапазон ползунка. */
    val replayRange: LongRange? get() = replayShown.replayRange(replayMode)

    /** Позиция ползунка в пределах [replayRange] (диапазон меняется при смене галочек и групп). */
    val replayClampedPosition: Long get() = replayRange?.let { replayPosition.coerceIn(it) } ?: 0

    /** Треки на карте: при просмотре — [replayShown], в режиме скорости с выбранным участником — только он. */
    val mapTracks: List<ViewerTrack>
        get() = when {
            replay -> replayShown.map { it.track }
            else -> speedSelection?.let { listOf(it) } ?: visibleTracks
        }

    /** Раскраска по скорости для трека, если режим включён и участник финишировал. */
    fun speedProfileOf(track: ViewerTrack): TrackSpeedProfile? =
        if (speedMode && !track.isActive) speedProfiles[track.sessionId] else null
}

/**
 * Запрос экрану показать участника.
 *
 * @property wholeTrack Вписать в экран весь трек (режим скорости), иначе — центрировать на [point].
 * @property point Куда центрировать: положение при просмотре, иначе последняя точка трека.
 */
data class TrackFocus(val track: ViewerTrack, val wholeTrack: Boolean, val point: ViewerTrackPoint)

/** Действия карты онлайн-треков. */
sealed interface LiveTrackMapAction : BaseAction {
    data class ToggleGroup(val group: String) : LiveTrackMapAction
    data object ToggleTail : LiveTrackMapAction
    data object ToggleSpeedMode : LiveTrackMapAction
    data object ToggleReplay : LiveTrackMapAction
    data class SetReplayMode(val mode: ReplayTimeMode) : LiveTrackMapAction
    data class SeekReplay(val position: Long) : LiveTrackMapAction
    data object TogglePlay : LiveTrackMapAction
    data class SetPlaybackSpeed(val speed: Int) : LiveTrackMapAction
    data class ToggleChecked(val sessionId: String) : LiveTrackMapAction
    data object Back : LiveTrackMapAction

    /** Нажатие на участника: показать на карте, в режиме скорости — выбрать (повторно — снять выбор). */
    data class FocusTrack(val sessionId: String) : LiveTrackMapAction
}

/**
 * Карта онлайн-треков дистанции: сначала архив (полные треки, в т.ч. давно закрытые), затем опрос
 * `live` с курсором — раз в [LIVE_POLL_MS], пока кто-то на дистанции, иначе раз в [IDLE_POLL_MS].
 * Опрос идёт, только пока экран виден ([start]/[stop]).
 */
class LiveTrackMapViewModel(
    private val viewerRepository: LiveTrackViewerRepository,
    private val competitionRepository: OrienteeringCompetitionRemoteRepository,
    private val analytics: AnalyticsTracker,
    private val navigation: Navigation
) : BaseViewModel<LiveTrackMapState>(LiveTrackMapState()) {

    private val accumulator = LiveTrackAccumulator()
    private val colors = LinkedHashMap<String, Int>()
    private var pollJob: Job? = null
    private var distanceLoaded = false
    private var archiveLoaded = false
    private var openedReported = false
    private var eventId: String? = null
    private var playJob: Job? = null

    /** Старт и финиш из результатов по id участника. */
    private var resultTimes: Map<String, Pair<Long?, Long?>> = emptyMap()
    private var resultsLoadedAt = 0L

    /** Раскраска по скорости завершённых треков; пересчитывается, только если у трека изменились точки. */
    private val speedCache = HashMap<String, Pair<List<ViewerTrackPoint>, TrackSpeedProfile?>>()

    private val _focus = MutableSharedFlow<TrackFocus>(extraBufferCapacity = 1)

    /** Запросы экрану показать участника на карте. */
    val focus: SharedFlow<TrackFocus> = _focus.asSharedFlow()

    /** Начинает (или возобновляет) загрузку и опрос треков дистанции. */
    fun start(eventId: String, distanceId: Long) {
        if (pollJob?.isActive == true) return
        this.eventId = eventId
        pollJob = viewModelScope.launch {
            if (!distanceLoaded) loadDistance(eventId, distanceId)
            while (isActive) {
                val ok = refresh(distanceId)
                if (ok && !openedReported) {
                    openedReported = true
                    val mode = if (accumulator.hasActive()) AnalyticsEvent.LiveTrackMapMode.LIVE else AnalyticsEvent.LiveTrackMapMode.ARCHIVE
                    analytics.trackEvent(AnalyticsEvent.LiveTrackMapOpened(eventId, distanceId, mode))
                }
                delay(
                    when {
                        !ok -> ERROR_RETRY_MS
                        accumulator.hasActive() -> LIVE_POLL_MS
                        else -> IDLE_POLL_MS
                    }
                )
            }
        }
    }

    /** Останавливает опрос и воспроизведение (экран ушёл в фон). */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
        pause()
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            is LiveTrackMapAction.ToggleGroup -> updateState {
                copy(selectedGroups = if (action.group in selectedGroups) selectedGroups - action.group else selectedGroups + action.group)
            }
            is LiveTrackMapAction.ToggleTail -> updateState { copy(tailOnly = !tailOnly) }
            is LiveTrackMapAction.ToggleSpeedMode -> updateState { copy(speedMode = !speedMode, selectedSessionId = null) }
            is LiveTrackMapAction.FocusTrack -> focusTrack(action.sessionId)
            is LiveTrackMapAction.ToggleReplay -> {
                pause()
                updateState { copy(replay = !replay, replayPosition = replayRange?.first ?: 0) }
            }
            is LiveTrackMapAction.SetReplayMode -> {
                pause()
                updateState { copy(replayMode = action.mode).let { it.copy(replayPosition = it.replayRange?.first ?: 0) } }
            }
            is LiveTrackMapAction.SeekReplay -> updateState { copy(replayPosition = action.position) }
            is LiveTrackMapAction.TogglePlay -> if (stateValue.isPlaying) pause() else play()
            is LiveTrackMapAction.SetPlaybackSpeed -> updateState { copy(playbackSpeed = action.speed) }
            is LiveTrackMapAction.ToggleChecked -> updateState {
                val id = action.sessionId
                copy(checkedSessionIds = if (id in checkedSessionIds) checkedSessionIds - id else checkedSessionIds + id)
            }
            is LiveTrackMapAction.Back -> viewModelScope.launch { navigation.back() }
        }
    }

    /** Воспроизведение с текущей позиции (с начала, если ползунок в конце) до конца диапазона. */
    private fun play() {
        val range = stateValue.replayRange ?: return
        if (stateValue.replayClampedPosition >= range.last) updateState { copy(replayPosition = range.first) }
        updateState { copy(isPlaying = true) }
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (isActive) {
                delay(REPLAY_FRAME_MS)
                val state = stateValue
                val end = state.replayRange?.last ?: break
                val next = state.replayClampedPosition + REPLAY_FRAME_MS * state.playbackSpeed
                updateState { copy(replayPosition = minOf(next, end)) }
                if (next >= end) break
            }
            updateState { copy(isPlaying = false) }
        }
    }

    private fun pause() {
        playJob?.cancel()
        playJob = null
        if (stateValue.isPlaying) updateState { copy(isPlaying = false) }
    }

    private fun focusTrack(sessionId: String) {
        val state = stateValue
        val track = state.tracks.firstOrNull { it.sessionId == sessionId } ?: return
        val last = track.points.lastOrNull() ?: return
        when {
            state.replay -> {
                val replayTrack = state.replayTracks[sessionId] ?: return
                val position = replayTrack.positionAt(replayTrack.timeAt(state.replayClampedPosition, state.replayMode))
                _focus.tryEmit(TrackFocus(replayTrack.track, wholeTrack = false, point = position.point))
            }
            state.speedMode -> {
                val deselect = state.selectedSessionId == sessionId
                updateState { copy(selectedSessionId = if (deselect) null else sessionId) }
                if (!deselect) _focus.tryEmit(TrackFocus(track, wholeTrack = true, point = last))
            }
            else -> _focus.tryEmit(TrackFocus(track, wholeTrack = false, point = last))
        }
    }

    private suspend fun loadDistance(eventId: String, distanceId: Long) {
        competitionRepository.getDistancesForCompetition(eventId).onSuccess { distances ->
            val distance = distances.firstOrNull { it.remoteId == distanceId } ?: return@onSuccess
            distanceLoaded = true
            updateState {
                copy(
                    distanceName = distance.name.orEmpty(),
                    map = distance.map,
                    controlPoints = distance.controlPoints.filter { it.latitude != null && it.longitude != null }
                )
            }
        }
    }

    /** Архив (один раз) + снимок `live`; `false` — сеть недоступна. */
    private suspend fun refresh(distanceId: Long): Boolean {
        if (!archiveLoaded) {
            viewerRepository.tracks(distanceId).onSuccess {
                accumulator.applyArchive(it)
                archiveLoaded = true
            }
        }
        val result = viewerRepository.live(distanceId, accumulator.cursor)
        result.onSuccess { accumulator.applySnapshot(it) }
        loadResultsIfNeeded()
        publish(connectionLost = result.isFailure)
        return result.isSuccess
    }

    /**
     * Старт и финиш участников для обрезки треков при просмотре: при первом опросе и когда появился
     * закрытый трек участника без результата (не чаще [RESULTS_RELOAD_MS]).
     */
    private suspend fun loadResultsIfNeeded() {
        val eventId = eventId ?: return
        val now = System.currentTimeMillis()
        val missing = accumulator.tracks().any { !it.isActive && it.participantId !in resultTimes }
        if (resultsLoadedAt != 0L && (!missing || now - resultsLoadedAt < RESULTS_RELOAD_MS)) return
        resultsLoadedAt = now
        competitionRepository.getResultsByCompetition(eventId).onSuccess { results ->
            resultTimes = results.associate { it.participantId to (it.startTime?.takeIf { t -> t > 0 } to it.finishTime?.takeIf { t -> t > 0 }) }
        }
    }

    private fun publish(connectionLost: Boolean) {
        // Перезапуски трека одним участником показываем как один трек.
        val tracks = accumulator.tracks().mergedByParticipant()
        tracks.sortedBy { it.startedAt }.forEach { colors.getOrPut(it.sessionId) { colors.size } }
        val speedProfiles = tracks.filter { !it.isActive }.mapNotNull { track ->
            val cached = speedCache[track.sessionId]?.takeIf { it.first == track.points }
            val profile = if (cached != null) cached.second else track.speedProfile()
            speedCache[track.sessionId] = track.points to profile
            profile?.let { track.sessionId to it }
        }.toMap()
        val replayTracks = tracks.filter { !it.isActive }.mapNotNull { track ->
            val (start, finish) = resultTimes[track.participantId] ?: (null to null)
            track.toReplay(start, finish)?.let { track.sessionId to it }
        }.toMap()
        updateState {
            copy(
                isLoading = false,
                tracks = tracks,
                colorIndex = colors.toMap(),
                speedProfiles = speedProfiles,
                replayTracks = replayTracks,
                serverTime = accumulator.serverTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                isConnectionLost = connectionLost
            )
        }
    }
}
