package com.competra.remote.request.orienteering

import com.google.gson.annotations.SerializedName

data class ParticipantGroupPublishRequest(
    @SerializedName("groupId") val groupId: Long? = null,
    @SerializedName("competitionId") val competitionId: String,
    @SerializedName("title") val title: String,
    @SerializedName("gender") val gender: String?,
    @SerializedName("minAge") val minAge: Int?,
    @SerializedName("maxAge") val maxAge: Int?,
    @SerializedName("distanceId") val distanceId: Long,
    @SerializedName("maxParticipants") val maxParticipants: Int?,
    @SerializedName("timeLimitMinutes") val timeLimitMinutes: Int? = null,
    @SerializedName("scorePenaltyPerMinute") val scorePenaltyPerMinute: Int? = null,
    @SerializedName("maxLatenessMinutes") val maxLatenessMinutes: Int? = null,
    /** Своё N командного зачёта: N > 0 — задать, 0 — как у соревнования. */
    @SerializedName("teamCountedResults") val teamCountedResults: Int? = null,
    @SerializedName("serverUpdatedAt") val serverUpdatedAt: Long? = null
)
