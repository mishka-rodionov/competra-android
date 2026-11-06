package com.competra.remote.response.rating

import com.google.gson.annotations.SerializedName

data class RatingStandingBreakdownEntryResponse(
    @SerializedName("competitionId") val competitionId: String,
    @SerializedName("place") val place: Int?,
    @SerializedName("points") val points: Int,
    @SerializedName("participantId") val participantId: String?
)

data class RatingStandingEntryResponse(
    @SerializedName("participantKey") val participantKey: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("totalPoints") val totalPoints: Int,
    @SerializedName("rank") val rank: Int,
    @SerializedName("breakdown") val breakdown: List<RatingStandingBreakdownEntryResponse>,
    @SerializedName("userId") val userId: String?,
    @SerializedName("firstName") val firstName: String?,
    @SerializedName("lastName") val lastName: String?
)

data class RatingStandingsResponse(
    @SerializedName("ratingGroupId") val ratingGroupId: Long,
    @SerializedName("standings") val standings: List<RatingStandingEntryResponse>,
    @SerializedName("possibleDuplicates") val possibleDuplicates: List<List<String>>?
)
