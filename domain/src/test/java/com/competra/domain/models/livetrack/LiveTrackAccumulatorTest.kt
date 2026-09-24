package com.competra.domain.models.livetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTrackAccumulatorTest {

    private fun track(
        id: String,
        status: LiveTrackStatus = LiveTrackStatus.ACTIVE,
        lastPointAt: Long? = null,
        vararg t: Long
    ) = ViewerTrack(
        sessionId = id, participantId = "p-$id", displayName = "Участник $id", groupName = "М21", startNumber = null,
        status = status, closeReason = null, startedAt = 0, lastPointAt = lastPointAt,
        points = t.map { ViewerTrackPoint(it, 55.0, 37.0) }
    )

    @Test
    fun `late points are merged into order without duplicates`() {
        val acc = LiveTrackAccumulator()
        acc.applySnapshot(LiveTrackSnapshot("e:2", reset = false, serverTime = 10, sessions = listOf(track("a", t = longArrayOf(1_000, 3_000)))))
        acc.applySnapshot(LiveTrackSnapshot("e:4", reset = false, serverTime = 20, sessions = listOf(track("a", t = longArrayOf(2_000, 3_000)))))

        assertEquals(listOf(1_000L, 2_000L, 3_000L), acc.tracks().single().points.map { it.t })
        assertEquals("e:4", acc.cursor)
        assertEquals(20L, acc.serverTime)
    }

    @Test
    fun `reset replaces points of sessions in the snapshot and keeps archived ones`() {
        val acc = LiveTrackAccumulator()
        acc.applyArchive(listOf(track("old", LiveTrackStatus.FINISHED, t = longArrayOf(1, 2))))
        acc.applySnapshot(LiveTrackSnapshot("e:1", false, 0, listOf(track("a", t = longArrayOf(5, 6, 7)))))

        acc.applySnapshot(LiveTrackSnapshot("f:9", reset = true, serverTime = 0, sessions = listOf(track("a", t = longArrayOf(6, 5)))))

        val byId = acc.tracks().associateBy { it.sessionId }
        assertEquals(listOf(5L, 6L), byId.getValue("a").points.map { it.t })
        assertEquals(2, byId.getValue("old").points.size)
    }

    @Test
    fun `archive of a closed session completes the accumulated track and updates status`() {
        val acc = LiveTrackAccumulator()
        acc.applySnapshot(LiveTrackSnapshot("e:1", false, 0, listOf(track("a", t = longArrayOf(10)))))

        acc.applyArchive(listOf(track("a", LiveTrackStatus.FINISHED, t = longArrayOf(5, 10, 15))))

        val result = acc.tracks().single()
        assertEquals(LiveTrackStatus.FINISHED, result.status)
        assertEquals(listOf(5L, 10L, 15L), result.points.map { it.t })
        assertFalse(acc.hasActive())
    }

    @Test
    fun `segments break on gaps longer than 30 s and tail filter trims old points`() {
        val t = track("a", t = longArrayOf(0, 10_000, 20_000, 60_000, 70_000))

        assertEquals(listOf(3, 2), t.segments().map { it.size })
        assertEquals(listOf(2), t.segments(since = 55_000).map { it.size })
    }

    @Test
    fun `active participant without points for over a minute is stale`() {
        assertTrue(track("a", lastPointAt = 1_000).isStale(serverTime = 62_000))
        assertFalse(track("a", lastPointAt = 1_000).isStale(serverTime = 30_000))
        assertFalse(track("a", LiveTrackStatus.FINISHED, lastPointAt = 1_000).isStale(serverTime = 999_999))
    }
}
