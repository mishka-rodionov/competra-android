package com.competra.domain.models.orienteering

/**
 * Как подводится итог в формате «по выбору» ([OrienteeringDirection.BY_CHOICE]); для остальных
 * направлений не используется. Свободный порядок взятия КП общий для обоих режимов — отличаются
 * только баллы и способ распределения мест.
 */
enum class ByChoiceMode {
    /** Score-О: у КП есть стоимость, места по сумме баллов (убывание), тай-брейк по времени. */
    SCORE,

    /**
     * Свободный порядок с минимумом КП: нужно взять не меньше [Distance.minControlsCount] КП
     * (null — все) и все обязательные ([ControlPointRole.REQUIRED]), места — по времени.
     * Баллов нет: [OrienteeringResult.totalScore] остаётся null.
     */
    MIN_CONTROLS;

    companion object {
        val DEFAULT = SCORE

        /** Неизвестное значение (данные с более новой версии) трактуем как умолчание, а не падаем. */
        fun fromString(raw: String?): ByChoiceMode =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

/**
 * true, если места считаются по сумме баллов (score-О), а не по времени: только [OrienteeringDirection.BY_CHOICE]
 * в режиме [ByChoiceMode.SCORE]. Та же функция есть на сервере (eSport, ByChoiceMode.kt).
 */
fun ranksByScore(direction: OrienteeringDirection, byChoiceMode: ByChoiceMode): Boolean =
    direction == OrienteeringDirection.BY_CHOICE && byChoiceMode == ByChoiceMode.SCORE

/** true, если места в этом соревновании считаются по сумме баллов — см. [ranksByScore]. */
val OrienteeringCompetition.ranksByScore: Boolean
    get() = ranksByScore(direction, byChoiceMode)
