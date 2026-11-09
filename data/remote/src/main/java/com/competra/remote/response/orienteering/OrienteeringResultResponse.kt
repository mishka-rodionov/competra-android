package com.competra.remote.response.orienteering

data class OrienteeringResultResponse(
    val id: String,
    val competitionId: String,
    val groupId: Long,
    val participantId: String,
    val startTime: Long?,
    val finishTime: Long?,
    val totalTime: Long?,
    val rank: Int?,
    val status: String,
    val penaltyTime: Long,
    val totalScore: Int? = null,
    val scorePenalty: Int = 0,
    val splits: List<SplitTimeResponse>?,
    val isEditable: Boolean,
    val isEdited: Boolean,
    /** Статус выставлен организатором вручную. */
    val isStatusManual: Boolean? = null,
    /** Время считывания чипа на финише (мс). */
    val readAt: Long? = null,
    val updatedAt: Long = 0L
)

data class SplitTimeResponse(
    val controlPoint: Int,
    val timestamp: Long
)
