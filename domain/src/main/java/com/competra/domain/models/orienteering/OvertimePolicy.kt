package com.competra.domain.models.orienteering

/**
 * Что делать с результатом, превысившим контрольное время (КВ).
 *
 * Политика задаётся один раз на всё соревнование, само КВ — на уровне соревнования
 * ([OrienteeringCompetition.controlTimeMinutes]) с переопределением на уровне группы
 * ([com.competra.domain.models.ParticipantGroup.timeLimitMinutes]).
 */
enum class OvertimePolicy {
    /** КВ показывается в информации о соревновании, но на результаты не влияет. */
    IGNORE,

    /** Превысившие КВ снимаются: статус OVERTIME, места не получают. */
    DISQUALIFY,

    /**
     * Формат "по выбору" (score-О): штраф очками за каждую минуту опоздания
     * (scorePenaltyPerMinute) и обнуление после maxLatenessMinutes.
     */
    SCORE_PENALTY;

    companion object {
        val DEFAULT = IGNORE

        fun fromString(raw: String?): OvertimePolicy =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}
