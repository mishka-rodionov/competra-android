package com.competra.eventdetails.presentation.result_links

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.data.navigation.EventsNavigation
import com.competra.data.navigation.Navigation
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.participant_link.LinkRequest
import com.competra.domain.models.participant_link.LinkRequestSource
import com.competra.domain.models.participant_link.LinkSuggestion
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.participant_link.ParticipantLinkRepository
import com.competra.ui.BaseAction
import com.competra.ui.BaseState
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Состояние экрана «Мои результаты в протоколах».
 *
 * @property selectedIds Отмеченные подсказки (participantId) для отправки заявки.
 */
data class ResultLinksState(
    val isLoading: Boolean = true,
    val suggestions: List<LinkSuggestion> = emptyList(),
    val requests: List<LinkRequest> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isConfirmShown: Boolean = false,
    val isSending: Boolean = false,
    val cancellingId: String? = null,
) : BaseState {
    /** Выбранные подсказки, которые ещё есть в списке (после отправки список обновляется). */
    val selected: List<LinkSuggestion> get() = suggestions.filter { it.participantId in selectedIds }
}

/** Действия на экране «Мои результаты в протоколах». */
sealed interface ResultLinksAction : BaseAction {
    data object Back : ResultLinksAction
    data class ToggleSelection(val participantId: String) : ResultLinksAction
    data object ToggleSelectAll : ResultLinksAction
    data object ShowConfirm : ResultLinksAction
    data object HideConfirm : ResultLinksAction
    data object SendRequests : ResultLinksAction
    data class CancelRequest(val request: LinkRequest) : ResultLinksAction
    data class OpenCompetition(val competitionId: String) : ResultLinksAction
}

/**
 * Результаты, которые организатор внёс вручную (без привязки к аккаунту): подсказки по имени
 * из профиля и заявки пользователя на привязку с их статусами.
 */
class ResultLinksViewModel(
    private val repository: ParticipantLinkRepository,
    private val navigation: Navigation,
    private val networkErrorRepository: NetworkErrorRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<ResultLinksState>(ResultLinksState()) {

    private var viewTracked = false

    init {
        load()
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            ResultLinksAction.Back -> viewModelScope.launch { navigation.back() }
            is ResultLinksAction.ToggleSelection -> updateState {
                val next = if (action.participantId in selectedIds) selectedIds - action.participantId
                else selectedIds + action.participantId
                copy(selectedIds = next)
            }
            ResultLinksAction.ToggleSelectAll -> updateState {
                val all = suggestions.map { it.participantId }.toSet()
                copy(selectedIds = if (selectedIds.containsAll(all)) emptySet() else all)
            }
            ResultLinksAction.ShowConfirm -> if (stateValue.selected.isNotEmpty()) updateState { copy(isConfirmShown = true) }
            ResultLinksAction.HideConfirm -> updateState { copy(isConfirmShown = false) }
            ResultLinksAction.SendRequests -> sendRequests()
            is ResultLinksAction.CancelRequest -> cancelRequest(action.request)
            is ResultLinksAction.OpenCompetition -> viewModelScope.launch {
                navigation.navigate(EventsNavigation.EventDetailsRoute(eventId = action.competitionId))
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val suggestionsDeferred = async { repository.getSuggestions() }
            val requestsDeferred = async { repository.getMyRequests() }
            val suggestions = suggestionsDeferred.await()
            val requests = requestsDeferred.await()
            suggestions.exceptionOrNull()?.let(::emitError)
            requests.exceptionOrNull()?.let(::emitError)
            val suggestionList = suggestions.getOrDefault(emptyList())
            updateState {
                copy(
                    isLoading = false,
                    suggestions = suggestionList,
                    requests = requests.getOrDefault(emptyList()),
                )
            }
            if (!viewTracked && suggestions.isSuccess) {
                viewTracked = true
                analytics.trackEvent(AnalyticsEvent.ResultLinkSuggestionsViewed(suggestionList.size))
            }
        }
    }

    private fun sendRequests() {
        val selected = stateValue.selected
        if (selected.isEmpty()) return
        viewModelScope.launch {
            updateState { copy(isSending = true) }
            repository.createRequests(selected.map { it.participantId }, LinkRequestSource.SUGGESTION)
                .onSuccess {
                    selected.groupingBy { it.competitionId }.eachCount().forEach { (competitionId, count) ->
                        analytics.trackEvent(
                            AnalyticsEvent.ResultLinkRequested(competitionId, AnalyticsEvent.ResultLinkSource.SUGGESTION, count)
                        )
                    }
                    updateState { copy(isSending = false, isConfirmShown = false, selectedIds = emptySet()) }
                    load()
                }
                .onFailure {
                    updateState { copy(isSending = false, isConfirmShown = false) }
                    emitError(it)
                }
        }
    }

    private fun cancelRequest(request: LinkRequest) {
        viewModelScope.launch {
            updateState { copy(cancellingId = request.id) }
            repository.cancelRequest(request.id)
                .onSuccess {
                    analytics.trackEvent(AnalyticsEvent.ResultLinkRequestCancelled(request.competitionId))
                    load()
                }
                .onFailure(::emitError)
            updateState { copy(cancellingId = null) }
        }
    }

    private fun emitError(throwable: Throwable) {
        viewModelScope.launch {
            val code = (throwable as? NetworkException)?.code
            networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
        }
    }
}
