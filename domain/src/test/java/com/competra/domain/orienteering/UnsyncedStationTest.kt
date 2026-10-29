package com.competra.domain.orienteering

import com.competra.domain.models.orienteering.SplitTime
import com.competra.domain.models.orienteering.unsyncedStationControlPoints
import org.junit.Assert.assertEquals
import org.junit.Test

class UnsyncedStationTest {

    private val readAt = 1_793_000_000_000L

    @Test
    fun `punches before reading are fine`() {
        val splits = listOf(SplitTime(31, readAt - 600_000), SplitTime(32, readAt - 1_000))
        assertEquals(emptyList<Int>(), unsyncedStationControlPoints(splits, readAt))
    }

    @Test
    fun `small clock drift within tolerance is ignored`() {
        val splits = listOf(SplitTime(245, readAt + 30_000))
        assertEquals(emptyList<Int>(), unsyncedStationControlPoints(splits, readAt))
    }

    @Test
    fun `punches from the future are reported once per control point in chip order`() {
        val future = readAt + 22L * 24 * 3600 * 1000
        val splits = listOf(
            SplitTime(56, readAt - 300_000),
            SplitTime(31, future),
            SplitTime(56, readAt - 100_000),
            SplitTime(31, future),
            SplitTime(40, readAt + 120_000),
        )
        assertEquals(listOf(31, 40), unsyncedStationControlPoints(splits, readAt))
    }
}
