package com.competra.rating.data.athlete_starts

import com.competra.ui.BaseAction

sealed class AthleteStartsAction : BaseAction {
    data object BackClick : AthleteStartsAction()
    data class StartClick(val competitionId: String) : AthleteStartsAction()
}
