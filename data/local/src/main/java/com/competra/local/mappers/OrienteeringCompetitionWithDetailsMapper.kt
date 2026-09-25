package com.competra.local.mappers

import com.competra.domain.models.orienteering.OrienteeringCompetitionDetails
import com.competra.domain.models.orienteering.ParticipantGroupParticipants
import com.competra.local.entities.orienteering.OrienteeringCompetitionWithDetails
import com.competra.local.entities.orienteering.ParticipantGroupWithParticipants

fun OrienteeringCompetitionWithDetails.toDomain(): OrienteeringCompetitionDetails {
    return OrienteeringCompetitionDetails(
        competition = competition.toDomain(),
        groupsWithParticipants = groupsWithParticipants.map { it.toDomain() }
    )
}

fun ParticipantGroupWithParticipants.toDomain(): ParticipantGroupParticipants {
    return ParticipantGroupParticipants(
        group = group.toDomain(),
        // Помеченные на удаление участники ждут выгрузки DELETE и в UI не показываются
        participants = participants.filterNot { it.isDeleted }.map { it.toDomain() }
    )
}
