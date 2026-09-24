package com.competra.eventdetails.presentation.live_tracks

import androidx.lifecycle.viewModelScope
import com.competra.data.navigation.EventsNavigation
import com.competra.data.navigation.Navigation
import com.competra.domain.repository.livetrack.LiveTrackViewerRepository
import com.competra.domain.repository.orienteering.OrienteeringCompetitionRemoteRepository
import com.competra.ui.BaseAction
import com.competra.ui.BaseState
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Период обновления списка (счётчики «на дистанции»). */
private const val REFRESH_INTERVAL_MS = 15_000L

/**
 * Дистанция в списке онлайн-треков.
 *
 * @property hasMap У дистанции прикреплена карта (иначе треки будут на подложке OSM).
 */
data class LiveTrackDistanceItem(
    val distanceId: Long,
    val name: String,
    val activeCount: Int,
    val totalCount: Int,
    val hasMap: Boolean
)

/** Состояние экрана выбора дистанции. */
data class LiveTracksState(
    val isLoading: Boolean = true,
    val distances: List<LiveTrackDistanceItem> = emptyList(),
    val isError: Boolean = false
) : BaseState

/** Действия экрана выбора дистанции. */
sealed interface LiveTracksAction : BaseAction {
    data class OpenDistance(val distanceId: Long) : LiveTracksAction
}

/**
 * Онлайн-треки соревнования для зрителя: дистанции, по которым есть треки. Список обновляется,
 * пока экран виден ([start]/[stop] — по жизненному циклу экрана).
 */
class LiveTracksViewModel(
    private val viewerRepository: LiveTrackViewerRepository,
    private val competitionRepository: OrienteeringCompetitionRemoteRepository,
    private val navigation: Navigation
) : BaseViewModel<LiveTracksState>(LiveTracksState()) {

    private var eventId: String? = null
    private var pollJob: Job? = null
    private var mapByDistance: Map<Long, Pair<String?, Boolean>> = emptyMap()

    /** Начинает (или возобновляет) обновление списка для соревнования [eventId]. */
    fun start(eventId: String) {
        this.eventId = eventId
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            if (mapByDistance.isEmpty()) {
                competitionRepository.getDistancesForCompetition(eventId).onSuccess { distances ->
                    mapByDistance = distances.mapNotNull { d -> d.remoteId?.let { it to (d.name to (d.map != null)) } }.toMap()
                }
            }
            while (isActive) {
                viewerRepository.distances(eventId)
                    .onSuccess { tracked ->
                        val items = tracked.map { d ->
                            val (mainName, hasMap) = mapByDistance[d.distanceId] ?: (null to false)
                            LiveTrackDistanceItem(
                                distanceId = d.distanceId,
                                name = d.name ?: mainName ?: "Дистанция",
                                activeCount = d.activeCount,
                                totalCount = d.totalCount,
                                hasMap = hasMap
                            )
                        }
                        updateState { copy(isLoading = false, distances = items, isError = false) }
                    }
                    .onFailure { updateState { copy(isLoading = false, isError = true) } }
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    /** Останавливает обновление (экран ушёл в фон). */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            is LiveTracksAction.OpenDistance -> {
                val id = eventId ?: return
                viewModelScope.launch {
                    navigation.navigate(EventsNavigation.LiveTrackMapRoute(eventId = id, distanceId = action.distanceId))
                }
            }
        }
    }
}
