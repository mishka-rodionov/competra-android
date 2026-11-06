package com.competra.rating.data.detail

import com.competra.ui.BaseAction

sealed class RatingDetailAction : BaseAction {
    data object BackClick : RatingDetailAction()
    data class SelectGroup(val groupId: Long) : RatingDetailAction()
    /** Клик по строке: переход к стартам спортсмена или, в режиме выбора, отметка строки. */
    data class StandingClick(val participantKey: String) : RatingDetailAction()
    data object AddCompetitionClick : RatingDetailAction()
    data class RemoveCompetitionClick(val competitionId: String) : RatingDetailAction()
    data class EditMappingClick(val competitionId: String) : RatingDetailAction()
    data object OpenDeleteConfirm : RatingDetailAction()
    data object CloseDeleteConfirm : RatingDetailAction()
    data object ConfirmDelete : RatingDetailAction()

    /** Войти в режим ручного выбора строк для объединения / выйти из него. */
    data object StartMergeSelection : RatingDetailAction()
    data object CancelMergeSelection : RatingDetailAction()
    /** Открыть диалог объединения для подсказанной группы дублей. */
    data class OpenMerge(val participantKeys: List<String>) : RatingDetailAction()
    /** Открыть диалог объединения для строк, отмеченных вручную. */
    data object OpenMergeForSelected : RatingDetailAction()
    data class SelectMergeTarget(val participantKey: String) : RatingDetailAction()
    data object CloseMerge : RatingDetailAction()
    data object ConfirmMerge : RatingDetailAction()
}
