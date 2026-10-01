package com.competra.eventdetails.presentation.results

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.data.navigation.EventsNavigation
import com.competra.data.navigation.Navigation
import com.competra.domain.models.orienteering.GroupWithParticipantsAndResults
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.ParticipantWithResult
import com.competra.domain.models.orienteering.sortedForResults
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.participant_link.LinkRequest
import com.competra.domain.models.participant_link.LinkRequestSource
import com.competra.domain.models.participant_link.LinkRequestStatus
import com.competra.domain.models.participant_link.LinkSuggestion
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.orienteering.OrienteeringCompetitionRemoteRepository
import com.competra.domain.repository.participant_link.ParticipantLinkRepository
import com.competra.domain.repository.user.UserRepository
import com.competra.ui.BaseAction
import com.competra.ui.BaseState
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class EventResultsState(
    val isLoading: Boolean = true,
    val groupsWithResults: List<GroupWithParticipantsAndResults> = emptyList(),
    val selectedParticipant: ParticipantWithResult? = null,
    val direction: OrienteeringDirection = OrienteeringDirection.FORWARD,
    /** Привязка ручных результатов к аккаунту — только для авторизованного пользователя. */
    val link: ResultLinkState = ResultLinkState(),
) : BaseState

/**
 * Привязка вручную внесённых результатов к аккаунту на экране результатов.
 *
 * @property suggestion Подсказка по имени среди участников этого соревнования.
 * @property pendingRequest Заявка пользователя на рассмотрении в этом соревновании.
 * @property confirmTarget Участник, для которого открыто подтверждение заявки.
 */
data class ResultLinkState(
    val currentUserId: String? = null,
    val suggestion: LinkSuggestion? = null,
    val pendingRequest: LinkRequest? = null,
    val confirmTarget: LinkTarget? = null,
    val isSending: Boolean = false,
    val unlinkParticipantId: String? = null,
)

/** Кандидат на привязку: участник, его описание для подтверждения и откуда он взят. */
data class LinkTarget(val participantId: String, val label: String, val source: LinkRequestSource)

sealed interface EventResultsAction : BaseAction {
    data class ShowSplits(val participant: ParticipantWithResult) : EventResultsAction
    data object HideSplits : EventResultsAction
    data class OpenGroupSplitsTable(val eventId: String, val groupId: Long) : EventResultsAction
    data class OpenRaceGraph(val eventId: String, val groupId: Long) : EventResultsAction
    data class OpenScoreGraph(val eventId: String, val groupId: Long) : EventResultsAction
    data class RequestLink(val target: LinkTarget) : EventResultsAction
    data object ConfirmLink : EventResultsAction
    data object DismissLink : EventResultsAction
    data class AskUnlink(val participantId: String) : EventResultsAction
    data object ConfirmUnlink : EventResultsAction
    data object CancelUnlink : EventResultsAction
}

