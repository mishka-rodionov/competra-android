package com.competra.domain.models.livetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSpeedTest {

    /** Метров в градусе широты. */
    private val metersPerDegree = 111_195.0

    /** Точки на север с шагом [stepMs]; [speeds] — скорость (м/с) на каждом шаге. */
    private fun points(speeds: List<Double>, stepMs: Long = 2_000, startT: Long = 0, startLat: Double = 55.0): List<ViewerTrackPoint> {
        var lat = startLat
        var t = startT
        val result = mutableListOf(ViewerTrackPoint(t, lat, 37.0))
        speeds.forEach { v ->
            t += stepMs
            lat += v * stepMs / 1000.0 / metersPerDegree
            result += ViewerTrackPoint(t, lat, 37.0)
        }
        return result
    }

    private fun track(points: List<ViewerTrackPoint>) = ViewerTrack(
        sessionId = "s",
        participantId = "p",
        displayName = "Участник",
        groupName = null,
        startNumber = null,
        status = LiveTrackStatus.FINISHED,
        closeReason = LiveTrackCloseReason.RESULT_SAVED,
        startedAt = 0,
        lastPointAt = points.lastOrNull()?.t,
        points = points
    )

    @Test
    fun `constant speed gives one middle-colored chunk`() {
        val profile = track(points(List(60) { 3.0 })).speedProfile()!!

        assertEquals(1, profile.chunks.size)
        assertEquals((SPEED_COLOR_STEPS - 1) / 2, profile.chunks.single().level)
        assertEquals(3.0, profile.slowSpeed, 0.05)
        assertEquals(3.0, profile.fastSpeed, 0.05)
    }

    @Test
    fun `slow part is red and fast part is green`() {
        val profile = track(points(List(60) { 1.0 } + List(60) { 4.0 })).speedProfile()!!

        assertEquals(0, profile.chunks.first().level)
        assertEquals(SPEED_COLOR_STEPS - 1, profile.chunks.last().level)
        assertEquals(1.0, profile.slowSpeed, 0.05)
        assertEquals(4.0, profile.fastSpeed, 0.05)
    }

    @Test
    fun `chunks share junction points and cover the whole track`() {
        val source = points(List(60) { 1.0 } + List(60) { 4.0 })
        val chunks = track(source).speedProfile()!!.chunks

        chunks.zipWithNext().forEach { (a, b) -> assertEquals(a.points.last(), b.points.first()) }
        assertEquals(source.first(), chunks.first().points.first())
        assertEquals(source.last(), chunks.last().points.last())
    }

    @Test
    fun `gap longer than track gap is not bridged`() {
        val first = points(List(20) { 3.0 })
        val second = points(List(20) { 3.0 }, startT = first.last().t + TRACK_GAP_MS + 1_000, startLat = 55.01)
        val chunks = track(first + second).speedProfile()!!.chunks

        assertTrue(chunks.none { chunk -> first.last() in chunk.points && second.first() in chunk.points })
    }

    @Test
    fun `single gps jump does not create a speed spike`() {
        val source = points(List(60) { 3.0 }).toMutableList()
        // Точка «улетела» на 40 м в сторону — сглаженная скорость в ней берётся по краям окна.
        source[30] = source[30].copy(lon = source[30].lon + 40 / (metersPerDegree * 0.5736))
        val speeds = smoothedSpeeds(source, SPEED_WINDOW_MS)

        assertEquals(3.0, speeds[30]!!, 0.05)
    }

    @Test
    fun `too few points give no profile`() {
        assertNull(track(points(emptyList())).speedProfile())
    }
}
