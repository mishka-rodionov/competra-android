package com.competra.remote.response.orienteering

import com.competra.domain.models.Gender
import com.google.gson.annotations.SerializedName

/** Бэкенд (Gson) не сериализует null-поля — nullable-поля здесь могут отсутствовать в JSON. */
data class LinkResultSummaryResponse(
    @SerializedName("rank") val rank: Int?,
    @SerializedName("totalTime") val totalTime: Long?,
    @SerializedName("totalScore") val totalScore: Int?,
    @SerializedName("status") val status: String
)

data class LinkSuggestionResponse(
    @SerializedName("participantId") val participantId: String,
    @SerializedName("competitionId") val competitionId: String,
    @SerializedName("competitionTitle") val competitionTitle: String,
    @SerializedName("competitionStartDate") val competitionStartDate: Long,
    @SerializedName("firstName") val firstName: String,
    @SerializedName("lastName") val lastName: String,
    @SerializedName("groupName") val groupName: String,
    @SerializedName("commandName") val commandName: String?,
    @SerializedName("result") val result: LinkResultSummaryResponse?
)

data class LinkRequestResponse(
    @SerializedName("id") val id: String,
    @SerializedName("participantId") val participantId: String,
    @SerializedName("competitionId") val competitionId: String,
    @SerializedName("competitionTitle") val competitionTitle: String,
    @SerializedName("competitionStartDate") val competitionStartDate: Long,
    @SerializedName("participantFirstName") val participantFirstName: String,
    @SerializedName("participantLastName") val participantLastName: String,
    @SerializedName("groupName") val groupName: String,
    @SerializedName("status") val status: String,
    @SerializedName("source") val source: String,
    @SerializedName("comment") val comment: String?,
    @SerializedName("createdAt") val createdAt: Long
)

data class CompetitionLinkRequestResponse(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String,
    @SerializedName("source") val source: String,
    @SerializedName("comment") val comment: String?,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("participantId") val participantId: String,
    @SerializedName("participantFirstName") val participantFirstName: String,
    @SerializedName("participantLastName") val participantLastName: String,
    @SerializedName("groupName") val groupName: String,
    @SerializedName("commandName") val commandName: String?,
    @SerializedName("startNumber") val startNumber: Int,
    @SerializedName("result") val result: LinkResultSummaryResponse?,
    @SerializedName("userId") val userId: String,
    @SerializedName("userFirstName") val userFirstName: String,
    @SerializedName("userLastName") val userLastName: String,
    @SerializedName("userBirthYear") val userBirthYear: Int?,
    @SerializedName("userGender") val userGender: Gender?,
    @SerializedName("nameMatches") val nameMatches: Boolean,
    @SerializedName("eligibilityWarning") val eligibilityWarning: String?,
    @SerializedName("competingRequests") val competingRequests: Int,
    @SerializedName("userAlreadyInCompetition") val userAlreadyInCompetition: Boolean
)