class EventResultsViewModel(
    private val remoteRepository: OrienteeringCompetitionRemoteRepository,
    private val analytics: AnalyticsTracker,
    private val navigation: Navigation,
    private val participantLinkRepository: ParticipantLinkRepository,
    private val userRepository: UserRepository,
    private val networkErrorRepository: NetworkErrorRepository,
) : BaseViewModel<EventResultsState>(EventResultsState()) {

    private var eventId: String? = null

    override fun onAction(action: BaseAction) {
        when (action) {
            is EventResultsAction.ShowSplits -> updateState { copy(selectedParticipant = action.participant) }
            is EventResultsAction.HideSplits -> updateState { copy(selectedParticipant = null) }
            is EventResultsAction.OpenGroupSplitsTable -> openGroupSplitsTable(action.eventId, action.groupId)
            is EventResultsAction.OpenRaceGraph -> openRaceGraph(action.eventId, action.groupId)
            is EventResultsAction.OpenScoreGraph -> openScoreGraph(action.eventId, action.groupId)
            is EventResultsAction.RequestLink -> updateLink { copy(confirmTarget = action.target) }
            EventResultsAction.ConfirmLink -> sendLinkRequest()
            EventResultsAction.DismissLink -> updateLink { copy(confirmTarget = null) }
            is EventResultsAction.AskUnlink -> updateLink { copy(unlinkParticipantId = action.participantId) }
            EventResultsAction.ConfirmUnlink -> unlink()
            EventResultsAction.CancelUnlink -> updateLink { copy(unlinkParticipantId = null) }
        }
    }

    private fun updateLink(block: ResultLinkState.() -> ResultLinkState) = updateState { copy(link = link.block()) }

    /**
     * Подсказка по имени и заявка на рассмотрении в этом соревновании. Незалогиненному пользователю
     * не нужны — [UserRepository.retrieveUser] вернёт ошибку, и блок просто не покажется.
     */
    private fun loadLinkState(eventId: String) {
        viewModelScope.launch {
            val user = userRepository.retrieveUser().getOrNull() ?: return@launch
            val suggestion = participantLinkRepository.getSuggestions().getOrNull()
                ?.firstOrNull { it.competitionId == eventId }
            val pending = participantLinkRepository.getMyRequests().getOrNull()
                ?.firstOrNull { it.competitionId == eventId && it.status == LinkRequestStatus.PENDING }
            updateLink { copy(currentUserId = user.id, suggestion = suggestion, pendingRequest = pending) }
        }
    }

    private fun sendLinkRequest() {
        val target = stateValue.link.confirmTarget ?: return
        val id = eventId ?: return
        viewModelScope.launch {
            updateLink { copy(isSending = true) }
            participantLinkRepository.createRequests(listOf(target.participantId), target.source)
                .onSuccess {
                    val source = if (target.source == LinkRequestSource.SUGGESTION) {
                        AnalyticsEvent.ResultLinkSource.SUGGESTION
                    } else {
                        AnalyticsEvent.ResultLinkSource.MANUAL
                    }
                    analytics.trackEvent(AnalyticsEvent.ResultLinkRequested(id, source, 1))
                    updateLink { copy(isSending = false, confirmTarget = null) }
                    // Организатор, заявивший свой же результат, получает автоодобрение — участник меняется сразу.
                    loadResults(id, showLoading = false)
                }
                .onFailure {
                    updateLink { copy(isSending = false, confirmTarget = null) }
                    emitError(it)
                }
        }
    }

    private fun unlink() {
        val participantId = stateValue.link.unlinkParticipantId ?: return
        val id = eventId ?: return
        viewModelScope.launch {
            participantLinkRepository.unlinkParticipant(participantId)
                .onSuccess {
                    analytics.trackEvent(AnalyticsEvent.ResultUnlinked(id, AnalyticsEvent.ResultUnlinkedBy.SELF))
                    updateState { copy(selectedParticipant = null) }
                    loadResults(id, showLoading = false)
                }
                .onFailure(::emitError)
            updateLink { copy(unlinkParticipantId = null) }
        }
    }

    private fun emitError(throwable: Throwable) {
        viewModelScope.launch {
            val code = (throwable as? NetworkException)?.code
            networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
        }
    }

    private fun openGroupSplitsTable(eventId: String, groupId: Long) {
        viewModelScope.launch {
            navigation.navigate(EventsNavigation.EventSplitsTableRoute(eventId, groupId))
        }
    }

    private fun openRaceGraph(eventId: String, groupId: Long) {
        viewModelScope.launch {
            navigation.navigate(EventsNavigation.EventRaceGraphRoute(eventId, groupId))
        }
    }

    private fun openScoreGraph(eventId: String, groupId: Long) {
        viewModelScope.launch {
            navigation.navigate(EventsNavigation.EventScoreGraphRoute(eventId, groupId))
        }
    }

    /**
     * @param showLoading false — тихое обновление после заявки/отвязки: без полноэкранного индикатора,
     *   иначе пересоздаётся пейджер групп и пользователь теряет открытую вкладку.
     */
    fun loadResults(eventId: String, showLoading: Boolean = true) {
        if (this.eventId != eventId) analytics.trackEvent(AnalyticsEvent.ResultsViewed(eventId))
        this.eventId = eventId
        loadLinkState(eventId)
        viewModelScope.launch(Dispatchers.IO) {
            if (showLoading) updateState { copy(isLoading = true) }

            val competitionDeferred = async { remoteRepository.getCompetitionById(eventId).getOrNull() }
            val groupsDeferred = async { remoteRepository.getCompetitionParticipantsGroups(eventId).getOrNull() ?: emptyList() }
            val participantsDeferred = async { remoteRepository.getParticipantsForCompetition(eventId).getOrNull() ?: emptyList() }
            val resultsDeferred = async { remoteRepository.getResultsByCompetition(eventId).getOrNull() ?: emptyList() }

            val direction = competitionDeferred.await()?.direction ?: OrienteeringDirection.FORWARD
            val groups = groupsDeferred.await()
            val participants = participantsDeferred.await()
            val results = resultsDeferred.await()

            val resultsByParticipant = results.associateBy { it.participantId }
            val participantsByGroup = participants.groupBy { it.groupId }

            val groupsWithResults = groups.map { group ->
                val groupParticipants = participantsByGroup[group.remoteId] ?: emptyList()
                val participantsWithResults = groupParticipants.map { participant ->
                    ParticipantWithResult(
                        participant = participant,
                        result = resultsByParticipant[participant.id]
                    )
                }.sortedForResults(direction)
                GroupWithParticipantsAndResults(group = group, participants = participantsWithResults)
            }

            updateState {
                // Открытый шит участника — на свежие данные (после привязки у участника меняется userId).
                val refreshedSelection = selectedParticipant?.let { selected ->
                    groupsWithResults.asSequence().flatMap { it.participants }
                        .firstOrNull { it.participant.id == selected.participant.id }
                }
                copy(
                    isLoading = false,
                    groupsWithResults = groupsWithResults,
                    direction = direction,
                    selectedParticipant = refreshedSelection
                )
            }
        }
    }
}
