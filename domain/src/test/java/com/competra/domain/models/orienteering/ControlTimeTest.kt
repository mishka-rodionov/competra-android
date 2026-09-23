package com.competra.domain.models.orienteering

import com.competra.domain.models.ResultStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlTimeTest {

    /** КВ 90 минут в секундах: totalTime результата хранится в секундах. */
    private val ninetyMinutes = 90 * 60L

    @Test
    fun `group control time overrides competition default`() {
        assertEquals(60, effectiveControlTimeMinutes(groupLimitMinutes = 60, competitionControlTimeMinutes = 90))
    }

    @Test
    fun `group without own control time inherits competition default`() {
        assertEquals(90, effectiveControlTimeMinutes(groupLimitMinutes = null, competitionControlTimeMinutes = 90))
    }

    @Test
    fun `control time is absent when neither level defines it`() {
        assertNull(effectiveControlTimeMinutes(groupLimitMinutes = null, competitionControlTimeMinutes = null))
    }

    @Test
    fun `result slower than control time is overtime`() {
        assertTrue(isOvertime(totalTimeSeconds = ninetyMinutes + 1, controlTimeMinutes = 90))
    }

    @Test
    fun `result exactly at control time fits the limit`() {
        assertFalse(isOvertime(totalTimeSeconds = ninetyMinutes, controlTimeMinutes = 90))
    }

    @Test
    fun `result without total time is never overtime`() {
        assertFalse(isOvertime(totalTimeSeconds = null, controlTimeMinutes = 90))
    }

    @Test
    fun `disqualify policy turns slow result into overtime`() {
        assertEquals(
            ResultStatus.OVERTIME,
            applyOvertimePolicy(
                status = ResultStatus.FINISHED,
                totalTimeSeconds = ninetyMinutes + 1,
                controlTimeMinutes = 90,
                policy = OvertimePolicy.DISQUALIFY
            )
        )
    }

    @Test
    fun `ignore policy keeps slow result finished`() {
        assertEquals(
            ResultStatus.FINISHED,
            applyOvertimePolicy(
                status = ResultStatus.FINISHED,
                totalTimeSeconds = ninetyMinutes + 1,
                controlTimeMinutes = 90,
                policy = OvertimePolicy.IGNORE
            )
        )
    }

    @Test
    fun `score penalty policy does not disqualify`() {
        assertEquals(
            ResultStatus.FINISHED,
            applyOvertimePolicy(
                status = ResultStatus.FINISHED,
                totalTimeSeconds = ninetyMinutes + 1,
                controlTimeMinutes = 90,
                policy = OvertimePolicy.SCORE_PENALTY
            )
        )
    }

    @Test
    fun `overtime returns to finished when policy changes to ignore`() {
        assertEquals(
            ResultStatus.FINISHED,
            applyOvertimePolicy(
                status = ResultStatus.OVERTIME,
                totalTimeSeconds = ninetyMinutes + 1,
                controlTimeMinutes = 90,
                policy = OvertimePolicy.IGNORE
            )
        )
    }

    @Test
    fun `overtime returns to finished when control time is raised`() {
        assertEquals(
            ResultStatus.FINISHED,
            applyOvertimePolicy(
                status = ResultStatus.OVERTIME,
                totalTimeSeconds = ninetyMinutes + 1,
                controlTimeMinutes = 120,
                policy = OvertimePolicy.DISQUALIFY
            )
        )
    }

    @Test
    fun `disqualified result stays disqualified regardless of control time`() {
        assertEquals(
            ResultStatus.DSQ,
            applyOvertimePolicy(
                status = ResultStatus.DSQ,
                totalTimeSeconds = ninetyMinutes + 1,
                controlTimeMinutes = 90,
                policy = OvertimePolicy.DISQUALIFY
            )
        )
    }

    @Test
    fun `unknown policy falls back to ignore`() {
        assertEquals(OvertimePolicy.IGNORE, OvertimePolicy.fromString("SOMETHING_ELSE"))
        assertEquals(OvertimePolicy.IGNORE, OvertimePolicy.fromString(null))
    }
}
