package com.competra.remote.response.orienteering

/**
 * Участие в прошлом соревновании организатора (`GET event/orienteering/participants/history`).
 */
data class ParticipantHistoryResponse(
    val id: String?,
    val userId: String?,
    val firstName: String?,
    val lastName: String?,
    val groupName: String?,
    val commandName: String?,
    val competitionId: String?,
    val competitionDate: Long?
)
