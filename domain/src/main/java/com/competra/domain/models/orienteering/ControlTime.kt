package com.competra.domain.models.orienteering

import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.ResultStatus

/**
 * Контрольное время (КВ) и его применение к результату.
 *
 * Правило применяется гибридно: здесь — локально, в том числе офлайн при считывании чипа,
 * и на сервере при пересчёте мест (ResultRanking в проекте eSport). Обе реализации выводят
 * статус заново из времени и текущих настроек, поэтому снятие за КВ обратимо: смена политики
 * на [OvertimePolicy.IGNORE] или увеличение КВ возвращают результат в FINISHED.
 */

/** Итоговое КВ группы в минутах: своё значение группы, иначе умолчание соревнования. */
fun effectiveControlTimeMinutes(groupLimitMinutes: Int?, competitionControlTimeMinutes: Int?): Int? =
    groupLimitMinutes ?: competitionControlTimeMinutes

/** Итоговое КВ для пары группа/соревнование. */
fun effectiveControlTimeMinutes(group: ParticipantGroup?, competition: OrienteeringCompetition?): Int? =
    effectiveControlTimeMinutes(group?.timeLimitMinutes, competition?.controlTimeMinutes)

/**
 * Превышение КВ считается по чистому времени на дистанции (финиш − старт участника, в секундах),
 * без штрафного времени: penaltyTime — санкция судьи, а не бег.
 * Ровно КВ укладывается в лимит, снимается только строгое превышение.
 */
fun isOvertime(totalTimeSeconds: Long?, controlTimeMinutes: Int?): Boolean =
    controlTimeMinutes != null && totalTimeSeconds != null && totalTimeSeconds > controlTimeMinutes * 60L

/**
 * Приводит статус результата в соответствие с КВ и политикой соревнования.
 *
 * Трогает только FINISHED и OVERTIME: DSQ, DNF, DNS и незавершённые статусы остаются как есть —
 * снятие за ошибку отметки важнее превышения КВ.
 */
fun applyOvertimePolicy(
    status: ResultStatus,
    totalTimeSeconds: Long?,
    controlTimeMinutes: Int?,
    policy: OvertimePolicy
): ResultStatus {
    if (status != ResultStatus.FINISHED && status != ResultStatus.OVERTIME) return status
    val disqualifies = policy == OvertimePolicy.DISQUALIFY &&
        isOvertime(totalTimeSeconds, controlTimeMinutes)
    return if (disqualifies) ResultStatus.OVERTIME else ResultStatus.FINISHED
}
