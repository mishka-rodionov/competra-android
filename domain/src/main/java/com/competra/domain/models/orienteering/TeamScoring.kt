package com.competra.domain.models.orienteering

/** Способ подсчёта командного зачёта в группе. */
enum class TeamScoringMethod {
    /** Сумма очков за места N лучших участников команды ([placePoints]). */
    POINTS,

    /** Сумма времени N лучших участников команды; финишировавших меньше N — вне зачёта. */
    TIME;

    companion object {
        fun fromString(raw: String?): TeamScoringMethod = entries.firstOrNull { it.name == raw } ?: POINTS
    }
}

/** Общий командный зачёт — по полу группы или по всем группам. */
enum class TeamOverallScope {
    MEN, WOMEN, ALL;

    companion object {
        fun fromString(raw: String?): TeamOverallScope? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * Настройки командного зачёта соревнования (docs/specs/team-scoring.md). Задаются абстрактно, без
 * ссылок на группы: в каждой группе считается свой зачёт ([groupMethod], [groupCountedResults]),
 * общие зачёты ([overallScopes]) складывают баллы за командные места в группах.
 * В [OrienteeringCompetition.teamScoring] `null` — командного зачёта нет.
 */
data class TeamScoring(
    val groupMethod: TeamScoringMethod = TeamScoringMethod.POINTS,
    val groupCountedResults: Int = DEFAULT_COUNTED_RESULTS,
    val overallScopes: Set<TeamOverallScope> = emptySet()
) {
    companion object {
        const val DEFAULT_COUNTED_RESULTS = 3
    }
}

/**
 * Очки за место по фиксированной таблице рейтинга (как RatingPointsTable в eSport, основа — IOF World Cup):
 * 1–9 места — 100, 80, 60, 50, 45, 40, 37, 35, 33; 10–40 — `41 − место`; ниже 40-го и без места — 0.
 */
fun placePoints(place: Int): Int = when {
    place < 1 -> 0
    place <= FIXED_PLACE_POINTS.size -> FIXED_PLACE_POINTS[place - 1]
    place <= 40 -> 41 - place
    else -> 0
}

private val FIXED_PLACE_POINTS = listOf(100, 80, 60, 50, 45, 40, 37, 35, 33)
