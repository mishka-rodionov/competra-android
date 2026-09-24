package com.competra.remote.datasource.livetrack

import com.google.gson.annotations.SerializedName

/** `POST /api/live-track/sessions`: участник определяется сервером по JWT. */
data class LiveTrackStartRequest(
    @SerializedName("competitionId") val competitionId: String,
    @SerializedName("consent") val consent: Boolean
)

/** Ответ на старт/возобновление сессии. */
data class LiveTrackSessionResponse(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("status") val status: String,
    @SerializedName("closeReason") val closeReason: String?,
    @SerializedName("lastBatchSeq") val lastBatchSeq: Int,
    @SerializedName("uploadIntervalSec") val uploadIntervalSec: Int,
    @SerializedName("deadlineAt") val deadlineAt: Long
)

/** Батч точек. */
data class LiveTrackPointsRequest(
    @SerializedName("batchSeq") val batchSeq: Int,
    @SerializedName("points") val points: List<LiveTrackPointDto>
)

/** Точка в батче. */
data class LiveTrackPointDto(
    @SerializedName("t") val t: Long,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("acc") val acc: Float?
)

/** Ответ на батч/стоп. */
data class LiveTrackAckResponse(
    @SerializedName("status") val status: String,
    @SerializedName("closeReason") val closeReason: String?,
    @SerializedName("ackedBatchSeq") val ackedBatchSeq: Int
)
