package com.competra.domain.models.rating

data class RatingStandingBreakdownEntry(
    val competitionId: String,
    val place: Int?,
    val points: Int,
    /** Запись участника в протоколе этого соревнования. */
    val participantId: String = ""
)

/** Строка итоговой таблицы рейтинга — участник, суммарные очки и разбивка по стартам. */
data class RatingStanding(
    val participantKey: String,
    val displayName: String,
    val totalPoints: Int,
    val rank: Int,
    val breakdown: List<RatingStandingBreakdownEntry>,
    /** Аккаунт, если строка собрана по привязанным участникам; null — ручные записи без аккаунта. */
    val userId: String? = null,
    val firstName: String = "",
    val lastName: String = ""
)

/**
 * Таблица рейтинга по группе. [possibleDuplicates] — группы participantKey, похожих на одного
 * человека (та же фамилия, «М.» ~ «Михаил»), их организатор может объединить.
 */
data class RatingStandings(
    val standings: List<RatingStanding>,
    val possibleDuplicates: List<List<String>>
)
