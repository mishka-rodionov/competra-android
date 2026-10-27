package com.competra.remote.request.competition

import com.google.gson.annotations.SerializedName

/**
 * Запрос на создание/обновление соревнования на сервере.
 */
data class CompetitionRequest(
    @SerializedName("title")
    val title: String,
    @SerializedName("startDate")
    val startDate: Long,
    @SerializedName("endDate")
    val endDate: Long?,
    @SerializedName("kindOfSport")
    val kindOfSport: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("address")
    val address: String?,
    @SerializedName("mainOrganizerId")
    val mainOrganizerId: String?,
    @SerializedName("coordinates")
    val coordinates: CoordinatesRequest?,
    @SerializedName("status")
    val status: String,
    @SerializedName("registrationStart")
    val registrationStart: Long?,
    @SerializedName("registrationEnd")
    val registrationEnd: Long?,
    @SerializedName("maxParticipants")
    val maxParticipants: Int?,
    @SerializedName("feeAmount")
    val feeAmount: Double?,
    @SerializedName("feeCurrency")
    val feeCurrency: String?,
    @SerializedName("regulationUrl")
    val regulationUrl: String?,
    @SerializedName("mapUrl")
    val mapUrl: String?,
    @SerializedName("resultsUrl")
    val resultsUrl: String? = null,
    @SerializedName("contactPhone")
    val contactPhone: String?,
    @SerializedName("contactEmail")
    val contactEmail: String?,
    @SerializedName("website")
    val website: String?,
    @SerializedName("resultsStatus")
    val resultsStatus: String,
    @SerializedName("timeZoneId")
    val timeZoneId: String,
    @SerializedName("imageUrl")
    val imageUrl: String? = null,
    @SerializedName("coverCropX")
    val coverCropX: Double? = null,
    @SerializedName("coverCropY")
    val coverCropY: Double? = null,
    @SerializedName("coverCropWidth")
    val coverCropWidth: Double? = null,
    @SerializedName("coverCropHeight")
    val coverCropHeight: Double? = null,
    /** Тестовое соревнование: создаётся из debug-сборки, не попадает в публичную ленту. */
    @SerializedName("isTest")
    val isTest: Boolean = false,
    @SerializedName("serverUpdatedAt")
    val serverUpdatedAt: Long? = null,
    @SerializedName("organizingClubId")
    val organizingClubId: String? = null,
    /** Организатор (ФИО). Сервер перезаписывает поле при каждом upsert — передаём всегда. */
    @SerializedName("organizerName")
    val organizerName: String? = null
)
