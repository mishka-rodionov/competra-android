package com.competra.center.presentation.start_grid

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.center.data.start_grid.StartGridAction
import com.competra.center.data.start_grid.StartGridState
import com.competra.center.data.start_grid.StartSlot
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.getArguments
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.repository.LoadingRepository
import com.competra.ui.BaseAction
import com.competra.ui.CompetitionStartTimeRepository
import com.competra.ui.viewmodel.BaseViewModel
import com.competra.utils.constants.EventsConstants
import com.competra.utils.isValidStartTimestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel экрана «Стартовая решётка».
 *
 * Загружает участников соревнования, группирует их по стартовым минутам и ведёт секундомер
 * соревнования, по ходу которого подсвечивается текущая стартующая минута.
 *
 * @property navigation Навигация приложения (источник competitionId).
 * @property interactor Интерактор данных соревнований.
 * @property startTimeRepository Надёжный источник времени старта во время гонки.
 * @property analytics Трекер продуктовой аналитики.
 * @property loadingRepository Глобальный индикатор загрузки.
 */
class StartGridViewModel(
    private val navigation: Navigation,
    private val interactor: OrienteeringCompetitionInteractor,
    private val startTimeRepository: CompetitionStartTimeRepository,
    private val analytics: AnalyticsTracker,
    private val loadingRepository: LoadingRepository
) : BaseViewModel<StartGridState>(StartGridState()) {

    val competitionId: String? = navigation.getArguments<String>(EventsConstants.EVENT_ID.name)

    private var stopwatchJob: Job? = null

    override fun onAction(action: BaseAction) {
        when (action) {
            StartGridAction.Reload -> loadData()
            is StartGridAction.SearchChanged -> updateState { copy(searchQuery = action.value) }
        }
    }

    /**
     * Загружает соревнование и участников, строит стартовые минуты и запускает секундомер.
     * Трекает факт открытия экрана.
     */
    fun loadData() {
        val id = competitionId ?: return
        analytics.trackEvent(AnalyticsEvent.CenterStartGridOpened(id))
        viewModelScope.launch {
            loadingRepository.emit(true)
            interactor.getCompetitionWithDetails(id).onSuccess { details ->
                val participants = details.groupsWithParticipants
                    .flatMap { it.participants }
                    .filter { it.startNumber.isNotEmpty() && it.startTime.isValidStartTimestamp() }

                val slots = participants
                    .groupBy { it.startTime }
                    .toSortedMap()
                    .map { (startTime, group) ->
                        StartSlot(
                            startTime = startTime,
                            participants = group.sortedBy { it.startNumber.toIntOrNull() ?: Int.MAX_VALUE }
                        )
                    }

                val isRunning = details.competition.competition.status == CompetitionStatus.IN_PROGRESS
                updateState {
                    copy(
                        slots = slots,
                        competition = details.competition,
                        isCompetitionRunning = isRunning,
                        currentSlotIndex = computeCurrentSlotIndex(slots, System.currentTimeMillis())
                    )
                }
                if (isRunning) startStopwatch()
            }
            loadingRepository.emit(false)
        }
    }

    /**
     * Запускает секундомер соревнования: каждые 16 мс обновляет прошедшее время и
     * пересчитывает индекс текущей стартующей минуты.
     */
    private fun startStopwatch() {
        stopwatchJob?.cancel()
        stopwatchJob = viewModelScope.launch {
            while (true) {
                val startTimeMs = startTimeRepository.startTimeMs.value
                    .takeIf { it > 0L }
                    ?: stateValue.competition?.startTime
                    ?: break
                val now = System.currentTimeMillis()
                val elapsed = (now - startTimeMs).coerceAtLeast(0L)
                updateState {
                    copy(
                        stopwatchMillis = elapsed,
                        currentSlotIndex = computeCurrentSlotIndex(slots, now)
                    )
                }
                delay(16)
            }
        }
    }

    /**
     * Индекс ближайшей минуты, которая ещё не стартовала (startTime >= now).
     * Если все минуты уже стартовали — возвращает индекс последней.
     */
    private fun computeCurrentSlotIndex(slots: List<StartSlot>, now: Long): Int {
        if (slots.isEmpty()) return 0
        val idx = slots.indexOfFirst { it.startTime >= now }
        return if (idx >= 0) idx else slots.lastIndex
    }

    override fun onCleared() {
        super.onCleared()
        stopwatchJob?.cancel()
    }
}
