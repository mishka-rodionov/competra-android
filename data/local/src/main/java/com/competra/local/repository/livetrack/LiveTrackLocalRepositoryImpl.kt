package com.competra.local.repository.livetrack

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.competra.domain.models.livetrack.LiveTrackCloseReason
import com.competra.domain.models.livetrack.LiveTrackStatus
import com.competra.domain.models.livetrack.RunnerTrackBatch
import com.competra.domain.models.livetrack.RunnerTrackPoint
import com.competra.domain.models.livetrack.RunnerTrackSession
import com.competra.domain.repository.livetrack.LiveTrackLocalRepository
import com.competra.local.dao.livetrack.LiveTrackDao
import com.competra.local.entities.livetrack.LiveTrackPointEntity
import com.competra.local.entities.livetrack.LiveTrackSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.liveTrackPrefs by preferencesDataStore(name = "live_track_prefs")
private val CONSENT_KEY = booleanPreferencesKey("publish_consent_given")

/** Room-реализация [LiveTrackLocalRepository]; согласие хранится в DataStore (не секрет). */
class LiveTrackLocalRepositoryImpl(
    private val dao: LiveTrackDao,
    private val context: Context
) : LiveTrackLocalRepository {

    override fun observeSession(competitionId: String): Flow<RunnerTrackSession?> =
        dao.observeLatestSession(competitionId).map { it?.toDomain() }

    override suspend fun findUnfinishedSession(): RunnerTrackSession? = dao.findUnfinishedSession()?.toDomain()

    override suspend fun getSession(sessionId: String): RunnerTrackSession? = dao.getSession(sessionId)?.toDomain()

    override suspend fun upsertStartedSession(session: RunnerTrackSession, serverLastBatchSeq: Int) =
        dao.upsertStarted(session.toEntity(inFlightBatchSeq = null), serverLastBatchSeq)

    override suspend fun addPoint(sessionId: String, point: RunnerTrackPoint) = dao.insertPoint(
        LiveTrackPointEntity(sessionId = sessionId, t = point.t, lat = point.lat, lon = point.lon, accuracy = point.accuracy, batchSeq = null)
    )

    override suspend fun nextBatch(sessionId: String, maxPoints: Int, allowEmpty: Boolean): RunnerTrackBatch? =
        dao.nextBatch(sessionId, maxPoints, allowEmpty)?.let { (seq, points) ->
            RunnerTrackBatch(seq, points.map { RunnerTrackPoint(it.t, it.lat, it.lon, it.accuracy) })
        }

    override suspend fun ackBatch(sessionId: String, batchSeq: Int) = dao.ackBatch(sessionId, batchSeq)

    override fun observePendingCount(sessionId: String): Flow<Int> = dao.observePendingCount(sessionId)

    override suspend fun hasPending(sessionId: String): Boolean =
        dao.pendingCount(sessionId) > 0 || dao.getSession(sessionId)?.inFlightBatchSeq != null

    override suspend fun updateStatus(sessionId: String, status: LiveTrackStatus, closeReason: LiveTrackCloseReason?) =
        dao.updateStatus(sessionId, status.name, closeReason?.name)

    override suspend fun markStopRequested(sessionId: String) = dao.markStopRequested(sessionId)

    override suspend fun markStopDelivered(sessionId: String) = dao.markStopDelivered(sessionId)

    override suspend fun dropPending(sessionId: String) {
        dao.deletePoints(sessionId)
        dao.setInFlight(sessionId, null)
    }

    override suspend fun isConsentGiven(): Boolean = context.liveTrackPrefs.data.first()[CONSENT_KEY] ?: false

    override suspend fun setConsentGiven() {
        context.liveTrackPrefs.edit { it[CONSENT_KEY] = true }
    }
}

private fun LiveTrackSessionEntity.toDomain() = RunnerTrackSession(
    sessionId = sessionId,
    competitionId = competitionId,
    status = runCatching { LiveTrackStatus.valueOf(status) }.getOrDefault(LiveTrackStatus.ACTIVE),
    closeReason = closeReason?.let { runCatching { LiveTrackCloseReason.valueOf(it) }.getOrNull() },
    startedAt = startedAt,
    deadlineAt = deadlineAt,
    uploadIntervalSec = uploadIntervalSec,
    lastAckedBatchSeq = lastAckedBatchSeq,
    stopRequested = stopRequested,
    stopDelivered = stopDelivered
)

private fun RunnerTrackSession.toEntity(inFlightBatchSeq: Int?) = LiveTrackSessionEntity(
    sessionId = sessionId,
    competitionId = competitionId,
    status = status.name,
    closeReason = closeReason?.name,
    startedAt = startedAt,
    deadlineAt = deadlineAt,
    uploadIntervalSec = uploadIntervalSec,
    lastAckedBatchSeq = lastAckedBatchSeq,
    inFlightBatchSeq = inFlightBatchSeq,
    stopRequested = stopRequested,
    stopDelivered = stopDelivered
)
