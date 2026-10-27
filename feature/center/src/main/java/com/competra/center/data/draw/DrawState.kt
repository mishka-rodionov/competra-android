package com.competra.center.data.draw

import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.ui.BaseState

data class DrawState(
    val participants: List<OrienteeringParticipant> = emptyList(),
    /** Группы с участниками, но без дистанции: пока они есть, жеребьёвка по дистанциям недоступна. */
    val groupsWithoutDistance: List<String> = emptyList()
) : BaseState
