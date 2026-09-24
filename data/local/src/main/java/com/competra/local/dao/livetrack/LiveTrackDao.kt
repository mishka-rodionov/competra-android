package com.competra.local.dao.livetrack

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.competra.local.entities.livetrack.LiveTrackPointEntity
import com.competra.local.entities.livetrack.LiveTrackSessionEntity
import kotlinx.coroutines.flow.Flow

/** DAO буфера онлайн-трекинга бегуна. */
@Dao
interface LiveTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: LiveTrackSessionEntity)

    @Query("SELECT * FROM live_track_runner_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): LiveTrackSessionEntity?

    @Query("SELECT * FROM live_track_runner_sessions WHERE competitionId = :competitionId ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestSession(competitionId: String): Flow<LiveTrackSessionEntity?>

    /** Сессия, которой есть что делать: пишет GPS или ещё не сообщила серверу об остановке. */
    @Query(
        "SELECT * FROM live_track_runner_sessions " +
            "WHERE (status = 'ACTIVE' AND stopRequested = 0) OR (stopRequested = 1 AND stopDelivered = 0) " +
            "OR sessionId IN (SELECT DISTINCT sessionId FROM live_track_runner_points) " +
            "ORDER BY startedAt DESC LIMIT 1"
    )
    suspend fun findUnfinishedSession(): LiveTrackSessionEntity?

    @Insert
    suspend fun insertPoint(point: LiveTrackPointEntity)

    @Query("SELECT * FROM live_track_runner_points WHERE sessionId = :sessionId AND batchSeq = :batchSeq ORDER BY id")
    suspend fun pointsOfBatch(sessionId: String, batchSeq: Int): List<LiveTrackPointEntity>

    @Query("SELECT id FROM live_track_runner_points WHERE sessionId = :sessionId AND batchSeq IS NULL ORDER BY id LIMIT :limit")
    suspend fun unassignedPointIds(sessionId: String, limit: Int): List<Long>

    @Query("UPDATE live_track_runner_points SET batchSeq = :batchSeq WHERE id IN (:ids)")
    suspend fun assignBatch(ids: List<Long>, batchSeq: Int)

    @Query("UPDATE live_track_runner_sessions SET inFlightBatchSeq = :batchSeq WHERE sessionId = :sessionId")
    suspend fun setInFlight(sessionId: String, batchSeq: Int?)

    @Query("DELETE FROM live_track_runner_points WHERE sessionId = :sessionId AND batchSeq IS NOT NULL AND batchSeq <= :batchSeq")
    suspend fun deleteAckedPoints(sessionId: String, batchSeq: Int)

    @Query(
        "UPDATE live_track_runner_sessions SET lastAckedBatchSeq = MAX(lastAckedBatchSeq, :batchSeq), " +
            "inFlightBatchSeq = CASE WHEN inFlightBatchSeq IS NOT NULL AND inFlightBatchSeq <= :batchSeq THEN NULL ELSE inFlightBatchSeq END " +
            "WHERE sessionId = :sessionId"
    )
    suspend fun markAcked(sessionId: String, batchSeq: Int)

    @Query("SELECT COUNT(*) FROM live_track_runner_points WHERE sessionId = :sessionId")
    fun observePendingCount(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM live_track_runner_points WHERE sessionId = :sessionId")
    suspend fun pendingCount(sessionId: String): Int

    @Query("UPDATE live_track_runner_sessions SET status = :status, closeReason = :closeReason WHERE sessionId = :sessionId")
    suspend fun updateStatus(sessionId: String, status: String, closeReason: String?)

    @Query("UPDATE live_track_runner_sessions SET stopRequested = 1 WHERE sessionId = :sessionId")
    suspend fun markStopRequested(sessionId: String)

    @Query("UPDATE live_track_runner_sessions SET stopDelivered = 1 WHERE sessionId = :sessionId")
    suspend fun markStopDelivered(sessionId: String)

    @Query("DELETE FROM live_track_runner_points WHERE sessionId = :sessionId")
    suspend fun deletePoints(sessionId: String)

    /**
     * Батч к отправке. Назначение номера и точек — в одной транзакции: иначе при убийстве процесса
     * между шагами часть точек осталась бы с номером, которого сессия «не помнит».
     */
    @Transaction
    suspend fun nextBatch(sessionId: String, maxPoints: Int, allowEmpty: Boolean): Pair<Int, List<LiveTrackPointEntity>>? {
        val session = getSession(sessionId) ?: return null
        session.inFlightBatchSeq?.let { seq -> return seq to pointsOfBatch(sessionId, seq) }
        val ids = unassignedPointIds(sessionId, maxPoints)
        if (ids.isEmpty() && !allowEmpty) return null
        val seq = session.lastAckedBatchSeq + 1
        if (ids.isNotEmpty()) assignBatch(ids, seq)
        setInFlight(sessionId, seq)
        return seq to pointsOfBatch(sessionId, seq)
    }

    /** Подтверждение батча (и всех с меньшим номером — на случай потерянных ответов). */
    @Transaction
    suspend fun ackBatch(sessionId: String, batchSeq: Int) {
        deleteAckedPoints(sessionId, batchSeq)
        markAcked(sessionId, batchSeq)
    }

    /**
     * Согласование с сервером при старте/возобновлении: всё, что не новее [serverLastBatchSeq],
     * сервер уже принял; незаконченный батч с большим номером остаётся и уйдёт повторно.
     * Флаги остановки берутся из [session]: явный старт означает «писать снова».
     */
    @Transaction
    suspend fun upsertStarted(session: LiveTrackSessionEntity, serverLastBatchSeq: Int) {
        val existing = getSession(session.sessionId)
        upsertSession(
            session.copy(
                startedAt = existing?.startedAt ?: session.startedAt,
                lastAckedBatchSeq = maxOf(existing?.lastAckedBatchSeq ?: 0, session.lastAckedBatchSeq),
                inFlightBatchSeq = existing?.inFlightBatchSeq
            )
        )
        ackBatch(session.sessionId, serverLastBatchSeq)
    }
}
