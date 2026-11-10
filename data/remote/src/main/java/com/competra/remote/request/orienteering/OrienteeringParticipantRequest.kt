package com.competra.remote.request.orienteering

data class OrienteeringParticipantRequest(
    val id: String,
    val userId: String?,
    val firstName: String,
    val lastName: String,
    val groupId: Long,
    val groupName: String,
    val competitionId: String,
    val commandName: String?,
    val startNumber: Int,
    val startTime: Long,
    val chipNumber: Long,
    val comment: String?,
    val isChipGiven: Boolean,
    /** Участник вне конкурса (ВК). Клиент шлёт всегда; null на сервере — «не менять» для старых версий. */
    val isOutOfCompetition: Boolean,
    val serverUpdatedAt: Long? = null
)
