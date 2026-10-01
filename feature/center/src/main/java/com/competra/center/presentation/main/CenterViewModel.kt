package com.competra.center.presentation.main

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.competra.center.data.CenterEffects
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.center.data.main.CenterState
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.data.navigation.CenterNavigation
import com.competra.data.navigation.Navigation
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.repository.LoadingRepository
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.orienteering.OrienteeringCompetitionRemoteRepository
import com.competra.domain.repository.user.UserRepository
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import com.competra.utils.constants.EventsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CenterViewModel(
    private val userRepository: UserRepository,
    private val navigation: Navigation,
    private val orienteeringCompetitionRemoteRepository: OrienteeringCompetitionRemoteRepository,
    private val orienteeringCompetitionInteractor: OrienteeringCompetitionInteractor,
    private val networkErrorRepository: NetworkErrorRepository,
    private val loadingRepository: LoadingRepository,
    private val analytics: AnalyticsTracker
) : BaseViewModel<CenterState>(CenterState()) {

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
                viewModelScope.launch(Dispatchers.IO) {
                    loadingRepository.emit(true)
                    orienteeringCompetitionInteractor.deleteCompetition(effect.competition.competitionId)
                        .onSuccess {
                            updateState {
                                copy(controlledEvents = controlledEvents.filter {
                                    it.competitionId != effect.competition.competitionId
                                })
                            }
                        }
                        .onFailure { handleFailure(it) }
                    loadingRepository.emit(false)
                }
            }
        }
    }

    fun initialize() {
        viewModelScope.launch {
            val isAuthed = userRepository.isAuthorized()
            updateState {
                copy(isAuthed = isAuthed)
            }
            if (isAuthed) {
                loadingRepository.emit(true)
                userRepository.retrieveUser().onSuccess { user ->
                    orienteeringCompetitionInteractor.getCompetitionsByUserId(user.id)
                        .onSuccess { competitions ->
                            Log.d("LOG_TAG", "initialize: ${competitions.size}")
                            updateState {
                                copy(controlledEvents = competitions.sortedByDescending { it.competition.startDate })
                            }
                        }
                        .onFailure { handleFailure(it) }
//                    orienteeringCompetitionRemoteRepository.getCompetitionsByUserid(user.id)
//                        .onSuccess { competitions ->
//
//                            updateState {
//                                copy(controlledEvents = competitions)
//                            }
//                        }
                }.onFailure { handleFailure(it) }
                loadingRepository.emit(false)
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
