package com.competra.domain.models.events

/**
 * Статус события для экранов участника (детали события, группа).
 *
 * - [CREATED] — регистрация ещё не открыта;
 * - [REGISTRATION] — регистрация открыта;
 * - [REGISTRATION_CLOSED] — регистрация завершена (по дедлайну или досрочно организатором), старта ещё не было;
 * - [STARTED], [FINISHED], [CANCELLED] — соревнование идёт, завершено, отменено.
 */
enum class EventStatus {
    CREATED, REGISTRATION, REGISTRATION_CLOSED, STARTED, FINISHED, CANCELLED
}
