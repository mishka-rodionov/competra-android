package com.competra.remote.response.events

import com.google.gson.annotations.SerializedName

/** Ответ `GET event/orienteering/competitions/{id}/registration-team-options`. */
data class RegistrationTeamOptionsResponse(
    @SerializedName("options") val options: List<RegistrationTeamOptionResponse>?,
    @SerializedName("protocolNames") val protocolNames: List<String>?,
    @SerializedName("suggestedCommandName") val suggestedCommandName: String?
)

/** Клубная команда (или клуб без команды) пользователя с готовой подписью для протокола. */
data class RegistrationTeamOptionResponse(
    @SerializedName("teamId") val teamId: String?,
    @SerializedName("clubId") val clubId: String,
    @SerializedName("clubName") val clubName: String,
    @SerializedName("teamName") val teamName: String?,
    @SerializedName("label") val label: String
)

/** Ответ `GET clubs/match`: клуб с названием, совпавшим с подписью команды. */
data class ClubMatchResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("allowJoinRequests") val allowJoinRequests: Boolean
)
