package com.competra.remote.response.events

import com.competra.remote.response.competition.CoordinatesResponse
import com.google.gson.annotations.SerializedName

data class ParticipantGroupDetailResponse(
    @SerializedName("groupId") val groupId: String,
    @SerializedName("title") val title: String,
    @SerializedName("maxParticipants") val maxParticipants: Int?,
    @SerializedName("registeredCount") val registeredCount: Int,
    @SerializedName("distanceName") val distanceName: String? = null,
    @SerializedName("distanceLengthMeters") val distanceLengthMeters: Int? = null,
    @SerializedName("distanceClimbMeters") val distanceClimbMeters: Int? = null,
    @SerializedName("distanceControlsCount") val distanceControlsCount: Int? = null,
    @SerializedName("distanceDescription") val distanceDescription: String? = null,
    /** Итоговое КВ группы: своё значение группы либо унаследованное от соревнования. */
    @SerializedName("controlTimeMinutes") val controlTimeMinutes: Int? = null
)

data class CompetitionDetailResponse(
    @SerializedName("id") val id: String,
    @SerializedName("legacyId") val legacyId: Long? = null,
    @SerializedName("title") val title: String,
    @SerializedName("startDate") val startDate: Long,
    @SerializedName("endDate") val endDate: Long?,
    @SerializedName("kindOfSport") val kindOfSport: String,
    @SerializedName("description") val description: String?,
    @SerializedName("address") val address: String?,
    @SerializedName("mainOrganizerId") val mainOrganizerId: String?,
    @SerializedName("organizingClubId") val organizingClubId: String? = null,
    @SerializedName("organizerFirstName") val organizerFirstName: String? = null,
    @SerializedName("organizerLastName") val organizerLastName: String? = null,
    @SerializedName("organizerMiddleName") val organizerMiddleName: String? = null,
    @SerializedName("coordinates") val coordinates: CoordinatesResponse? = null,
    @SerializedName("status") val status: String,
    @SerializedName("startTime") val startTime: Long? = null,
    @SerializedName("registrationStart") val registrationStart: Long?,
    @SerializedName("registrationEnd") val registrationEnd: Long?,
    @SerializedName("maxParticipants") val maxParticipants: Int?,
    @SerializedName("feeAmount") val feeAmount: Double? = null,
    @SerializedName("feeCurrency") val feeCurrency: String? = null,
    @SerializedName("regulationUrl") val regulationUrl: String? = null,
    @SerializedName("mapUrl") val mapUrl: String? = null,
    @SerializedName("resultsUrl") val resultsUrl: String? = null,
    @SerializedName("contactPhone") val contactPhone: String? = null,
    @SerializedName("contactEmail") val contactEmail: String? = null,
    @SerializedName("website") val website: String? = null,
    @SerializedName("timeZoneId") val timeZoneId: String? = null,
    @SerializedName("resultsStatus") val resultsStatus: String,
    /** IGNORE / DISQUALIFY / SCORE_PENALTY — как соревнование обходится с превышением КВ. */
    @SerializedName("overtimePolicy") val overtimePolicy: String? = null,
    @SerializedName("participantGroups") val participantGroups: List<ParticipantGroupDetailResponse>,
    @SerializedName("isUserRegistered") val isUserRegistered: Boolean = false,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("coverCropX") val coverCropX: Double? = null,
    @SerializedName("coverCropY") val coverCropY: Double? = null,
    @SerializedName("coverCropWidth") val coverCropWidth: Double? = null,
    @SerializedName("coverCropHeight") val coverCropHeight: Double? = null
)
