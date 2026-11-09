package com.competra.center

import com.competra.center.presentation.read_card.computeFinishOnlyResult
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.models.orienteering.SplitTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Проверка результата без дистанции в режиме «Регистрация на финише» — [computeFinishOnlyResult].
 */
class ComputeFinishOnlyResultTest {

    private val start = PunchingSystem.SPORTIDUINO.defaultStartStationCode!!
    private val finish = PunchingSystem.SPORTIDUINO.defaultFinishStationCode!!

    @Test
    fun `sportiduino default station codes are 240 and 245`() {
        assertEquals(240, start)
        assertEquals(245, finish)
        assertNull(PunchingSystem.PENCIL.defaultFinishStationCode)
    }

    @Test
    fun `any punches count, finish is the first finish punch after start`() {
        val splits = listOf(
            SplitTime(start, 1_000L),
            SplitTime(31, 2_000L),
            SplitTime(77, 3_000L),
            SplitTime(finish, 4_000L),
            SplitTime(finish, 5_000L),
        )
        val result = computeFinishOnlyResult(splits, startTime = 1_000L, finishStationCode = finish)
        assertEquals(ResultStatus.FINISHED, result.status)
        assertEquals(4_000L, result.validSplits.last().timestamp)
        assertEquals(4, result.validSplits.size)
    }

    @Test
    fun `punches before start are ignored`() {
        val splits = listOf(
            SplitTime(finish, 500L),
            SplitTime(start, 1_000L),
            SplitTime(31, 2_000L),
        )
        val result = computeFinishOnlyResult(splits, startTime = 1_000L, finishStationCode = finish)
        assertEquals(ResultStatus.DSQ, result.status)
        assertEquals("Нет отметки финиша", result.message)
    }

    @Test
    fun `no finish code means no finish`() {
        val splits = listOf(SplitTime(start, 1_000L), SplitTime(245, 2_000L))
        val result = computeFinishOnlyResult(splits, startTime = 1_000L, finishStationCode = null)
        assertEquals(ResultStatus.DSQ, result.status)
    }
}
