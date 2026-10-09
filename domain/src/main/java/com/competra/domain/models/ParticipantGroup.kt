package com.competra.domain.models

import com.competra.domain.models.orienteering.ControlPoint

data class ParticipantGroup(
    val groupId: Long,
    val competitionId: String,
    val title: String,
    val gender: Gender?,                   // MALE, FEMALE, MIXED, null – не задано
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val distanceId: Long,                  // ссылка на дистанцию
    val maxParticipants: Int? = null,      // лимит для группы
    val timeLimitMinutes: Int? = null,         // лимит времени для формата "по выбору" (BY_CHOICE)
    val scorePenaltyPerMinute: Int? = null,    // штраф в очках за минуту опоздания сверх лимита
    val maxLatenessMinutes: Int? = null,       // порог сильного опоздания → результат обнуляется
    val teamCountedResults: Int? = null,       // своё N командного зачёта; null — как у соревнования (TeamScoring)
    // Поля синхронизации
    val remoteId: Long? = null,          // ID группы на сервере (null = не синхронизирована)
    val isSynced: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val serverUpdatedAt: Long? = null,
    val syncError: String? = null
)