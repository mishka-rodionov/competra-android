package com.competra.center.presentation.group_splits

import androidx.lifecycle.viewModelScope
import com.competra.domain.models.orienteering.effectiveFormat
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.center.data.group_splits.GroupSplitsTableState
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.domain.models.orienteering.ByChoiceMode
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.buildSplitsTable
import com.competra.domain.models.orienteering.sortedForResults
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GroupSplitsTableViewModel(
    private val interactor: OrienteeringCompetitionInteractor,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<GroupSplitsTableState>(GroupSplitsTableState()) {

    override fun onAction(action: BaseAction) {}

    fun load(groupId: Long, competitionId: String) {
        analytics.trackEvent(AnalyticsEvent.GroupSplitsTableOpened(groupId, competitionId))
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
            val sortedGroup = group.copy(participants = group.participants.sortedForResults(format))
            val table = buildSplitsTable(sortedGroup, distance, direction)
            updateState {
                copy(
                    groupTitle = sortedGroup.group.title,
                    table = table,
                    direction = direction,
                    byChoiceMode = format.byChoiceMode,
                    isLoading = false
                )
            }
        }
    }
}
