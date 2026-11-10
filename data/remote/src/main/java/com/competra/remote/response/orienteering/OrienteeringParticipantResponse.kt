package com.competra.remote.response.orienteering

data class OrienteeringParticipantResponse(
    val id: String?,
    val userId: String?,
    val firstName: String?,
    val lastName: String?,
    val groupId: Long?,
    val groupName: String?,
    val competitionId: String?,
    val commandName: String?,
    val startNumber: String?,
    val startTime: Long?,
    val chipNumber: String?,
    val comment: String?,
    val isChipGiven: Boolean?,
    /** Участник вне конкурса (ВК); null — старый сервер без поля. */
    val isOutOfCompetition: Boolean? = null,
    val updatedAt: Long = 0L
)
