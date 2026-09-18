package com.competra.rating.data.detail

import com.competra.ui.BaseAction

sealed class RatingDetailAction : BaseAction {
    data object BackClick : RatingDetailAction()
    data class SelectGroup(val groupId: Long) : RatingDetailAction()
    data class StandingClick(val participantKey: String) : RatingDetailAction()
    data object AddCompetitionClick : RatingDetailAction()
    data class RemoveCompetitionClick(val competitionId: String) : RatingDetailAction()
    data class EditMappingClick(val competitionId: String) : RatingDetailAction()
    data object OpenDeleteConfirm : RatingDetailAction()
    data object CloseDeleteConfirm : RatingDetailAction()
    data object ConfirmDelete : RatingDetailAction()
}
