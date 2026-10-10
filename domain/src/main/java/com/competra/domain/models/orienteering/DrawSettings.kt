package com.competra.domain.models.orienteering

/**
 * Режим проведённой жеребьёвки стартового протокола. Тот же enum есть на сервере (eSport, DrawMode.kt).
 */
enum class DrawMode {
    /** Общая: в одну минуту стартует один участник, группы чередуются. */
    GENERAL,

    /** По группам: в одну минуту стартует не больше одного участника группы. */
    GROUP,

    /**
     * По дистанциям: в одну минуту не больше [DrawSettings.corridors] участников и не больше
     * одного с каждой дистанции, старты одной дистанции разнесены минимум на [DrawSettings.gap] интервалов.
     */
    DISTANCE;

    companion object {
        /** Неизвестное значение (данные с более новой версии) — null, как будто режим не сохранён. */
        fun fromStringOrNull(raw: String?): DrawMode? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * Параметры проведённой жеребьёвки. По ним дозаявка опоздавшего участника подбирает свободную
 * стартовую минуту по тем же правилам, что и сама жеребьёвка.
 *
 * @property mode Режим жеребьёвки.
 * @property corridors Число коридоров (только [DrawMode.DISTANCE]).
 * @property gap Минимальный зазор между стартами одной дистанции в интервалах (только [DrawMode.DISTANCE]).
 */
data class DrawSettings(
    val mode: DrawMode,
    val corridors: Int? = null,
    val gap: Int? = null
)
