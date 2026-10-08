package com.competra.center.data.results

import com.competra.domain.models.orienteering.ByChoiceMode
import com.competra.domain.models.orienteering.TeamStandings
import com.competra.domain.models.orienteering.GroupWithParticipantsAndResults
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.ResultsStatus
import com.competra.ui.BaseState

data class OrienteeringCompetitionResultsState(
    val groupsWithParticipantsAndResults: List<GroupWithParticipantsAndResults> = emptyList(),
    val direction: OrienteeringDirection = OrienteeringDirection.FORWARD,
    /** Итог формата «по выбору»: по баллам или по минимуму КП (места по времени, баллов нет). */
    val byChoiceMode: ByChoiceMode = ByChoiceMode.DEFAULT,
    /** Командный зачёт, посчитанный из текущих результатов; null — зачёт в соревновании не включён. */
    val teamStandings: TeamStandings? = null,
    /** Показывается командный зачёт вместо личных результатов. */
    val showTeamStandings: Boolean = false,
    val isApproved: Boolean = false,
    /** Соревнование уже завершено — кнопка "Утвердить результаты" больше не показывается. */
    val isCompetitionFinished: Boolean = false,
    val competitionTitle: String = "",
    val isPublishingHtml: Boolean = false,
    val publishedHtmlUrl: String? = null,
    val importDiff: ImportResultsDiff? = null,
    val isImporting: Boolean = false,
    val importError: String? = null,
    /** Статус публикации результатов участникам (push-уведомление шлётся при переходе в PRELIMINARY/OFFICIAL). */
    val resultsStatus: ResultsStatus = ResultsStatus.NOT_PUBLISHED,
    val isPublishingResults: Boolean = false,
    val isShowPublishResultsConfirm: Boolean = false,
): BaseState