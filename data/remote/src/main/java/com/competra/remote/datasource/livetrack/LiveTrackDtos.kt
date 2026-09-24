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

/** Дистанция с онлайн-треками (зритель). */
data class TrackedDistanceDto(
    @SerializedName("distanceId") val distanceId: Long,
    @SerializedName("name") val name: String?,
    @SerializedName("activeCount") val activeCount: Int,
    @SerializedName("totalCount") val totalCount: Int
)

/** Ответ `live` (зритель). */
data class LiveSnapshotDto(
    @SerializedName("cursor") val cursor: String,
    @SerializedName("reset") val reset: Boolean,
    @SerializedName("serverTime") val serverTime: Long,
    @SerializedName("sessions") val sessions: List<LiveSessionDto>?
)

/** Сессия в ответе `live`; точки — `[t, lat, lon]`. */
data class LiveSessionDto(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("participantId") val participantId: String,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("groupName") val groupName: String?,
    @SerializedName("startNumber") val startNumber: Int?,
    @SerializedName("status") val status: String,
    @SerializedName("closeReason") val closeReason: String?,
    @SerializedName("startedAt") val startedAt: Long,
    @SerializedName("lastPointAt") val lastPointAt: Long?,
    @SerializedName("points") val points: List<List<Double>>?
)

/** Трек из архива `tracks`: `trackEncoded` — формат `TrackCodec` от `startedAt`. */
data class ArchivedTrackDto(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("participantId") val participantId: String,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("groupName") val groupName: String?,
    @SerializedName("startNumber") val startNumber: Int?,
    @SerializedName("status") val status: String,
    @SerializedName("closeReason") val closeReason: String?,
    @SerializedName("startedAt") val startedAt: Long,
    @SerializedName("closedAt") val closedAt: Long?,
    @SerializedName("trackEncoded") val trackEncoded: String?
)
