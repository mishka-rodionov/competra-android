package com.competra.domain.models.cyclic_event

import kotlinx.serialization.Serializable

/**
 * Модель группы участников события.
 */
@Serializable
data class EventParticipantGroup(
    val groupId: String,
    val title: String,
    val description: String?,
    val maxParticipant: Int,
    val registeredParticipant: Int,
    val distanceName: String? = null,
    val distanceLengthMeters: Int? = null,
    val distanceClimbMeters: Int? = null,
    val distanceControlsCount: Int? = null,
    val distanceDescription: String? = null,
    /** Контрольное время группы в минутах (итоговое, с учётом умолчания соревнования). */
    val controlTimeMinutes: Int? = null,
    /** Политика применения КВ, выбранная для соревнования: IGNORE / DISQUALIFY / SCORE_PENALTY. */
    val overtimePolicy: String? = null
)
