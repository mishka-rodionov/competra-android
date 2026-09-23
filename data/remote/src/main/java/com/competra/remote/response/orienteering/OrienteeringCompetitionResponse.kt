package com.competra.remote.response.orienteering

import com.google.gson.annotations.SerializedName
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.remote.response.competition.CompetitionResponse

/**
 * Ответ сервера с информацией о соревновании по ориентированию.
 * 
 * @property competitionId Уникальный идентификатор
 * @property competition Базовая информация
 * @property direction Направление
 * @property punchingSystem Система отметки
 * @property startTimeMode Режим начала соревнования
 * @property countdownTimer Время отсчета перед стартом (в минутах)
 * @property startTime Фактическое время начала соревнования (timestamp)
 * @property startIntervalSeconds Интервал между стартами участников (в секундах)
 */
data class OrienteeringCompetitionResponse(
    @SerializedName("competitionId")
    val competitionId: String,

    @SerializedName("competition")
    val competition: CompetitionResponse,

    @SerializedName("direction")
    val direction: String,

    @SerializedName("punchingSystem")
    val punchingSystem: PunchingSystem,

    @SerializedName("startTimeMode")
    val startTimeMode: StartTimeMode,

    @SerializedName("countdownTimer")
    val countdownTimer: Int? = null,

    @SerializedName("startTime")
    val startTime: Long? = null,

    @SerializedName("startIntervalSeconds")
    val startIntervalSeconds: Int? = null,

    @SerializedName("controlTimeMinutes")
    val controlTimeMinutes: Int? = null,

    @SerializedName("overtimePolicy")
    val overtimePolicy: String? = null,

    @SerializedName("updatedAt")
    val updatedAt: Long = 0L
)
