package com.competra.eventdetails.presentation.live_tracks

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.domain.models.livetrack.LiveTrackAccumulator
import com.competra.domain.models.livetrack.ViewerTrack
import com.competra.domain.models.livetrack.mergedByParticipant
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
    val isConnectionLost: Boolean = false
) : BaseState {

    /** Группы, встречающиеся среди треков. */
    val groups: List<String> get() = tracks.mapNotNull { it.groupName }.distinct().sorted()

    /** Треки с учётом фильтра групп. */
    val visibleTracks: List<ViewerTrack>
        get() = if (selectedGroups.isEmpty()) tracks else tracks.filter { it.groupName in selectedGroups }

    /** Кто-то ещё на дистанции — режим «онлайн», иначе архив. */
    val isLive: Boolean get() = tracks.any { it.isActive }
}

/** Действия карты онлайн-треков. */
sealed interface LiveTrackMapAction : BaseAction {
    data class ToggleGroup(val group: String) : LiveTrackMapAction
    data object ToggleTail : LiveTrackMapAction
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
    private val analytics: AnalyticsTracker
) : BaseViewModel<LiveTrackMapState>(LiveTrackMapState()) {

    private val accumulator = LiveTrackAccumulator()
    private val colors = LinkedHashMap<String, Int>()
    private var pollJob: Job? = null
    private var distanceLoaded = false
    private var archiveLoaded = false
    private var openedReported = false

    private val _focus = MutableSharedFlow<ViewerTrack>(extraBufferCapacity = 1)

    /** Запросы экрану показать участника на карте. */
    val focus: SharedFlow<ViewerTrack> = _focus.asSharedFlow()

    /** Начинает (или возобновляет) загрузку и опрос треков дистанции. */
    fun start(eventId: String, distanceId: Long) {
        if (pollJob?.isActive == true) return
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

    /** Останавливает опрос (экран ушёл в фон). */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            is LiveTrackMapAction.ToggleGroup -> updateState {
                copy(selectedGroups = if (action.group in selectedGroups) selectedGroups - action.group else selectedGroups + action.group)
            }
            is LiveTrackMapAction.ToggleTail -> updateState { copy(tailOnly = !tailOnly) }
            is LiveTrackMapAction.FocusTrack -> stateValue.tracks.firstOrNull { it.sessionId == action.sessionId }
                ?.takeIf { it.points.isNotEmpty() }
                ?.let { _focus.tryEmit(it) }
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
        publish(connectionLost = result.isFailure)
        return result.isSuccess
    }

    private fun publish(connectionLost: Boolean) {
        // Перезапуски трека одним участником показываем как один трек.
        val tracks = accumulator.tracks().mergedByParticipant()
        tracks.sortedBy { it.startedAt }.forEach { colors.getOrPut(it.sessionId) { colors.size } }
        updateState {
            copy(
                isLoading = false,
                tracks = tracks,
                colorIndex = colors.toMap(),
                serverTime = accumulator.serverTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                isConnectionLost = connectionLost
            )
        }
    }
}
