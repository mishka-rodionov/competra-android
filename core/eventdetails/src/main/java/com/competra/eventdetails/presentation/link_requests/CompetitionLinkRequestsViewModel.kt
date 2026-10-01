package com.competra.eventdetails.presentation.link_requests

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.data.navigation.Navigation
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.participant_link.CompetitionLinkRequest
import com.competra.domain.models.participant_link.LinkRequestStatus
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.participant_link.ParticipantLinkRepository
import com.competra.ui.BaseAction
import com.competra.ui.BaseState
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

/**
 * Состояние экрана заявок на привязку результатов по соревнованию.
 *
 * @property rejectingId Заявка, для которой открыт ввод причины отклонения.
 * @property errors Ошибки по заявкам (id → текст сервера), показываются под строкой.
 */
data class CompetitionLinkRequestsState(
    val competitionId: String = "",
    val isLoading: Boolean = true,
    val requests: List<CompetitionLinkRequest> = emptyList(),
    val isBusy: Boolean = false,
    val rejectingId: String? = null,
    val rejectComment: String = "",
    val unlinkingParticipantId: String? = null,
    val errors: Map<String, String> = emptyMap(),
) : BaseState {
    val pending: List<CompetitionLinkRequest> get() = requests.filter { it.status == LinkRequestStatus.PENDING }
    val processed: List<CompetitionLinkRequest> get() = requests.filter { it.status != LinkRequestStatus.PENDING }
}

/** Действия на экране заявок на привязку. */
sealed interface CompetitionLinkRequestsAction : BaseAction {
    data object Back : CompetitionLinkRequestsAction
    data class Approve(val requests: List<CompetitionLinkRequest>) : CompetitionLinkRequestsAction
    data class StartReject(val requestId: String) : CompetitionLinkRequestsAction
    data class UpdateRejectComment(val value: String) : CompetitionLinkRequestsAction
    data object ConfirmReject : CompetitionLinkRequestsAction
    data object CancelReject : CompetitionLinkRequestsAction
    data class AskUnlink(val participantId: String) : CompetitionLinkRequestsAction
    data object ConfirmUnlink : CompetitionLinkRequestsAction
    data object CancelUnlink : CompetitionLinkRequestsAction
}

/**
 * Заявки спортсменов «этот участник протокола — я». Одобрение записывает аккаунт в участника:
 * результат попадает в профиль спортсмена и склеивается с его стартами в рейтингах.
 */
class CompetitionLinkRequestsViewModel(
    private val repository: ParticipantLinkRepository,
    private val navigation: Navigation,
    private val networkErrorRepository: NetworkErrorRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<CompetitionLinkRequestsState>(CompetitionLinkRequestsState()) {

    fun initialize(competitionId: String) {
        if (stateValue.competitionId == competitionId) return
        updateState { copy(competitionId = competitionId) }
        reload()
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            CompetitionLinkRequestsAction.Back -> viewModelScope.launch { navigation.back() }
            is CompetitionLinkRequestsAction.Approve -> review(action.requests, approve = true, comment = null)
            is CompetitionLinkRequestsAction.StartReject ->
                updateState { copy(rejectingId = action.requestId, rejectComment = "") }
            is CompetitionLinkRequestsAction.UpdateRejectComment ->
                updateState { copy(rejectComment = action.value.take(COMMENT_MAX_LENGTH)) }
            CompetitionLinkRequestsAction.ConfirmReject -> {
                val target = stateValue.requests.firstOrNull { it.id == stateValue.rejectingId } ?: return
                review(listOf(target), approve = false, comment = stateValue.rejectComment.trim().ifBlank { null })
            }
            CompetitionLinkRequestsAction.CancelReject -> updateState { copy(rejectingId = null) }
            is CompetitionLinkRequestsAction.AskUnlink -> updateState { copy(unlinkingParticipantId = action.participantId) }
            CompetitionLinkRequestsAction.ConfirmUnlink -> unlink()
            CompetitionLinkRequestsAction.CancelUnlink -> updateState { copy(unlinkingParticipantId = null) }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            repository.getCompetitionRequests(stateValue.competitionId)
                .onSuccess { requests -> updateState { copy(isLoading = false, requests = requests) } }
                .onFailure {
                    updateState { copy(isLoading = false) }
                    emitError(it)
                }
        }
    }

    /** Последовательно: одобрение одного участника может автоматически отклонить конкурирующую заявку. */
    private fun review(targets: List<CompetitionLinkRequest>, approve: Boolean, comment: String?) {
        viewModelScope.launch {
            updateState { copy(isBusy = true, errors = emptyMap()) }
            val errors = mutableMapOf<String, String>()
            for (request in targets) {
                repository.reviewRequest(request.id, approve, comment)
                    .onSuccess {
                        analytics.trackEvent(AnalyticsEvent.ResultLinkRequestReviewed(stateValue.competitionId, approve))
                    }
                    .onFailure { errors[request.id] = it.message ?: "Не удалось обработать заявку" }
            }
            updateState { copy(isBusy = false, rejectingId = null, rejectComment = "", errors = errors) }
            reload()
        }
    }

    private fun unlink() {
        val participantId = stateValue.unlinkingParticipantId ?: return
        viewModelScope.launch {
            updateState { copy(isBusy = true) }
            repository.unlinkParticipant(participantId)
                .onSuccess {
                    analytics.trackEvent(
                        AnalyticsEvent.ResultUnlinked(stateValue.competitionId, AnalyticsEvent.ResultUnlinkedBy.ORGANIZER)
                    )
                }
                .onFailure(::emitError)
            updateState { copy(isBusy = false, unlinkingParticipantId = null) }
            reload()
        }
    }

    private fun emitError(throwable: Throwable) {
        viewModelScope.launch {
            val code = (throwable as? NetworkException)?.code
            networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
        }
    }

    private companion object {
        const val COMMENT_MAX_LENGTH = 500
    }
}
