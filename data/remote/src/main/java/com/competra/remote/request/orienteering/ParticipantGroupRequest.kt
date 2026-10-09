package com.competra.remote.request.orienteering

import com.google.gson.annotations.SerializedName

/**
 * Запрос на создание или обновление группы участников соревнования.
 *
 * @property groupId Идентификатор группы (может быть null при создании).
 * @property competitionId Идентификатор соревнования.
 * @property title Название группы (например, "М21").
 * @property gender Пол участников (MALE, FEMALE, MIXED).
 * @property minAge Минимальный возраст.
 * @property maxAge Максимальный возраст.
 * @property distanceId Идентификатор связанной дистанции.
 * @property maxParticipants Лимит участников для группы.
 * @property timeLimitMinutes Лимит времени для формата "по выбору" (BY_CHOICE), в минутах.
 * @property scorePenaltyPerMinute Штраф в очках за минуту опоздания сверх лимита (BY_CHOICE).
 * @property maxLatenessMinutes Порог сильного опоздания, после которого результат обнуляется (BY_CHOICE).
 */
data class ParticipantGroupRequest(
    @SerializedName("groupId")
    val groupId: Long? = null,
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
    /** Своё N командного зачёта: N > 0 — задать, 0 — как у соревнования (null для сервера — «не менять»). */
    @SerializedName("teamCountedResults")
    val teamCountedResults: Int? = null,
    @SerializedName("serverUpdatedAt")
    val serverUpdatedAt: Long? = null
)
