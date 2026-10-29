package com.competra.local.entities.livetrack

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Локальная сессия онлайн-трекинга бегуна (см. `RunnerTrackSession`).
 *
 * @property inFlightBatchSeq Номер назначенного, но ещё не подтверждённого сервером батча:
 *   до подтверждения он отправляется повторно с тем же номером (идемпотентность на сервере).
 * @property isRegistered Сервер уже знает о сессии (до v59 сессии создавал сервер — `true`).
 * @property rejectMessage Текст отказа сервера, если он не принял сессию.
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
    val stopDelivered: Boolean,
    val isRegistered: Boolean = true,
    val rejectMessage: String? = null
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

/**
 * Соревнование, на которое зарегистрирован пользователь (см. `MyStart`): кэш списка
 * `competitions/registered` для карточки «Сегодня старт» без сети.
 */
@Entity(tableName = "my_starts")
data class MyStartEntity(
    @PrimaryKey val competitionId: String,
    val title: String,
    val startDate: Long,
    val endDate: Long?,
    val timeZoneId: String,
    val address: String?,
    val status: String,
    val controlTimeMinutes: Int?
)
