package com.competra.rating.presentation.athlete_starts

import androidx.lifecycle.viewModelScope
import com.competra.data.navigation.EventsNavigation
import com.competra.data.navigation.Navigation
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.rating.RatingRepository
import com.competra.rating.data.athlete_starts.AthleteStartsAction
import com.competra.rating.data.athlete_starts.AthleteStartsState
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

class AthleteStartsViewModel(
    private val ratingRepository: RatingRepository,
    private val navigation: Navigation,
    private val networkErrorRepository: NetworkErrorRepository,
) : BaseViewModel<AthleteStartsState>(AthleteStartsState()) {

    fun initialize(ratingId: String, groupId: Long, participantKey: String) {
        if (stateValue.ratingId == ratingId && stateValue.groupId == groupId &&
            stateValue.participantKey == participantKey && stateValue.standing != null
        ) {
            return
        }
        updateState { copy(ratingId = ratingId, groupId = groupId, participantKey = participantKey) }
        reload()
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            is AthleteStartsAction.BackClick -> viewModelScope.launch { navigation.back() }
            is AthleteStartsAction.StartClick -> {
                viewModelScope.launch {
                    navigation.navigate(EventsNavigation.EventResultsRoute(action.competitionId))
                }
            }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            val standingsResult = ratingRepository.getStandings(stateValue.ratingId, stateValue.groupId)
            val competitionsResult = ratingRepository.listCompetitions(stateValue.ratingId)

            val failure = standingsResult.exceptionOrNull() ?: competitionsResult.exceptionOrNull()
            if (failure != null) {
                updateState { copy(isLoading = false) }
                emitNetworkError(failure)
                return@launch
            }

            val standing = standingsResult.getOrDefault(emptyList())
                .firstOrNull { it.participantKey == stateValue.participantKey }

            updateState {
                copy(
                    standing = standing,
                    competitions = competitionsResult.getOrDefault(emptyList()),
                    isLoading = false
                )
            }
        }
    }

    private suspend fun emitNetworkError(throwable: Throwable) {
        val code = (throwable as? NetworkException)?.code
        networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
    }
}
