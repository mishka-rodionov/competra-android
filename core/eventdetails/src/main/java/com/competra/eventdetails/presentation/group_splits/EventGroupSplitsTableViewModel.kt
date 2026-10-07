package com.competra.eventdetails.presentation.group_splits

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.ByChoiceMode
import com.competra.domain.models.orienteering.GroupWithParticipantsAndResults
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.ParticipantWithResult
import com.competra.domain.models.orienteering.buildSplitsTable
import com.competra.domain.models.orienteering.sortedForResults
import com.competra.domain.repository.orienteering.OrienteeringCompetitionRemoteRepository
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class EventGroupSplitsTableViewModel(
    private val remoteRepository: OrienteeringCompetitionRemoteRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<EventGroupSplitsTableState>(EventGroupSplitsTableState()) {

    override fun onAction(action: BaseAction) {}

    /**
     * [groupId] здесь — это [ParticipantGroup.remoteId], а не [ParticipantGroup.groupId]:
     * в этом remote-only потоке (публичный просмотр события) локальный groupId не заполняется
     * маппером ответа сервера (`ParticipantGroupResponse.toDomain()` всегда ставит 0) — реальный
     * уникальный идентификатор группы приходит только в remoteId. См. тот же паттерн
     * в [com.competra.eventdetails.presentation.results.EventResultsViewModel.loadResults].
     */
    fun load(eventId: String, groupId: Long) {
        analytics.trackEvent(AnalyticsEvent.GroupSplitsTableOpened(groupId, eventId))
        viewModelScope.launch(Dispatchers.IO) {
            val groupsDeferred = async { remoteRepository.getCompetitionParticipantsGroups(eventId).getOrNull() ?: emptyList() }
            val participantsDeferred = async { remoteRepository.getParticipantsForCompetition(eventId).getOrNull() ?: emptyList() }
            val resultsDeferred = async { remoteRepository.getResultsByCompetition(eventId).getOrNull() ?: emptyList() }
            val distancesDeferred = async { remoteRepository.getDistancesForCompetition(eventId).getOrNull() ?: emptyList() }
            val competitionDeferred = async { remoteRepository.getCompetitionById(eventId).getOrNull() }

            val group = groupsDeferred.await().firstOrNull { it.remoteId == groupId }
            if (group == null) {
                updateState { copy(isLoading = false) }
                return@launch
            }

            val participants = participantsDeferred.await()
            val results = resultsDeferred.await()
            val distances = distancesDeferred.await()
            val competition = competitionDeferred.await()
            val direction = competition?.direction ?: OrienteeringDirection.FORWARD
            val byChoiceMode = competition?.byChoiceMode ?: ByChoiceMode.DEFAULT
            // distanceId группы — это серверный id дистанции, а он хранится в domain-модели
            // Distance.remoteId (см. DistanceResponse.toDomain), а не в Distance.id.
            val distance = distances.firstOrNull { it.remoteId == group.distanceId }

            val resultsByParticipant = results.associateBy { it.participantId }
            val groupParticipants = participants.filter { it.groupId == group.remoteId }
            val participantsWithResults = groupParticipants.map { participant ->
                ParticipantWithResult(
                    participant = participant,
                    result = resultsByParticipant[participant.id]
                )
            }.sortedForResults(direction)

            val table = buildSplitsTable(
                GroupWithParticipantsAndResults(group = group, participants = participantsWithResults),
                distance,
                direction,
            )
            updateState {
                copy(groupTitle = group.title, table = table, direction = direction, byChoiceMode = byChoiceMode, isLoading = false)
            }
        }
    }
}
