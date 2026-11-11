package com.competra.domain.models.orienteering

import com.competra.domain.models.ParticipantGroup

/**
 * Формат дистанции: направление и режим «по выбору» ([byChoiceMode] значим только при
 * [OrienteeringDirection.BY_CHOICE]).
 *
 * У соревнования это формат по умолчанию ([OrienteeringCompetition.direction],
 * [OrienteeringCompetition.byChoiceMode]), дистанция может его переопределить
 * ([Distance.direction] / [Distance.byChoiceMode], `null` — «как у соревнования»). Формат группы —
 * формат её дистанции. Та же логика есть на сервере (eSport, CourseFormat.kt); спека —
 * `docs/specs/per-distance-format.md`.
 */
data class CourseFormat(
    val direction: OrienteeringDirection,
    val byChoiceMode: ByChoiceMode = ByChoiceMode.DEFAULT
) {
    /** true, если места считаются по сумме баллов (score-О) — см. [ranksByScore]. */
    val ranksByScore: Boolean get() = ranksByScore(direction, byChoiceMode)

    /** Одинаковые ли правила: режим «по выбору» сравнивается только при [OrienteeringDirection.BY_CHOICE]. */
    fun sameAs(other: CourseFormat): Boolean =
        direction == other.direction &&
            (direction != OrienteeringDirection.BY_CHOICE || byChoiceMode == other.byChoiceMode)

    companion object {
        /** Формат, когда соревнование неизвестно (данные ещё не загрузились). */
        val DEFAULT = CourseFormat(OrienteeringDirection.FORWARD, ByChoiceMode.DEFAULT)
    }
}

/** Формат соревнования по умолчанию. */
val OrienteeringCompetition.defaultFormat: CourseFormat
    get() = CourseFormat(direction, byChoiceMode)

/** Формат соревнования по умолчанию, [CourseFormat.DEFAULT] — если соревнования нет. */
val OrienteeringCompetition?.defaultFormatOrDefault: CourseFormat
    get() = this?.defaultFormat ?: CourseFormat.DEFAULT

/**
 * Эффективный формат дистанции: свои значения дистанции, иначе — [competitionFormat]. Дистанция
 * `null` (группа без дистанции) получает формат соревнования.
 */
fun Distance?.effectiveFormat(competitionFormat: CourseFormat): CourseFormat = CourseFormat(
    direction = this?.direction ?: competitionFormat.direction,
    byChoiceMode = this?.byChoiceMode ?: competitionFormat.byChoiceMode
)

/** Эффективный формат дистанции в соревновании [competition] — см. [effectiveFormat]. */
fun Distance?.effectiveFormat(competition: OrienteeringCompetition?): CourseFormat =
    effectiveFormat(competition.defaultFormatOrDefault)

/** Эффективный формат группы — формат её дистанции из [distances] (см. [hasAssignedDistance]). */
fun ParticipantGroup.effectiveFormat(distances: List<Distance>, competitionFormat: CourseFormat): CourseFormat =
    distances.firstOrNull { it.id == distanceId && distanceId != NO_DISTANCE_ID }.effectiveFormat(competitionFormat)

/** Свой ли формат у дистанции, отличный от формата соревнования [competitionFormat]. */
fun Distance.hasOwnFormat(competitionFormat: CourseFormat): Boolean =
    !effectiveFormat(competitionFormat).sameAs(competitionFormat)

/** true, если хотя бы у одной дистанции формат отличается от формата соревнования. */
fun List<Distance>.isMixedFormat(competitionFormat: CourseFormat): Boolean =
    any { it.hasOwnFormat(competitionFormat) }

/** Политика КВ для дистанции заданного вида: по баллам — [OrienteeringCompetition.scoreOvertimePolicy], иначе по времени. */
fun OrienteeringCompetition.overtimePolicyFor(ranksByScore: Boolean): OvertimePolicy =
    if (ranksByScore) scoreOvertimePolicy else timeOvertimePolicy

/** Политика КВ для дистанции формата [format]. */
fun OrienteeringCompetition.overtimePolicyFor(format: CourseFormat): OvertimePolicy =
    overtimePolicyFor(format.ranksByScore)

/**
 * Значение legacy-поля API `overtimePolicy` — политика для формата соревнования по умолчанию. Его
 * читают клиенты, не знающие о формате на дистанции.
 */
val OrienteeringCompetition.legacyOvertimePolicy: OvertimePolicy
    get() = overtimePolicyFor(ranksByScore)

/**
 * Разбирает политики КВ из API/БД. Если новых полей нет (соревнование до формата на дистанции),
 * политика вида формата по умолчанию берётся из [legacy], другого вида — умолчание (по баллам —
 * штраф очками, по времени — справочное КВ). Штраф очками для дистанций по времени не применяется.
 *
 * @return пара (по времени, по баллам)
 */
