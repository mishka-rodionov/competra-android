package com.competra.remote.response.events

import com.google.gson.annotations.SerializedName

data class ParticipantPublicResponse(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String?,
    @SerializedName("firstName") val firstName: String,
    @SerializedName("lastName") val lastName: String,
    @SerializedName("groupId") val groupId: String,
    @SerializedName("groupName") val groupName: String,
    @SerializedName("competitionId") val competitionId: String,
    @SerializedName("commandName") val commandName: String?,
    @SerializedName("startNumber") val startNumber: Int,
    @SerializedName("startTime") val startTime: Long,
    @SerializedName("chipNumber") val chipNumber: Long,
    @SerializedName("comment") val comment: String?,
    @SerializedName("isChipGiven") val isChipGiven: Boolean,
    /** Участник вне конкурса (ВК); null — старый сервер без поля. */
    @SerializedName("isOutOfCompetition") val isOutOfCompetition: Boolean? = null
)
