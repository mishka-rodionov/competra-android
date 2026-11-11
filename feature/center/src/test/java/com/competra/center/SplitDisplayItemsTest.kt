package com.competra.center

import com.competra.center.presentation.read_card.SplitDisplayItem
import com.competra.center.presentation.read_card.alignInCourseOrder
import com.competra.center.presentation.read_card.buildSplitDisplayItems
import com.competra.domain.models.orienteering.SplitTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Разметка таблицы сплитов на экране считывания чипа — [buildSplitDisplayItems]. */
class SplitDisplayItemsTest {

    private fun splits(vararg cps: Int) = cps.mapIndexed { i, cp -> SplitTime(cp, 1_000L * (i + 1)) }

    /** (КП, позиция по дистанции, не в том порядке, лишняя) для каждой фактической отметки. */
    private fun marks(items: List<SplitDisplayItem>) = items.filterIsInstance<SplitDisplayItem.Actual>()
        .map { listOf(it.split.controlPoint, it.distanceOrdinal, it.isOutOfOrder, it.isExtra) }

    @Test
    fun `early punch of a later control is out of order, the in-order ones are not`() {
        // Дистанция 46, 47, 48, финиш 245; участник взял 48 раньше 47, потом снова 48.
        val items = buildSplitDisplayItems(
            rawSplits = splits(240, 46, 48, 47, 48, 245),
            expectedCpOrder = listOf(46, 47, 48, 245),
            startControlPoint = 240,
            startTime = 0L,
        )
        assertEquals(
            listOf(
                listOf(240, null, false, false),
                listOf(46, 1, false, false),
                listOf(48, null, true, false),
                listOf(47, 2, false, false),
                listOf(48, 3, false, false),
                listOf(245, 4, false, false),
            ),
            marks(items)
        )
        assertEquals(0, items.count { it is SplitDisplayItem.Missed })
    }

    @Test
    fun `control taken only out of order keeps its position and is highlighted`() {
        val items = buildSplitDisplayItems(
            rawSplits = splits(47, 46, 48),
            expectedCpOrder = listOf(46, 47, 48),
        )
        assertEquals(
            listOf(
                listOf(47, 2, true, false),
                listOf(46, 1, false, false),
                listOf(48, 3, false, false),
            ),
            marks(items)
        )
    }

    @Test
    fun `foreign control is extra and missing control is listed`() {
        val items = buildSplitDisplayItems(
            rawSplits = splits(46, 99, 48),
            expectedCpOrder = listOf(46, 47, 48),
        )
        assertEquals(
            listOf(
                listOf(46, 1, false, false),
                listOf(99, null, false, true),
                listOf(48, 3, false, false),
            ),
            marks(items)
        )
        assertEquals(listOf(47), items.filterIsInstance<SplitDisplayItem.Missed>().map { it.cpNumber })
    }

    @Test
    fun `alignment prefers earliest punches like the course check`() {
        // Две отметки 48 — берётся первая, как при проверке дистанции (финишное время по ней).
        assertEquals(listOf(0 to 0, 1 to 1), alignInCourseOrder(listOf(46, 48), listOf(46, 48, 48)))
    }

    @Test
    fun `alignment keeps the longest in-order run`() {
        // 47, 48, 49 по порядку, а 46 — в конце: не в порядке только 46.
        assertEquals(
            listOf(1 to 0, 2 to 1, 3 to 2),
            alignInCourseOrder(listOf(46, 47, 48, 49), listOf(47, 48, 49, 46))
        )
    }

    @Test
    fun `repeat of a course control in by-choice is marked as repeat, foreign control is not`() {
        val items = buildSplitDisplayItems(
            rawSplits = splits(46, 47, 48, 47, 99),
            expectedCpOrder = listOf(46, 47, 48),
            isByChoice = true,
        ).filterIsInstance<SplitDisplayItem.Actual>()
        assertEquals(listOf(false, false, false, true, false), items.map { it.isRepeat })
        assertEquals(listOf(false, false, false, true, true), items.map { it.isExtra })
    }
}
