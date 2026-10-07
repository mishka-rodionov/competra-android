package com.competra.remote.response.orienteering

import com.google.gson.annotations.SerializedName

/**
 * Ответ сервера с данными дистанции соревнования.
 *
 * @property id Серверный идентификатор дистанции.
 * @property competitionId Серверный идентификатор соревнования.
 * @property name Название дистанции.
 * @property lengthMeters Протяжённость в метрах.
 * @property climbMeters Набор высоты в метрах.
 * @property controlsCount Количество контрольных пунктов.
 * @property description Описание дистанции.
 * @property startLatitude Широта старта (из IOF XML); вместе с долготой — начало первого перегона.
 * @property finishLatitude Широта финиша (из IOF XML); вместе с долготой — конец перегона на финиш.
 * @property mapUrl Адрес растровой карты дистанции; углы `map*` — её геопривязка в WGS84.
 *                  Если задан верхний правый угол, углы точные (привязка по трём точкам),
 *                  иначе верхний левый и нижний правый задают bbox «север вверх».
 */
data class DistanceResponse(
    @SerializedName("id")
    val id: Long,
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
    val controlPoints: List<ControlPointResponse> = emptyList(),
    @SerializedName("finishControlPoint")
    val finishControlPoint: Int? = null,
    @SerializedName("startControlPoint")
    val startControlPoint: Int? = null,
    @SerializedName("startLatitude")
    val startLatitude: Double? = null,
    @SerializedName("startLongitude")
    val startLongitude: Double? = null,
    @SerializedName("finishLatitude")
    val finishLatitude: Double? = null,
    @SerializedName("finishLongitude")
    val finishLongitude: Double? = null,
    /** Минимум КП (формат «по выбору» с минимумом КП); null — все КП. */
    @SerializedName("minControlsCount")
    val minControlsCount: Int? = null,
    @SerializedName("mapUrl")
    val mapUrl: String? = null,
    @SerializedName("mapTopLeftLat")
    val mapTopLeftLat: Double? = null,
    @SerializedName("mapTopLeftLng")
    val mapTopLeftLng: Double? = null,
    @SerializedName("mapTopRightLat")
    val mapTopRightLat: Double? = null,
    @SerializedName("mapTopRightLng")
    val mapTopRightLng: Double? = null,
    @SerializedName("mapBottomRightLat")
    val mapBottomRightLat: Double? = null,
    @SerializedName("mapBottomRightLng")
    val mapBottomRightLng: Double? = null,
    @SerializedName("updatedAt")
    val updatedAt: Long = 0L
)
