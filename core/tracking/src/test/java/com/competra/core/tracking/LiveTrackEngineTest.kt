package com.competra.core.tracking

import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsScreen
import com.competra.analytics.AnalyticsTracker
import com.competra.domain.models.livetrack.LiveTrackAck
import com.competra.domain.models.livetrack.LiveTrackCloseReason
import com.competra.domain.models.livetrack.LiveTrackRejectedException
import com.competra.domain.models.livetrack.LiveTrackStartResult
import com.competra.domain.models.livetrack.LiveTrackStatus
import com.competra.domain.models.livetrack.RunnerTrackBatch
import com.competra.domain.models.livetrack.RunnerTrackPoint
import com.competra.domain.models.livetrack.RunnerTrackSession
import com.competra.domain.repository.livetrack.LiveTrackLocalRepository
import com.competra.domain.repository.livetrack.LiveTrackRemoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LiveTrackEngineTest {

    /** Повторяет семантику Room-реализации (LiveTrackDao) в памяти. */
    private class FakeLocal : LiveTrackLocalRepository {
        val sessions = LinkedHashMap<String, RunnerTrackSession>()
        val inFlight = HashMap<String, Int?>()
        val points = mutableListOf<Pair<RunnerTrackPoint, Int?>>() // точка → batchSeq
        var consent = false

        override fun observeSession(competitionId: String): Flow<RunnerTrackSession?> = flowOf(null)
        override suspend fun findUnfinishedSession() = sessions.values.lastOrNull()
        override suspend fun getSession(sessionId: String) = sessions[sessionId]
        override suspend fun upsertStartedSession(session: RunnerTrackSession, serverLastBatchSeq: Int) {
            val existing = sessions[session.sessionId]
            sessions[session.sessionId] = session.copy(
                startedAt = existing?.startedAt ?: session.startedAt,
                lastAckedBatchSeq = maxOf(existing?.lastAckedBatchSeq ?: 0, session.lastAckedBatchSeq)
            )
            ackBatch(session.sessionId, serverLastBatchSeq)
        }
        override suspend fun addPoint(sessionId: String, point: RunnerTrackPoint) { points += point to null }
        override suspend fun nextBatch(sessionId: String, maxPoints: Int, allowEmpty: Boolean): RunnerTrackBatch? {
            inFlight[sessionId]?.let { seq -> return RunnerTrackBatch(seq, points.filter { it.second == seq }.map { it.first }) }
            val free = points.withIndex().filter { it.value.second == null }.take(maxPoints)
            if (free.isEmpty() && !allowEmpty) return null
            val seq = sessions.getValue(sessionId).lastAckedBatchSeq + 1
            free.forEach { points[it.index] = it.value.first to seq }
            inFlight[sessionId] = seq
            return RunnerTrackBatch(seq, free.map { it.value.first })
        }
        override suspend fun ackBatch(sessionId: String, batchSeq: Int) {
            points.removeAll { it.second != null && it.second!! <= batchSeq }
            val s = sessions[sessionId] ?: return
            sessions[sessionId] = s.copy(lastAckedBatchSeq = maxOf(s.lastAckedBatchSeq, batchSeq))
            if ((inFlight[sessionId] ?: Int.MAX_VALUE) <= batchSeq) inFlight[sessionId] = null
        }
        override fun observePendingCount(sessionId: String): Flow<Int> = flowOf(points.size)
        override suspend fun hasPending(sessionId: String) = points.isNotEmpty() || inFlight[sessionId] != null
        override suspend fun updateStatus(sessionId: String, status: LiveTrackStatus, closeReason: LiveTrackCloseReason?) {
            sessions[sessionId] = sessions.getValue(sessionId).copy(status = status, closeReason = closeReason)
        }
        override suspend fun markStopRequested(sessionId: String) {
            sessions[sessionId] = sessions.getValue(sessionId).copy(stopRequested = true)
        }
        override suspend fun markStopDelivered(sessionId: String) {
            sessions[sessionId] = sessions.getValue(sessionId).copy(stopDelivered = true)
        }
        override suspend fun dropPending(sessionId: String) { points.clear(); inFlight[sessionId] = null }
        override suspend fun isConsentGiven() = consent
        override suspend fun setConsentGiven() { consent = true }
    }

    private class FakeRemote : LiveTrackRemoteRepository {
        var serverStatus = LiveTrackStatus.ACTIVE
        var serverReason: LiveTrackCloseReason? = null
        var failure: Throwable? = null
        var serverLastBatchSeq = 0
        val received = mutableListOf<Pair<Int, List<RunnerTrackPoint>>>()
        var stopCalls = 0

        override suspend fun start(competitionId: String) = Result.success(
            LiveTrackStartResult("s1", LiveTrackStatus.ACTIVE, null, serverLastBatchSeq, 10, 9_999_999_999L)
        )
        override suspend fun sendPoints(sessionId: String, batchSeq: Int, points: List<RunnerTrackPoint>): Result<LiveTrackAck> {
            failure?.let { return Result.failure(it) }
            if (batchSeq > serverLastBatchSeq) {
                received += batchSeq to points
                serverLastBatchSeq = batchSeq
            }
            return Result.success(LiveTrackAck(serverStatus, serverReason, batchSeq))
        }
        override suspend fun stop(sessionId: String): Result<LiveTrackAck> {
            failure?.let { return Result.failure(it) }
            stopCalls++
            serverStatus = LiveTrackStatus.STOPPED
            serverReason = LiveTrackCloseReason.MANUAL
            return Result.success(LiveTrackAck(serverStatus, serverReason, serverLastBatchSeq))
        }
    }

    private class FakeAnalytics : AnalyticsTracker {
        val events = mutableListOf<AnalyticsEvent>()
        override fun trackScreen(screen: AnalyticsScreen) = Unit
        override fun trackEvent(event: AnalyticsEvent) { events += event }
        override fun setUserId(userId: String?) = Unit
    }

    private var now = 1_790_000_000_000L
    private val local = FakeLocal()
    private val remote = FakeRemote()
    private val analytics = FakeAnalytics()
    private val engine = LiveTrackEngine(local, remote, LiveTrackRecorderState(), analytics) { now }

    private fun point(dt: Long) = RunnerTrackPoint(now + dt, 55.0, 37.0, 5f)

    @Test
    fun `batch lost in transit is resent with the same number and not duplicated`() = runBlocking {
        engine.start("c1")
        engine.recordPoint("s1", point(1))
        engine.recordPoint("s1", point(2))

        remote.failure = IOException("no network")
        assertEquals(UploadStep.RETRY, engine.uploadStep("s1"))
        engine.recordPoint("s1", point(3))

        remote.failure = null
        assertEquals(UploadStep.SENT, engine.uploadStep("s1"))
        assertEquals(UploadStep.SENT, engine.uploadStep("s1"))

        assertEquals(listOf(1 to 2, 2 to 1), remote.received.map { it.first to it.second.size })
        assertFalse(local.hasPending("s1"))
    }

    @Test
    fun `resume after reinstall continues numbering after the server's last batch`() = runBlocking {
        remote.serverLastBatchSeq = 57

        engine.start("c1")
        engine.recordPoint("s1", point(1))
        engine.uploadStep("s1")

        assertEquals(58, remote.received.single().first)
        assertTrue(analytics.events.any { it.eventName == "live_track_started" && it.params["is_resumed"] == true })
    }

    @Test
    fun `server close stops recording but the buffered tail is still sent`() = runBlocking {
        engine.start("c1")
        engine.recordPoint("s1", point(1))
        engine.recordPoint("s1", point(2))
        remote.serverStatus = LiveTrackStatus.FINISHED
        remote.serverReason = LiveTrackCloseReason.RESULT_SAVED

        engine.uploadStep("s1")
        engine.recordPoint("s1", point(3)) // после закрытия не пишется

        assertFalse(local.getSession("s1")!!.isRecording)
        assertFalse(local.hasPending("s1"))
        assertEquals(UploadStep.DONE, engine.uploadStep("s1"))
        val stopped = analytics.events.single { it.eventName == "live_track_stopped" }
        assertEquals("result_saved", stopped.params["reason"])
    }

    @Test
    fun `stop offline keeps the buffer and delivers points then stop when network returns`() = runBlocking {
        engine.start("c1")
        engine.recordPoint("s1", point(1))
        engine.requestStop("s1")

        remote.failure = IOException("offline")
        assertEquals(UploadStep.RETRY, engine.uploadStep("s1"))
        assertEquals(0, remote.stopCalls)

        remote.failure = null
        assertEquals(UploadStep.SENT, engine.uploadStep("s1"))
        assertEquals(UploadStep.DONE, engine.uploadStep("s1"))
        assertEquals(1, remote.stopCalls)
        assertTrue(local.getSession("s1")!!.stopDelivered)
        assertEquals(1, analytics.events.count { it.eventName == "live_track_stopped" })
    }

    @Test
    fun `session unknown to the server drops the buffer and finishes`() = runBlocking {
        engine.start("c1")
        engine.recordPoint("s1", point(1))
        remote.failure = LiveTrackRejectedException(404, "Сессия трекинга не найдена")

        assertEquals(UploadStep.DONE, engine.uploadStep("s1"))
        assertFalse(local.hasPending("s1"))
        assertEquals(LiveTrackCloseReason.SESSION_LOST, local.getSession("s1")!!.closeReason)
    }

    @Test
    fun `heartbeat sends an empty batch when idle so the client learns the server status`() = runBlocking {
        engine.start("c1")
        assertEquals(UploadStep.SENT, engine.uploadStep("s1")) // первый — сразу пульс
        assertEquals(UploadStep.IDLE, engine.uploadStep("s1")) // до следующего пульса ждём

        now += 31_000
        remote.serverStatus = LiveTrackStatus.TIMED_OUT
        remote.serverReason = LiveTrackCloseReason.CONTROL_TIME
        assertEquals(UploadStep.SENT, engine.uploadStep("s1"))
        assertEquals(LiveTrackStatus.TIMED_OUT, local.getSession("s1")!!.status)
    }
}
