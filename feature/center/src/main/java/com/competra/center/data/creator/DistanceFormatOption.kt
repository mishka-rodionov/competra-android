package com.competra.center.data.creator

import com.competra.domain.models.orienteering.ByChoiceMode
import com.competra.domain.models.orienteering.CourseFormat
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.effectiveFormat

/**
 * Вариант формата в редакторе дистанции. Направление и режим «по выбору» сведены в один список,
 * чтобы организатор выбирал формат одним полем. Новая дистанция получает формат соревнования.
 *
 * Вариант, совпадающий с форматом соревнования, сохраняется как «как у соревнования» (`null` у
 * [Distance.direction] / [Distance.byChoiceMode], см. [toDistanceFields]) — тогда смена формата
 * соревнования меняет и такие дистанции. Отдельного пункта «Как у соревнования» в списке нет.
 *
 * @property format формат дистанции с этим вариантом
 */
enum class DistanceFormatOption(val format: CourseFormat) {
    FORWARD(CourseFormat(OrienteeringDirection.FORWARD)),
    BY_CHOICE_SCORE(CourseFormat(OrienteeringDirection.BY_CHOICE, ByChoiceMode.SCORE)),
    BY_CHOICE_MIN_CONTROLS(CourseFormat(OrienteeringDirection.BY_CHOICE, ByChoiceMode.MIN_CONTROLS)),

    /**
     * Маркированная трасса пока не поддержана: вариант не предлагается, а показывается только у
     * дистанции или соревнования, где он уже выбран (см. [selectable]).
     */
    MARKING(CourseFormat(OrienteeringDirection.MARKING));

    /** Подпись варианта. */
    val label: String get() = format.label()

    /**
     * Поля дистанции для сохранения: `null` (как у соревнования), если вариант совпадает с форматом
     * соревнования [competitionFormat], иначе — явный формат.
     */
    fun toDistanceFields(competitionFormat: CourseFormat): Pair<OrienteeringDirection?, ByChoiceMode?> =
        if (format.sameAs(competitionFormat)) {
            null to null
        } else {
            format.direction to format.byChoiceMode.takeIf { format.direction == OrienteeringDirection.BY_CHOICE }
        }

    companion object {
        /**
         * Вариант для дистанции [distance] — по её эффективному формату; новая дистанция (`null`)
         * получает формат соревнования [competitionFormat].
         */
        fun of(distance: Distance?, competitionFormat: CourseFormat): DistanceFormatOption {
            val format = distance.effectiveFormat(competitionFormat)
            return entries.firstOrNull { it.format.sameAs(format) } ?: FORWARD
        }

        /** Варианты для списка: без маркированной трассы, если она не выбрана сейчас ([current]). */
        fun selectable(current: DistanceFormatOption): List<DistanceFormatOption> =
            entries.filter { it != MARKING || it == current }
    }
}

/** Человекочитаемое название формата: «В заданном направлении», «По выбору — по баллам» и т.д. */
fun CourseFormat.label(): String = when (direction) {
    OrienteeringDirection.FORWARD -> "В заданном направлении"
    OrienteeringDirection.MARKING -> "Маркированная трасса"
    OrienteeringDirection.BY_CHOICE -> when (byChoiceMode) {
        ByChoiceMode.SCORE -> "По выбору — по баллам"
        ByChoiceMode.MIN_CONTROLS -> "По выбору — по количеству КП"
    }
}
