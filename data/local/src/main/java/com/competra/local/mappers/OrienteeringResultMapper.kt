package com.competra.local.mappers

import com.competra.domain.models.orienteering.GroupWithParticipantsAndResults
import com.competra.domain.models.orienteering.ParticipantWithResult
import com.competra.local.entities.orienteering.GroupWithParticipantsAndResultsEntity
import com.competra.local.entities.orienteering.ParticipantWithResultEntity

fun GroupWithParticipantsAndResultsEntity.toDomain(): GroupWithParticipantsAndResults {
    return GroupWithParticipantsAndResults(
        group = group.toDomain(),
        // Помеченные на удаление участники ждут выгрузки DELETE и в протоколе не показываются
        participants = participants
            .filterNot { it.participant.isDeleted }
            .map(ParticipantWithResultEntity::toDomain)
    )
}

fun ParticipantWithResultEntity.toDomain(): ParticipantWithResult {
    return ParticipantWithResult(
        participant = participant.toDomain(),
        result = result?.toDomain()
    )
}