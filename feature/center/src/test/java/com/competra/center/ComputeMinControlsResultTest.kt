package com.competra.center

import com.competra.center.presentation.read_card.computeMinControlsResult
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.ControlPoint
import com.competra.domain.models.orienteering.ControlPointRole
import com.competra.domain.models.orienteering.SplitTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Проверка результата для формата «по выбору» с минимумом КП — [computeMinControlsResult].
 */
class ComputeMinControlsResultTest {

    private val startTime = 0L

    private fun cp(number: Int, role: ControlPointRole = ControlPointRole.ORDINARY) =
        ControlPoint(number = number, role = role)

    private fun split(cp: Int, timestampSeconds: Long) = SplitTime(cp, timestampSeconds * 1000L)

    /** 4 КП дистанции и финиш 100. */
    private val course = listOf(cp(31), cp(32), cp(33), cp(34), cp(100, role = ControlPointRole.FINISH))

    @Test
    fun `взят минимум КП в любом порядке — финиш без баллов`() {
        val actual = listOf(split(33, 60), split(31, 120), split(34, 180), split(100, 240))

        val result = computeMinControlsResult(course, actual, startTime, minControlsCount = 3)

        assertEquals(ResultStatus.FINISHED, result.status)
        assertNull(result.totalScore)
        assertEquals(listOf(33, 31, 34, 100), result.validSplits.map { it.controlPoint })
    }

    @Test
    fun `меньше минимума — снятие с причиной`() {
        val actual = listOf(split(31, 60), split(32, 120), split(100, 240))

        val result = computeMinControlsResult(course, actual, startTime, minControlsCount = 3)

        assertEquals(ResultStatus.DSQ, result.status)
        assertEquals("Взято 2 из 3 КП", result.message)
    }

    @Test
    fun `финишная станция не считается взятым КП`() {
        val actual = listOf(split(31, 60), split(32, 120), split(100, 240))

        val result = computeMinControlsResult(course, actual, startTime, minControlsCount = 3)

        assertEquals(ResultStatus.DSQ, result.status)
    }

    @Test
    fun `повторная отметка одного КП засчитывается один раз`() {
        val actual = listOf(split(31, 60), split(31, 90), split(32, 120), split(100, 240))

        val result = computeMinControlsResult(course, actual, startTime, minControlsCount = 3)

        assertEquals("Взято 2 из 3 КП", result.message)
    }

    @Test
    fun `без минимума нужно взять все КП`() {
        val actual = listOf(split(31, 60), split(32, 120), split(33, 180), split(100, 240))

        val result = computeMinControlsResult(course, actual, startTime, minControlsCount = null)

        assertEquals(ResultStatus.DSQ, result.status)
        assertEquals("Взято 3 из 4 КП", result.message)
    }

    @Test
    fun `пропущен обязательный КП — снятие, даже если минимум набран`() {
        val withRequired = listOf(cp(31), cp(32), cp(33, role = ControlPointRole.REQUIRED), cp(34))
        val actual = listOf(split(31, 60), split(32, 120), split(34, 180))

        val result = computeMinControlsResult(withRequired, actual, startTime, minControlsCount = 3)

        assertEquals(ResultStatus.DSQ, result.status)
        assertEquals("Пропущен обязательный КП 33", result.message)
    }

    @Test
    fun `отметки до старта и чужие КП не засчитываются`() {
        val start = 1000L
        val actual = listOf(split(31, 900), split(32, 1060), split(77, 1100), split(33, 1200))

        val result = computeMinControlsResult(course, actual, start * 1000L, minControlsCount = 3)

        assertEquals("Взято 2 из 3 КП", result.message)
    }

    @Test
    fun `минимум больше числа КП дистанции требует взять все`() {
        val actual = listOf(split(31, 60), split(32, 120), split(33, 180), split(34, 200))

        val result = computeMinControlsResult(course, actual, startTime, minControlsCount = 10)

        assertEquals(ResultStatus.FINISHED, result.status)
    }
}
