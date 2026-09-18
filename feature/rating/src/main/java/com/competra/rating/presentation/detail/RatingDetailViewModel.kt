package com.competra.rating.presentation.detail

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.RatingNavigation
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.club.ClubRole
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.clubs.ClubRepository
import com.competra.domain.repository.rating.RatingRepository
import com.competra.domain.repository.user.UserRepository
import com.competra.rating.data.detail.RatingDetailAction
import com.competra.rating.data.detail.RatingDetailState
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

class RatingDetailViewModel(
    private val ratingRepository: RatingRepository,
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val navigation: Navigation,
    private val networkErrorRepository: NetworkErrorRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<RatingDetailState>(RatingDetailState()) {

    fun initialize(ratingId: String) {
        if (stateValue.ratingId == ratingId && stateValue.rating != null) return
        updateState { copy(ratingId = ratingId) }
        reload()
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            is RatingDetailAction.BackClick -> viewModelScope.launch { navigation.back() }
            is RatingDetailAction.SelectGroup -> {
                updateState { copy(selectedGroupId = action.groupId) }
                loadStandings(action.groupId)
            }
            is RatingDetailAction.StandingClick -> {
                val groupId = stateValue.selectedGroupId ?: return
                viewModelScope.launch {
                    navigation.navigate(
                        RatingNavigation.AthleteStartsRoute(stateValue.ratingId, groupId, action.participantKey)
                    )
                }
            }
            is RatingDetailAction.AddCompetitionClick -> {
                viewModelScope.launch {
                    navigation.navigate(RatingNavigation.AddCompetitionRoute(stateValue.ratingId))
                }
            }
            is RatingDetailAction.RemoveCompetitionClick -> removeCompetition(action.competitionId)
            is RatingDetailAction.EditMappingClick -> {
                viewModelScope.launch {
                    navigation.navigate(
                        RatingNavigation.GroupMappingRoute(stateValue.ratingId, action.competitionId)
                    )
                }
            }
            is RatingDetailAction.OpenDeleteConfirm -> updateState { copy(isDeleteConfirmOpen = true) }
            is RatingDetailAction.CloseDeleteConfirm -> updateState { copy(isDeleteConfirmOpen = false) }
            is RatingDetailAction.ConfirmDelete -> confirmDelete()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            // Состав рейтинга мог измениться (удалено соревнование) — старый кэш standings невалиден.
            updateState { copy(isLoading = true, standingsByGroup = emptyMap()) }
            val ratingId = stateValue.ratingId
            val userId = userRepository.retrieveUser().getOrNull()?.id

            val ratingResult = ratingRepository.getById(ratingId)
            val competitionsResult = ratingRepository.listCompetitions(ratingId)

            val failure = ratingResult.exceptionOrNull() ?: competitionsResult.exceptionOrNull()
            if (failure != null) {
                updateState { copy(isLoading = false) }
                emitNetworkError(failure)
                return@launch
            }

            val rating = ratingResult.getOrNull()
            val isAdmin = if (userId != null && rating != null) {
                clubRepository.getMembers(rating.ownerClubId).getOrDefault(emptyList())
                    .any { it.userId == userId && it.role in listOf(ClubRole.FOUNDER, ClubRole.ADMIN) }
            } else {
                false
            }
            val selectedGroupId = stateValue.selectedGroupId ?: rating?.groups?.firstOrNull()?.id

            updateState {
                copy(
                    rating = rating,
                    competitions = competitionsResult.getOrDefault(emptyList()),
                    isAdmin = isAdmin,
                    selectedGroupId = selectedGroupId,
                    isLoading = false
                )
            }
            // forceReload=true: reload() уже очистил кэш выше, обходить его смысла нет.
            if (selectedGroupId != null) loadStandings(selectedGroupId, forceReload = true)
        }
    }

    /** Переключение вкладок группы использует кэш [RatingDetailState.standingsByGroup] на время жизни экрана. */
    private fun loadStandings(groupId: Long, forceReload: Boolean = false) {
        val cached = stateValue.standingsByGroup[groupId]
        if (!forceReload && cached != null) {
            updateState { copy(standings = cached) }
            return
        }
        viewModelScope.launch {
            updateState { copy(isStandingsLoading = true) }
            ratingRepository.getStandings(stateValue.ratingId, groupId)
                .onSuccess { standings ->
                    updateState {
                        copy(
                            standings = standings,
                            standingsByGroup = standingsByGroup + (groupId to standings),
                            isStandingsLoading = false
                        )
                    }
                }
                .onFailure { throwable ->
                    updateState { copy(isStandingsLoading = false) }
                    emitNetworkError(throwable)
                }
        }
    }

    private fun removeCompetition(competitionId: String) {
        viewModelScope.launch {
            ratingRepository.removeCompetition(stateValue.ratingId, competitionId)
                .onSuccess { reload() }
                .onFailure { emitNetworkError(it) }
        }
    }

    private fun confirmDelete() {
        val ratingId = stateValue.ratingId
        viewModelScope.launch {
            updateState { copy(isDeleting = true) }
            ratingRepository.delete(ratingId)
                .onSuccess {
                    analytics.trackEvent(AnalyticsEvent.RatingDeleted(ratingId))
                    updateState { copy(isDeleting = false, isDeleteConfirmOpen = false) }
                    navigation.back()
                }
                .onFailure { throwable ->
                    updateState { copy(isDeleting = false) }
                    emitNetworkError(throwable)
                }
        }
    }

    private suspend fun emitNetworkError(throwable: Throwable) {
        val code = (throwable as? NetworkException)?.code
        networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
    }
}
