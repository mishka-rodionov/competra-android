package com.competra.local.entities.livetrack

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Локальная сессия онлайн-трекинга бегуна (см. `RunnerTrackSession`).
 *
 * @property inFlightBatchSeq Номер назначенного, но ещё не подтверждённого сервером батча:
 *   до подтверждения он отправляется повторно с тем же номером (идемпотентность на сервере).
 */
@Entity(tableName = "live_track_runner_sessions", indices = [Index("competitionId")])
data class LiveTrackSessionEntity(
    @PrimaryKey val sessionId: String,
    val competitionId: String,
    val status: String,
    val closeReason: String?,
    val startedAt: Long,
    val deadlineAt: Long,
    val uploadIntervalSec: Int,
    val lastAckedBatchSeq: Int,
    val inFlightBatchSeq: Int?,
    val stopRequested: Boolean,
    val stopDelivered: Boolean
)

/**
 * Неотправленная точка трека. [batchSeq] = `null` — ещё не попала в батч.
 */
@Entity(tableName = "live_track_runner_points", indices = [Index(value = ["sessionId", "batchSeq"])])
data class LiveTrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val t: Long,
    val lat: Double,
    val lon: Double,
    val accuracy: Float?,
    val batchSeq: Int?
)
