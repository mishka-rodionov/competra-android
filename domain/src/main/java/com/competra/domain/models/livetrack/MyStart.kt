package com.competra.domain.models.livetrack

import com.competra.domain.models.orienteering.CompetitionStatus
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Без контрольного времени (и как нижняя граница) локальная запись идёт столько, прежде чем остановиться сама. */
private val DEFAULT_LOCAL_RECORDING_MS = TimeUnit.HOURS.toMillis(6)

/** Запас к контрольному времени — как на сервере. */
private val CONTROL_TIME_GRACE_MS = TimeUnit.MINUTES.toMillis(60)

/**
 * Соревнование, на которое зарегистрирован пользователь, — локальная копия, чтобы включить
 * онлайн-трек на старте без интернета (карточка «Сегодня старт» на вкладке «События»).
 *
 * @property competitionId UUID соревнования.
 * @property title Название.
 * @property startDate Начало соревнования (Unix ms).
 * @property endDate Окончание многодневного соревнования (Unix ms) или `null`.
 * @property timeZoneId IANA-пояс соревнования — по нему определяется «сегодня».
 * @property address Место проведения.
 * @property status Статус на момент последнего обновления кэша.
 * @property controlTimeMinutes Контрольное время соревнования, если задано.
 */
data class MyStart(
    val competitionId: String,
    val title: String,
    val startDate: Long,
    val endDate: Long?,
    val timeZoneId: String,
    val address: String?,
    val status: CompetitionStatus,
    val controlTimeMinutes: Int?
) {
    /** Пояс соревнования; при битом id — пояс телефона. */
    val zone: ZoneId get() = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())

    /** Сегодня (в поясе соревнования) один из дней соревнования, и оно не завершено. */
    fun isToday(now: Long): Boolean {
        if (status == CompetitionStatus.FINISHED || status == CompetitionStatus.ARCHIVED) return false
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val firstDay = Instant.ofEpochMilli(startDate).atZone(zone).toLocalDate()
        val lastDay = endDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }?.takeIf { it >= firstDay } ?: firstDay
        return today in firstDay..lastDay
    }

    /**
     * Когда запись, начатая в [startedAt], остановится сама, если сервер так и не ответил: не раньше
     * 6 ч от начала соревнования (старт участника может быть сильно позже), с КВ — не раньше КВ + 60 мин.
     * После связи с сервером действует его срок.
     */
    fun localDeadline(startedAt: Long): Long {
        val controlTime = controlTimeMinutes?.takeIf { it > 0 }?.let { TimeUnit.MINUTES.toMillis(it.toLong()) + CONTROL_TIME_GRACE_MS } ?: 0L
        return maxOf(startedAt, startDate) + maxOf(DEFAULT_LOCAL_RECORDING_MS, controlTime)
    }

    companion object {
        /** Локальный срок записи, если соревнования нет в кэше. */
        fun defaultLocalDeadline(startedAt: Long): Long = startedAt + DEFAULT_LOCAL_RECORDING_MS
    }
}
