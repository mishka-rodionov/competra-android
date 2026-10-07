package com.competra.eventdetails.presentation.live_results

import androidx.lifecycle.viewModelScope
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.ByChoiceMode
import com.competra.domain.models.orienteering.GroupWithParticipantsAndResults
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.ParticipantWithResult
import com.competra.domain.models.orienteering.sortedForResults
import com.competra.domain.repository.orienteering.OrienteeringCompetitionRemoteRepository
import com.competra.ui.BaseAction
import com.competra.ui.BaseState
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REFRESH_INTERVAL_MS = 30_000L

data class LiveResultsState(
    val groupsWithResults: List<GroupWithParticipantsAndResults> = emptyList(),
    val isLoading: Boolean = true,
    val lastUpdated: Long? = null,
    val selectedParticipant: ParticipantWithResult? = null,
    val direction: OrienteeringDirection = OrienteeringDirection.FORWARD,
    val byChoiceMode: ByChoiceMode = ByChoiceMode.DEFAULT
) : BaseState

sealed interface LiveResultsAction : BaseAction {
    data class ShowSplits(val participant: ParticipantWithResult) : LiveResultsAction
    data object HideSplits : LiveResultsAction
}

/**
 * ViewModel экрана онлайн-результатов соревнования.
 * Автоматически обновляет данные каждые [REFRESH_INTERVAL_MS] мс.
 */
class LiveResultsViewModel(
    private val remoteRepository: OrienteeringCompetitionRemoteRepository
) : BaseViewModel<LiveResultsState>(LiveResultsState()) {

    override fun onAction(action: BaseAction) {
        when (action) {
            is LiveResultsAction.ShowSplits -> updateState { copy(selectedParticipant = action.participant) }
            is LiveResultsAction.HideSplits -> updateState { copy(selectedParticipant = null) }
        }
    }

    fun startPolling(eventId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                loadResults(eventId)
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun loadResults(eventId: String) {
        val competitionDeferred = viewModelScope.async(Dispatchers.IO) {
            remoteRepository.getCompetitionById(eventId).getOrNull()
        }
        val groupsDeferred = viewModelScope.async(Dispatchers.IO) {
            remoteRepository.getCompetitionParticipantsGroups(eventId).getOrNull() ?: emptyList()
        }
        val participantsDeferred = viewModelScope.async(Dispatchers.IO) {
            remoteRepository.getParticipantsForCompetition(eventId).getOrNull() ?: emptyList()
        }
        val resultsDeferred = viewModelScope.async(Dispatchers.IO) {
            remoteRepository.getResultsByCompetition(eventId).getOrNull() ?: emptyList()
        }

        val competition = competitionDeferred.await()
        val direction = competition?.direction ?: OrienteeringDirection.FORWARD
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
            copy(
                groupsWithResults = groupsWithResults,
                isLoading = false,
                lastUpdated = System.currentTimeMillis(),
                direction = direction,
                byChoiceMode = competition?.byChoiceMode ?: ByChoiceMode.DEFAULT
            )
        }
    }
}
