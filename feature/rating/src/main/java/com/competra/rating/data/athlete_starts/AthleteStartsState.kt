package com.competra.rating.data.athlete_starts

import com.competra.domain.models.rating.RatingCompetition
import com.competra.domain.models.rating.RatingStanding
import com.competra.ui.BaseState

data class AthleteStartsState(
    val ratingId: String = "",
    val groupId: Long = 0L,
    val participantKey: String = "",
    val standing: RatingStanding? = null,
    val competitions: List<RatingCompetition> = emptyList(),
    val isLoading: Boolean = false
) : BaseState
