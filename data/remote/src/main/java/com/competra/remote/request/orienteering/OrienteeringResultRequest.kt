package com.competra.remote.request.orienteering

data class OrienteeringResultRequest(
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
    val splits: List<SplitTimeRequest>?,
    val isEditable: Boolean,
    val isEdited: Boolean,
    /** Статус выставлен организатором вручную. */
    val isStatusManual: Boolean? = null,
    /** Время считывания чипа на финише (мс); null — сервер оставляет сохранённое. */
    val readAt: Long? = null,
    val serverUpdatedAt: Long? = null
)

data class SplitTimeRequest(
    val controlPoint: Int,
    val timestamp: Long
)
