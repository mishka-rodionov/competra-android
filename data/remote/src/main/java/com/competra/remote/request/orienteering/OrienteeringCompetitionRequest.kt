package com.competra.remote.request.orienteering

import com.google.gson.annotations.SerializedName
import com.competra.remote.request.competition.CompetitionRequest

/**
 * Запрос для создания/обновления соревнования по ориентированию на сервере.
 * 
 * @property competitionId Уникальный идентификатор
 * @property competition Базовая информация
 * @property direction Направление
 * @property punchingSystem Система отметки
 * @property startTimeMode Режим начала соревнования
 * @property countdownTimer Время отсчета перед стартом (в минутах)
 */
data class OrienteeringCompetitionRequest(
    @SerializedName("competitionId")
    val competitionId: String,

    @SerializedName("competition")
    val competition: CompetitionRequest,

    @SerializedName("direction")
    val direction: String,

    @SerializedName("punchingSystem")
    val punchingSystem: String,

    @SerializedName("startTimeMode")
    val startTimeMode: String,

    @SerializedName("countdownTimer")
    val countdownTimer: Int? = null,

    @SerializedName("startIntervalSeconds")
    val startIntervalSeconds: Int? = null,

    @SerializedName("controlTimeMinutes")
    val controlTimeMinutes: Int? = null,

    @SerializedName("overtimePolicy")
    val overtimePolicy: String? = null,

    @SerializedName("serverUpdatedAt")
    val serverUpdatedAt: Long? = null
)
