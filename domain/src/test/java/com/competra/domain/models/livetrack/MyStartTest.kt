package com.competra.domain.models.livetrack

import com.competra.domain.models.orienteering.CompetitionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class MyStartTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, moscow).toInstant().toEpochMilli()

    private val start = MyStart(
        competitionId = "c1", title = "Осенний лес", startDate = at(28, 10), endDate = null,
        timeZoneId = "Europe/Moscow", address = null, status = CompetitionStatus.REGISTRATION_CLOSED, controlTimeMinutes = null
    )

    @Test
    fun `today is the competition day in its own time zone`() {
        assertTrue(start.isToday(at(28, 0)))
        assertTrue(start.isToday(at(28, 23)))
        assertFalse(start.isToday(at(27, 23)))
        assertFalse(start.isToday(at(29, 0)))
    }

    @Test
    fun `multi-day competition is today on every day, finished one never`() {
        val multiDay = start.copy(endDate = at(30, 15))
        assertTrue(multiDay.isToday(at(29, 12)))
        assertTrue(multiDay.isToday(at(30, 20)))
        assertFalse(start.copy(status = CompetitionStatus.FINISHED).isToday(at(28, 12)))
    }

    @Test
    fun `local deadline is at least six hours from the competition start, longer with control time`() {
        val early = at(28, 9)
        assertEquals(at(28, 16), start.localDeadline(early))
        val withLongControlTime = start.copy(controlTimeMinutes = 400)
        assertEquals(at(28, 10) + TimeUnit.MINUTES.toMillis(460), withLongControlTime.localDeadline(early))
        assertEquals(at(28, 18), start.localDeadline(at(28, 12)))
    }
}
