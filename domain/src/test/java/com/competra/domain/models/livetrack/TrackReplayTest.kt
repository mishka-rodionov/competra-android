package com.competra.domain.models.livetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackReplayTest {

    private fun track(vararg points: ViewerTrackPoint) = ViewerTrack(
        sessionId = "s",
        participantId = "p",
        displayName = "Участник",
        groupName = null,
        startNumber = null,
        status = LiveTrackStatus.FINISHED,
        closeReason = LiveTrackCloseReason.RESULT_SAVED,
        startedAt = points.first().t,
        lastPointAt = points.last().t,
        points = points.toList()
    )

    private fun p(t: Long, lat: Double, lon: Double = 37.0) = ViewerTrackPoint(t, lat, lon)

    @Test
    fun `position is interpolated between fixes`() {
        val replay = track(p(0, 55.0), p(2_000, 55.002)).toReplay(null, null)!!

        val position = replay.positionAt(1_000)

        assertEquals(ReplayRunnerState.RUNNING, position.state)
        assertEquals(55.001, position.point.lat, 1e-9)
    }

    @Test
    fun `gap keeps last known point without data`() {
        val replay = track(p(0, 55.0), p(2_000, 55.001), p(62_000, 55.01)).toReplay(null, null)!!

        val position = replay.positionAt(30_000)

        assertEquals(ReplayRunnerState.NO_DATA, position.state)
        assertEquals(55.001, position.point.lat, 1e-9)
    }

    @Test
    fun `track is trimmed to result start and finish`() {
        val replay = track(p(0, 55.0), p(10_000, 55.001), p(20_000, 55.002), p(30_000, 55.003), p(40_000, 55.004))
            .toReplay(startTime = 10_000, finishTime = 30_000)!!

        assertEquals(listOf(10_000L, 20_000L, 30_000L), replay.points.map { it.t })
        assertEquals(20_000L, replay.duration)
        assertEquals(ReplayRunnerState.NOT_STARTED, replay.positionAt(5_000).state)
        assertEquals(ReplayRunnerState.FINISHED, replay.positionAt(35_000).state)
    }

    @Test
    fun `result outside the track falls back to the whole track`() {
        val replay = track(p(0, 55.0), p(10_000, 55.001)).toReplay(startTime = 100_000, finishTime = 200_000)!!

        assertEquals(0L, replay.startAt)
        assertEquals(10_000L, replay.finishAt)
    }

    @Test
    fun `mass start time is counted from own start`() {
        val replay = track(p(60_000, 55.0), p(70_000, 55.001)).toReplay(null, null)!!

        assertEquals(65_000L, replay.timeAt(5_000, ReplayTimeMode.MASS_START))
        assertEquals(5_000L, replay.timeAt(5_000, ReplayTimeMode.REAL_TIME))
    }

    @Test
    fun `tail ends at current position and skips old points`() {
        val points = (0..100).map { p(it * 2_000L, 55.0 + it * 0.0001) }.toTypedArray()
        val replay = track(*points).toReplay(null, null)!!

        val tail = replay.tail(101_000, length = 10_000).single()

        assertTrue(tail.all { it.t > 91_000 })
        assertEquals(101_000L, tail.last().t)
    }

    @Test
    fun `range covers all runners in both modes`() {
        val a = track(p(0, 55.0), p(10_000, 55.001)).toReplay(null, null)!!
        val b = track(p(5_000, 55.0), p(30_000, 55.001)).toReplay(null, null)!!

        assertEquals(0L..30_000L, listOf(a, b).replayRange(ReplayTimeMode.REAL_TIME))
        assertEquals(0L..25_000L, listOf(a, b).replayRange(ReplayTimeMode.MASS_START))
        assertNull(emptyList<ReplayTrack>().replayRange(ReplayTimeMode.MASS_START))
    }
}
