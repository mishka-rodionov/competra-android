package com.competra.domain.repository.livetrack

import com.competra.domain.models.livetrack.LiveTrackAck
import com.competra.domain.models.livetrack.LiveTrackStartResult
import com.competra.domain.models.livetrack.RunnerTrackPoint

/**
 * API онлайн-трекинга бегуна (отдельный процесс на сервере, `/api/live-track`).
 *
 * Ошибки: [com.competra.domain.models.livetrack.LiveTrackRejectedException] — сервер отклонил
 * запрос (4xx), повторять не нужно; любое другое исключение — сеть/5xx, повторить позже.
 */
interface LiveTrackRemoteRepository {

    /** Старт или возобновление сессии текущего пользователя в соревновании (с согласием). */
    suspend fun start(competitionId: String): Result<LiveTrackStartResult>

    /** Отправка батча; батчи отправляются строго последовательно. */
    suspend fun sendPoints(sessionId: String, batchSeq: Int, points: List<RunnerTrackPoint>): Result<LiveTrackAck>

    /** Ручная остановка сессии. */
    suspend fun stop(sessionId: String): Result<LiveTrackAck>
}
