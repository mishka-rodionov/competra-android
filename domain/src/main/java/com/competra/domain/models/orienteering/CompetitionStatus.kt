package com.competra.domain.models.orienteering

enum class CompetitionStatus {
    DRAFT, CREATED, REGISTRATION_OPEN, REGISTRATION_CLOSED, IN_PROGRESS, FINISHED, ARCHIVED, UNKNOWN
}

/**
 * true, если соревнование уже стартовало и участников удалять нельзя: к ним привязаны стартовое
 * время, чип и результат, а удаление сдвигает места в протоколе. Неявку отмечают статусом DNS.
 * Та же проверка выполняется на сервере (eSport) и в веб-клиенте.
 */
val CompetitionStatus.isParticipantDeletionLocked: Boolean
    get() = this == CompetitionStatus.IN_PROGRESS ||
        this == CompetitionStatus.FINISHED ||
        this == CompetitionStatus.ARCHIVED
