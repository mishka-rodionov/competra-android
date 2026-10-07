package com.competra.remote.request.orienteering

import com.google.gson.annotations.SerializedName

/**
 * Запрос на создание или обновление дистанции соревнования.
 *
 * @property distanceId Серверный идентификатор дистанции. Null при создании, заполняется при обновлении.
 * @property competitionId Серверный идентификатор соревнования.
 * @property name Название дистанции.
 * @property lengthMeters Протяжённость в метрах.
 * @property climbMeters Набор высоты в метрах.
 * @property controlsCount Количество контрольных пунктов.
 * @property description Описание дистанции.
 *
 * Поля карты (`mapUrl`, углы) и координаты старта/финиша намеренно не отправляются: карту
 * прикрепляет веб, координаты приходят из IOF XML, а сервер при их отсутствии в запросе оставляет
 * существующие значения как есть.
 */
data class DistanceRequest(
    @SerializedName("distanceId")
    val distanceId: Long?,
    @SerializedName("competitionId")
    val competitionId: String,
    @SerializedName("name")
    val name: String?,
    @SerializedName("lengthMeters")
    val lengthMeters: Int,
    @SerializedName("climbMeters")
    val climbMeters: Int,
    @SerializedName("controlsCount")
    val controlsCount: Int,
    @SerializedName("description")
    val description: String?,
    @SerializedName("controlPoints")
    val controlPoints: List<ControlPointRequest> = emptyList(),
    @SerializedName("finishControlPoint")
    val finishControlPoint: Int? = null,
    @SerializedName("startControlPoint")
    val startControlPoint: Int? = null,
    /** Минимум КП: N > 0 — минимум, 0 — все КП, null — сервер значение не меняет. */
    @SerializedName("minControlsCount")
    val minControlsCount: Int? = null,
    @SerializedName("serverUpdatedAt")
    val serverUpdatedAt: Long? = null
)
