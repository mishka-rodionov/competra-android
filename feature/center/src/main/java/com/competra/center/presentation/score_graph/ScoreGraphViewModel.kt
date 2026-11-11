package com.competra.center.presentation.score_graph

import androidx.lifecycle.viewModelScope
import com.competra.domain.models.orienteering.effectiveFormat
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.center.data.score_graph.SCORE_GRAPH_DEFAULT_VISIBLE_COUNT
import com.competra.center.data.score_graph.ScoreGraphState
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.buildScoreGraphData
import com.competra.domain.models.orienteering.sortedForResults
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScoreGraphViewModel(
    private val interactor: OrienteeringCompetitionInteractor,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<ScoreGraphState>(ScoreGraphState()) {

    override fun onAction(action: BaseAction) {}

    fun load(groupId: Long, competitionId: String) {
        analytics.trackEvent(AnalyticsEvent.ScoreGraphOpened(groupId, competitionId))
        viewModelScope.launch(Dispatchers.IO) {
            val competition = interactor.getCompetition(competitionId)
            val group = interactor.getResultsByGroupsAuto(competitionId)
                .firstOrNull { it.group.groupId == groupId }

            if (group == null) {
                updateState { copy(isLoading = false) }
                return@launch
            }

            val distance = interactor.getDistanceById(group.group.distanceId).getOrNull()
            // Формат группы — по её дистанции, иначе соревнования.
            val format = distance.effectiveFormat(competition)
            val direction = format.direction
            val sortedGroup = group.copy(participants = group.participants.sortedForResults(direction))
            val scoreGraphData = buildScoreGraphData(sortedGroup, distance)

            val defaultVisible = scoreGraphData.series
                .sortedBy { it.result?.rank ?: Int.MAX_VALUE }
                .take(SCORE_GRAPH_DEFAULT_VISIBLE_COUNT)
                .map { it.participant.id }
                .toSet()

            updateState {
                copy(
                    groupTitle = sortedGroup.group.title,
                    data = scoreGraphData,
                    visibleParticipantIds = defaultVisible,
                    isLoading = false,
                )
            }
        }
    }

    fun toggleParticipantVisibility(participantId: String) {
        updateState {
            val visible = if (participantId in visibleParticipantIds) {
                visibleParticipantIds - participantId
            } else {
                visibleParticipantIds + participantId
            }
            copy(visibleParticipantIds = visible)
        }
    }

    fun toggleHighlight(participantId: String) {
        updateState {
            copy(highlightedParticipantId = if (highlightedParticipantId == participantId) null else participantId)
        }
    }
}