fun resolveOvertimePolicies(
    legacy: OvertimePolicy?,
    time: OvertimePolicy?,
    score: OvertimePolicy?,
    competitionRanksByScore: Boolean
): Pair<OvertimePolicy, OvertimePolicy> {
    val timePolicy = time ?: legacy.takeIf { !competitionRanksByScore } ?: OvertimePolicy.IGNORE
    val scorePolicy = score ?: legacy.takeIf { competitionRanksByScore } ?: OvertimePolicy.SCORE_PENALTY
    return timePolicy.forTimeCourses() to scorePolicy
}

/** Политика, допустимая для дистанций по времени: штраф очками там не имеет смысла. */
fun OvertimePolicy.forTimeCourses(): OvertimePolicy =
    if (this == OvertimePolicy.SCORE_PENALTY) OvertimePolicy.IGNORE else this

/**
 * Форматы групп соревнования — для экранов, где группы с разными форматами показываются вместе
 * (результаты, протоколы). Группа без записи получает формат соревнования [competition].
 */
data class GroupFormats(
    val competition: CourseFormat = CourseFormat.DEFAULT,
    val byGroupId: Map<Long, CourseFormat> = emptyMap()
) {
    /** Формат группы [groupId]. */
    fun of(groupId: Long): CourseFormat = byGroupId[groupId] ?: competition

    companion object {
        /** Форматы групп [groups] по их дистанциям из [distances]. */
        fun of(
            competition: OrienteeringCompetition?,
            groups: List<ParticipantGroup>,
            distances: List<Distance>
        ): GroupFormats {
            val competitionFormat = competition.defaultFormatOrDefault
            return GroupFormats(
                competition = competitionFormat,
                byGroupId = groups.associate { it.groupId to it.effectiveFormat(distances, competitionFormat) }
            )
        }
    }
}

/**
 * Форматы групп, загруженных с сервера (публичные экраны): ключ — серверный id группы
 * ([ParticipantGroup.remoteId]), дистанция ищется по [Distance.remoteId] — у таких моделей
 * локальные id не заполнены.
 */
fun remoteGroupFormats(
    competition: OrienteeringCompetition?,
    groups: List<ParticipantGroup>,
    distances: List<Distance>
): GroupFormats {
    val competitionFormat = competition.defaultFormatOrDefault
    return GroupFormats(
        competition = competitionFormat,
        byGroupId = groups.mapNotNull { group ->
            val remoteId = group.remoteId ?: return@mapNotNull null
            val distance = distances.firstOrNull { it.remoteId != null && it.remoteId == group.distanceId }
            remoteId to distance.effectiveFormat(competitionFormat)
        }.toMap()
    )
}

/**
 * Нужно ли группе своё КВ: на дистанции с баллами при политике «штраф очками» штраф считается от
 * КВ, и если у соревнования КВ нет ([competitionControlTimeMinutes] == null), без КВ группы он не
 * начислится. Та же проверка в web (`isGroupControlTimeRequired`).
 */
fun isGroupControlTimeRequired(
    groupFormat: CourseFormat,
    scoreOvertimePolicy: OvertimePolicy,
    competitionControlTimeMinutes: Int?
): Boolean = groupFormat.ranksByScore && scoreOvertimePolicy == OvertimePolicy.SCORE_PENALTY &&
    competitionControlTimeMinutes == null

/**
 * Группы, которым нужно своё КВ, а оно не задано (см. [isGroupControlTimeRequired]): например,
 * добавленные «Стандартными группами» или созданные до смены формата дистанции на «по выбору».
 */
fun List<ParticipantGroup>.withoutRequiredControlTime(
    distances: List<Distance>,
    competitionFormat: CourseFormat,
    scoreOvertimePolicy: OvertimePolicy,
    competitionControlTimeMinutes: Int?
): List<ParticipantGroup> = filter { group ->
    (group.timeLimitMinutes ?: 0) <= 0 && isGroupControlTimeRequired(
        groupFormat = group.effectiveFormat(distances, competitionFormat),
        scoreOvertimePolicy = scoreOvertimePolicy,
        competitionControlTimeMinutes = competitionControlTimeMinutes
    )
}

/** Группы соревнования [competition] без нужного КВ — см. [withoutRequiredControlTime]. */
fun List<ParticipantGroup>.withoutRequiredControlTime(
    distances: List<Distance>,
    competition: OrienteeringCompetition
): List<ParticipantGroup> = withoutRequiredControlTime(
    distances = distances,
    competitionFormat = competition.defaultFormat,
    scoreOvertimePolicy = competition.scoreOvertimePolicy,
    competitionControlTimeMinutes = competition.controlTimeMinutes
)
