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

    /**
     * Регистрация на сервере сессии, созданной телефоном (с согласием): [clientSessionId] — её UUID,
     * [recordedFrom] — когда началась запись. Повтор с тем же id возвращает ту же сессию.
     */
    suspend fun start(competitionId: String, clientSessionId: String, recordedFrom: Long): Result<LiveTrackStartResult>

    /** Отправка батча; батчи отправляются строго последовательно. */
    suspend fun sendPoints(sessionId: String, batchSeq: Int, points: List<RunnerTrackPoint>): Result<LiveTrackAck>

    /** Ручная остановка сессии. */
    suspend fun stop(sessionId: String): Result<LiveTrackAck>
}
