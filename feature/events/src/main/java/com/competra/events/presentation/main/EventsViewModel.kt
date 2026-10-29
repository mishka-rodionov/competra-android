package com.competra.events.presentation.main

import android.os.Parcelable
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.core.tracking.LiveTrackStarter
import com.competra.data.navigation.EventsNavigation
import com.competra.data.navigation.Navigation
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.Competition
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.events.EventsFilter
import com.competra.domain.repository.LoadingRepository
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.events.EventsRepository
import com.competra.domain.repository.livetrack.LiveTrackLocalRepository
import com.competra.domain.repository.livetrack.MyStartsRepository
import com.competra.events.data.main.EVENTS_PAGE_SIZE
import com.competra.events.data.main.EventsAction
import com.competra.events.data.main.EventsPagingSource
import com.competra.events.data.main.EventsState
import com.competra.events.data.main.TodayStart
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

/** Как часто пересчитывать, какие соревнования «сегодня» (смена дня при открытом экране). */
private const val TODAY_TICK_MS = 60_000L

/**
 * ViewModel вкладки «События»: публичный список соревнований и карточки «Сегодня старт» —
 * соревнования пользователя на сегодня из локального кэша с запуском онлайн-трека без интернета.
 */
class EventsViewModel(
    private val navigation: Navigation,
    private val eventsRepository: EventsRepository,
    private val networkErrorRepository: NetworkErrorRepository,
    private val loadingRepository: LoadingRepository,
    private val analytics: AnalyticsTracker,
    private val myStartsRepository: MyStartsRepository,
    private val liveTrackLocalRepository: LiveTrackLocalRepository,
    private val liveTrackStarter: LiveTrackStarter,
) : BaseViewModel<EventsState>(EventsState()) {

    private val filterFlow = MutableStateFlow(stateValue.appliedFilter)

    /** Соревнование, для которого идёт цепочка согласие → разрешения → старт трека. */
    private var pendingLiveTrack: String? = null

    private val _liveTrackPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Запросы экрану проверить/запросить разрешения перед стартом трека (нужен Activity-контекст). */
    val liveTrackPermissionRequests: SharedFlow<Unit> = _liveTrackPermissionRequests.asSharedFlow()

    init {
        observeTodayStarts()
        refreshMyStarts()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val events: Flow<PagingData<Competition>> = filterFlow
        .flatMapLatest { filter ->
            Pager(
                config = PagingConfig(pageSize = EVENTS_PAGE_SIZE, initialLoadSize = EVENTS_PAGE_SIZE),
                pagingSourceFactory = {
                    EventsPagingSource(eventsRepository, filter, ::onRefreshLoading, ::handleFailure)
                }
            ).flow
        }
        .cachedIn(viewModelScope)

    override fun onAction(action: BaseAction) {

    }

    fun onAction(action: EventsAction) {
        when (action) {
            is EventsAction.EventClick -> {
                val id = action.eventId ?: return
                analytics.trackEvent(
                    AnalyticsEvent.EventOpened(
                        eventId = id,
                        source = AnalyticsEvent.EventSource.LIST,
                    )
                )
                viewModelScope.launch {
                    navigation.navigate(
                        EventsNavigation.EventDetailsRoute(eventId = id)
                    )
                }
            }
            is EventsAction.OpenFilterDialog -> {
                updateState { copy(isFilterDialogOpen = true) }
            }
            is EventsAction.CloseFilterDialog -> {
                updateState { copy(isFilterDialogOpen = false) }
            }
            is EventsAction.ApplyFilter -> {
                analytics.trackEvent(AnalyticsEvent.EventFilterApplied(action.filter.toAnalyticsParams()))
                updateState { copy(appliedFilter = action.filter, isFilterDialogOpen = false) }
                filterFlow.value = action.filter
            }
            is EventsAction.ResetFilter -> {
                val cleared = EventsFilter()
                updateState { copy(appliedFilter = cleared) }
                filterFlow.value = cleared
            }
            // Саму перезагрузку запускает экран через LazyPagingItems.refresh(); здесь только флаг индикатора.
            is EventsAction.Refresh -> {
                updateState { copy(isRefreshing = true) }
                refreshMyStarts()
            }
            is EventsAction.StartLiveTrack -> requestLiveTrackStart(action.competitionId)
            is EventsAction.OpenLiveTrack -> viewModelScope.launch {
                navigation.navigate(EventsNavigation.LiveTrackRunnerRoute(eventId = action.competitionId))
            }
            is EventsAction.LiveTrackConsentAccepted -> {
                updateState { copy(liveTrackConsentFor = null) }
                viewModelScope.launch {
                    liveTrackLocalRepository.setConsentGiven()
                    _liveTrackPermissionRequests.emit(Unit)
                }
            }
            is EventsAction.LiveTrackConsentDismissed -> {
                pendingLiveTrack = null
                updateState { copy(liveTrackConsentFor = null) }
            }
            is EventsAction.LiveTrackPermissionsResult -> onLiveTrackPermissionsResult(action.locationGranted)
        }
    }

    /** Карточки «Сегодня старт»: кэш «моих стартов» + локальная сессия трека по каждому. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTodayStarts() {
        val ticks = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(TODAY_TICK_MS)
            }
        }
        viewModelScope.launch {
            combine(myStartsRepository.observe(), ticks) { starts, now -> starts.filter { it.isToday(now) } }
                .distinctUntilChanged()
                .flatMapLatest { starts ->
                    if (starts.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(starts.map { start ->
                            liveTrackLocalRepository.observeSession(start.competitionId).map { TodayStart(start, it) }
                        }) { it.toList() }
                    }
                }
                .collect { todayStarts -> updateState { copy(todayStarts = todayStarts) } }
        }
    }

    /** Обновляет кэш «моих стартов»; без сети или авторизации остаётся прежний — молча. */
    private fun refreshMyStarts() {
        viewModelScope.launch { myStartsRepository.refresh() }
    }

    private fun requestLiveTrackStart(competitionId: String) {
        pendingLiveTrack = competitionId
        viewModelScope.launch {
            if (liveTrackLocalRepository.isConsentGiven()) {
                _liveTrackPermissionRequests.emit(Unit)
            } else {
                updateState { copy(liveTrackConsentFor = competitionId) }
            }
        }
    }

    private fun onLiveTrackPermissionsResult(locationGranted: Boolean) {
        val competitionId = pendingLiveTrack ?: return
        pendingLiveTrack = null
        viewModelScope.launch {
            if (!locationGranted) {
                networkErrorRepository.emit(NetworkErrorEvent(code = null, message = "Без доступа к геолокации трек не записать"))
                return@launch
            }
            liveTrackStarter.start(competitionId, AnalyticsEvent.LiveTrackStartSource.TODAY_CARD)
            navigation.navigate(EventsNavigation.LiveTrackRunnerRoute(eventId = competitionId))
        }
    }

    /**
     * Загрузка первой страницы: при pull-to-refresh крутится его индикатор, иначе — глобальный лоадер.
     * Глобальный лоадер скрывается в любом случае: если жест прервал первичную загрузку, её `false` не придёт.
     */
    private suspend fun onRefreshLoading(isLoading: Boolean) {
        if (isLoading) {
            if (!stateValue.isRefreshing) loadingRepository.emit(true)
        } else {
            updateState { copy(isRefreshing = false) }
            loadingRepository.emit(false)
        }
    }

    private fun EventsFilter.toAnalyticsParams(): Map<String, Any?> = mapOf(
        "kinds_count" to kindOfSports.size,
        "statuses_count" to statuses.size,
        "has_date_from" to (dateFrom != null),
        "has_date_to" to (dateTo != null),
        "include_test" to includeTest,
    )

    private fun handleFailure(throwable: Throwable) {
        viewModelScope.launch {
            val code = (throwable as? NetworkException)?.code
            networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
        }
    }
}

@Parcelize
data class DetailsInfo(
    val title: String,
    val description: String
) : Parcelable
