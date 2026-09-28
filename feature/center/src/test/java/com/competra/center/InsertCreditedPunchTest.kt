package com.competra.center

import com.competra.center.presentation.read_card.insertCreditedPunch
import com.competra.domain.models.orienteering.SplitTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Проверка вставки «засчитанного» КП — [insertCreditedPunch].
 */
class InsertCreditedPunchTest {

    /** Дистанция М21 (КП по порядку + финиш 245), на которой воспроизвёлся баг. */
    private val distance = listOf(31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 44, 43, 42, 41, 45, 46, 47, 48, 49, 245)

    /** Чип участника: вместо 35 отметка 38, вместо 44 — 41; КП 35 и 44 пропущены. */
    private val chip = listOf(31, 32, 33, 34, 38, 36, 37, 38, 39, 40, 41, 43, 42, 41, 45, 46, 47, 48, 49, 245)
        .mapIndexed { i, cp -> SplitTime(controlPoint = cp, timestamp = 1_000L * (i + 1)) }

    private fun ordinalOf(cp: Int) = distance.indexOf(cp) + 1

    /** Та же последовательная проверка, что и checkControlPointOrderPro: true — все КП найдены по порядку. */
    private fun passesOrderCheck(splits: List<SplitTime>): Boolean {
        var searchIndex = 0
        for (cp in distance) {
            val found = (searchIndex until splits.size).firstOrNull { splits[it].controlPoint == cp } ?: return false
            searchIndex = found + 1
        }
        return true
    }

    private fun credit(splits: List<SplitTime>, cp: Int, startControlPoint: Int? = null) = insertCreditedPunch(
        rawSplits = splits,
        expectedCpNumbers = distance,
        cpNumber = cp,
        distanceOrdinal = ordinalOf(cp),
        startTime = 0L,
        startControlPoint = startControlPoint,
    )

    @Test
    fun `credited punch goes right after previous distance CP with its time plus 1 ms`() {
        val result = credit(chip, 35)

        assertEquals(35, result[4].controlPoint)
        assertEquals(chip[3].timestamp + 1, result[4].timestamp)
        assertEquals(chip.size + 1, result.size)
    }

    @Test
    fun `crediting both missed CPs passes order check in any order`() {
        assertEquals(true, passesOrderCheck(credit(credit(chip, 35), 44)))
        assertEquals(true, passesOrderCheck(credit(credit(chip, 44), 35)))
    }

    @Test
    fun `result stays strictly chronological`() {
        val result = credit(credit(chip, 44), 35)

        assertEquals(true, result.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
    }

    @Test
    fun `consecutive missed CPs keep distance order regardless of credit order`() {
        val splits = listOf(31, 32, 35, 245).mapIndexed { i, cp -> SplitTime(cp, 1_000L * (i + 1)) }
        val shortDistance = listOf(31, 32, 33, 34, 35, 245)
        fun creditShort(s: List<SplitTime>, cp: Int) =
            insertCreditedPunch(s, shortDistance, cp, shortDistance.indexOf(cp) + 1, startTime = 0L)

        val result = creditShort(creditShort(splits, 34), 33)

        assertEquals(listOf(31, 32, 33, 34, 35, 245), result.map { it.controlPoint })
        // Сортировка по времени (как на сервере) не должна менять порядок.
        assertEquals(result, result.sortedBy { it.timestamp })
        assertEquals(true, result.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
    }

    @Test
    fun `first CP credited with start time`() {
        val splits = listOf(32, 245).mapIndexed { i, cp -> SplitTime(cp, 1_000L * (i + 1)) }

        val result = insertCreditedPunch(splits, listOf(31, 32, 245), 31, 1, startTime = 500L)

        assertEquals(SplitTime(31, 501L), result.first())
    }

    @Test
    fun `first CP credited after start station punch`() {
        val splits = listOf(240, 32, 245).mapIndexed { i, cp -> SplitTime(cp, 1_000L * (i + 1)) }

        val result = insertCreditedPunch(splits, listOf(31, 32, 245), 31, 1, startTime = 0L, startControlPoint = 240)

        assertEquals(listOf(240, 31, 32, 245), result.map { it.controlPoint })
        assertEquals(1_001L, result[1].timestamp)
    }
}
