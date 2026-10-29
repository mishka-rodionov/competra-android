package com.competra.center.presentation.main

import androidx.lifecycle.viewModelScope
import com.competra.center.data.CenterEffects
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.center.data.main.CenterState
import com.competra.center.data.refresh.BackgroundRefresher
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.data.navigation.CenterNavigation
import com.competra.data.navigation.Navigation
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.repository.NetworkAvailabilityRepository
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.orienteering.OrienteeringCompetitionRemoteRepository
import com.competra.domain.repository.user.UserRepository
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import com.competra.utils.constants.EventsConstants
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel экрана «Центр».
 *
 * Список соревнований берётся из локальной БД (поток Room) и показывается сразу; свежие данные
 * с сервера подтягиваются в фоне ([BackgroundRefresher]) без глобального лоудера — на месте
 * старта связь бывает плохой, и экран не должен ждать сервер.
 */
class CenterViewModel(
    private val userRepository: UserRepository,
    private val navigation: Navigation,
    private val orienteeringCompetitionRemoteRepository: OrienteeringCompetitionRemoteRepository,
    private val orienteeringCompetitionInteractor: OrienteeringCompetitionInteractor,
    private val networkErrorRepository: NetworkErrorRepository,
    private val analytics: AnalyticsTracker,
    private val networkAvailabilityRepository: NetworkAvailabilityRepository
) : BaseViewModel<CenterState>(CenterState()) {

    /** Организатор, чьи соревнования сейчас показываются; null — не авторизован. */
    private var observedUserId: String? = null
    private var observeJob: Job? = null

    private val refresher = BackgroundRefresher(viewModelScope) {
        val userId = observedUserId ?: return@BackgroundRefresher null
        orienteeringCompetitionInteractor.refreshCompetitionsFromServer(userId).isSuccess
    }

    init {
        viewModelScope.launch {
            refresher.state.collect { refresh -> updateState { copy(refresh = refresh) } }
        }
        viewModelScope.launch {
            networkAvailabilityRepository.availableEvents.collect {
                if (observedUserId != null) refresher.refreshIfStale()
            }
        }
    }

    override fun onAction(action: BaseAction) {

    }

    fun handleEffects(effect: CenterEffects) {
        when (effect) {
            is CenterEffects.OpenKindOfSports -> {
                viewModelScope.launch {
                    navigation.navigate(CenterNavigation.KindOfSportRoute)
                }
            }

            is CenterEffects.OpenOrienteeringCreator -> viewModelScope.launch {
                navigation.navigate(CenterNavigation.CommonCompetitionFieldRoute())
//                navigation.navigate(CenterNavigation.OrienteeringCreatorRoute())
            }

            CenterEffects.OpenStationTools -> viewModelScope.launch {
                navigation.navigate(CenterNavigation.StationToolsRoute)
            }

            is CenterEffects.OpenOrienteeringEditor -> viewModelScope.launch {
                navigation.navigate(CenterNavigation.CommonCompetitionFieldRoute(competitionId = effect.competitionId))
//                navigation.navigate(CenterNavigation.OrienteeringCreatorRoute(competitionId = effect.competitionId))
            }

            is CenterEffects.OpenOrienteeringEventControl -> viewModelScope.launch {
                navigation.navigate(
                    CenterNavigation.OrienteeringEventControlRoute,
                    argument = navigation.createArguments(
                        EventsConstants.EVENT_ID.name to effect.competitionId
                    )
                )
            }

            is CenterEffects.ShowDeleteCompetitionDialog -> {
                updateState { copy(deletingCompetition = effect.competition) }
            }

            CenterEffects.HideDeleteCompetitionDialog -> {
                updateState { copy(deletingCompetition = null) }
            }

            is CenterEffects.DeleteCompetition -> {
                updateState { copy(deletingCompetition = null) }
                analytics.trackEvent(
                    AnalyticsEvent.CompetitionDeleted(effect.competition.competitionId)
                )
                // Удаление — локальная пометка (сеть не нужна), список обновит поток Room.
                viewModelScope.launch {
                    orienteeringCompetitionInteractor.deleteCompetition(effect.competition.competitionId)
                        .onFailure { handleFailure(it) }
                }
            }

            CenterEffects.Refresh -> refresher.refresh(isPull = true)
        }
    }

    /**
     * Вызывается при каждом показе экрана (ON_RESUME): проверяет авторизацию, подписывается на
     * локальный список соревнований и запускает фоновое обновление с сервера, если данные устарели.
     */
    fun onResume() {
        viewModelScope.launch {
            val user = userRepository.retrieveUser().getOrNull()
            updateState { copy(isAuthed = user != null) }
            observeCompetitions(user?.id)
            if (user != null) refresher.refreshIfStale()
//                    orienteeringCompetitionRemoteRepository.getCompetitionsByUserid(user.id)
//                        .onSuccess { competitions ->
//
//                            updateState {
//                                copy(controlledEvents = competitions)
//                            }
//                        }
        }
    }

    private fun observeCompetitions(userId: String?) {
        if (userId == observedUserId) return
        observedUserId = userId
        // Другой аккаунт — статус «Обновлено в …» и троттлинг прошлого к нему не относятся:
        // onResume сразу запустит загрузку списка нового пользователя.
        refresher.reset()
        observeJob?.cancel()
        updateState { copy(controlledEvents = emptyList(), isLocalLoaded = false) }
        if (userId == null) return
        observeJob = viewModelScope.launch {
            orienteeringCompetitionInteractor.observeCompetitionsByUserId(userId).collectLatest { competitions ->
                updateState {
                    copy(
                        controlledEvents = competitions.sortedByDescending { it.competition.startDate },
                        isLocalLoaded = true
                    )
                }
            }
        }
    }

    private fun handleFailure(throwable: Throwable) {
        viewModelScope.launch {
            val code = (throwable as? NetworkException)?.code
            networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
        }
    }

}
