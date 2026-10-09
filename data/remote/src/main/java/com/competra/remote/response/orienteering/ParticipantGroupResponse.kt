package com.competra.remote.response.orienteering

import com.google.gson.annotations.SerializedName

/**
 * Ответ сервера с информацией о группе участников соревнования.
 *
 * @property groupId ID группы на сервере.
 * @property competitionId ID соревнования на сервере.
 * @property title Название группы.
 * @property gender Пол участников.
 * @property minAge Минимальный возраст.
 * @property maxAge Максимальный возраст.
 * @property distanceId ID связанной дистанции.
 * @property maxParticipants Лимит участников для группы.
 * @property timeLimitMinutes Лимит времени для формата "по выбору" (BY_CHOICE), в минутах.
 * @property scorePenaltyPerMinute Штраф в очках за минуту опоздания сверх лимита (BY_CHOICE).
 * @property maxLatenessMinutes Порог сильного опоздания, после которого результат обнуляется (BY_CHOICE).
 */
data class ParticipantGroupResponse(
    @SerializedName("groupId")
    val groupId: Long,
    @SerializedName("competitionId")
    val competitionId: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("gender")
    val gender: String?,
    @SerializedName("minAge")
    val minAge: Int?,
    @SerializedName("maxAge")
    val maxAge: Int?,
    @SerializedName("distanceId")
    val distanceId: Long,
    @SerializedName("maxParticipants")
    val maxParticipants: Int?,
    @SerializedName("timeLimitMinutes")
    val timeLimitMinutes: Int? = null,
    @SerializedName("scorePenaltyPerMinute")
    val scorePenaltyPerMinute: Int? = null,
    @SerializedName("maxLatenessMinutes")
    val maxLatenessMinutes: Int? = null,
    /** Своё N командного зачёта; null — как у соревнования. */
    @SerializedName("teamCountedResults")
    val teamCountedResults: Int? = null,
    @SerializedName("updatedAt")
    val updatedAt: Long = 0L
)
