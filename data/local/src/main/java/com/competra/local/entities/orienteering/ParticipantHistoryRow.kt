package com.competra.local.entities.orienteering

/**
 * Строка истории участников организатора: участник прошлого соревнования и дата этого соревнования.
 * Результат [com.competra.local.dao.orienteering.OrienteeringParticipantDao.getParticipantHistory].
 */
data class ParticipantHistoryRow(
    val id: String,
    val competitionId: String,
    val userId: String,
    val firstName: String,
    val lastName: String,
    val groupName: String,
    val commandName: String,
    val competitionDate: Long
)
