package com.competra.core.tracking

import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.domain.models.livetrack.LiveTrackAck
import com.competra.domain.models.livetrack.LiveTrackCloseReason
import com.competra.domain.models.livetrack.LiveTrackRejectedException
import com.competra.domain.models.livetrack.LiveTrackStatus
import com.competra.domain.models.livetrack.RunnerTrackPoint
import com.competra.domain.models.livetrack.RunnerTrackSession
import com.competra.domain.repository.livetrack.LiveTrackLocalRepository
import com.competra.domain.repository.livetrack.LiveTrackRemoteRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Максимум точек в батче (лимит сервера — 300). */
private const val MAX_POINTS_PER_BATCH = 300

/** Пустой батч-«пульс» раз в столько, если точек нет: так клиент узнаёт, что сервер закрыл сессию. */
private val HEARTBEAT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30)

/** Период отправки до первого ответа сервера (дальше — его `uploadIntervalSec`). */
private const val DEFAULT_UPLOAD_INTERVAL_SEC = 10

/** Пауза перед повтором после сбоя сети: 10 → 20 → 40 → … ≤ 120 с. */
private val BACKOFF_MS = listOf(10_000L, 20_000L, 40_000L, 80_000L, 120_000L)

/** Результат одного шага отправки. */
enum class UploadStep {
    /** Батч отправлен — можно сразу отправлять следующий. */
    SENT,

    /** Отправлять нечего, запись идёт — ждать интервал. */
    IDLE,

    /** Сбой сети/сервера — повторить с паузой. */
    RETRY,

    /** Сессия закрыта, буфер досылан и сервер знает об остановке — работа закончена. */
    DONE
}

/**
 * Логика онлайн-трекинга бегуна без Android-зависимостей: сессия создаётся на телефоне (запись идёт
 * и без сети), при первой связи регистрируется на сервере, точки копятся в буфере и уходят
 * последовательными батчами, затем остановка. Сервис и воркер досылки лишь вызывают её.
 *
 * Батчи уходят строго по одному (сервер считает батч с номером не больше принятого повтором);
 * [uploadMutex] не даёт сервису и воркеру отправлять одновременно.
 */
class LiveTrackEngine(
    private val local: LiveTrackLocalRepository,
    private val remote: LiveTrackRemoteRepository,
    private val recorderState: LiveTrackRecorderState,
    private val analytics: AnalyticsTracker,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() }
) {
    private val uploadMutex = Mutex()
    private var lastSendAt = 0L

    /**
     * Включает трекинг без обращения к серверу (согласие на публикацию бегун уже дал в UI): если по
     * соревнованию уже идёт запись — продолжает её, иначе создаёт новую сессию на телефоне.
     * Сервер узнает о ней при первой связи ([uploadStep]).
     *
     * @param deadlineAt Когда запись остановится сама, если сервер так и не ответит.
     * @param offline Сети в момент включения нет — только для аналитики.
     */
    suspend fun startOrResume(
        competitionId: String,
        deadlineAt: Long,
        source: AnalyticsEvent.LiveTrackStartSource,
        offline: Boolean
    ): RunnerTrackSession {
        local.setConsentGiven()
        local.observeSession(competitionId).first()?.takeIf { it.isRecording }?.let { recording ->
            analytics.trackEvent(AnalyticsEvent.LiveTrackStarted(competitionId, isResumed = true, source = source, offline = offline))
            return recording
        }
        val session = RunnerTrackSession(
            sessionId = newSessionId(),
            competitionId = competitionId,
            status = LiveTrackStatus.ACTIVE,
            closeReason = null,
            startedAt = clock(),
            deadlineAt = deadlineAt,
            uploadIntervalSec = DEFAULT_UPLOAD_INTERVAL_SEC,
            lastAckedBatchSeq = 0,
            stopRequested = false,
            stopDelivered = false,
            isRegistered = false
        )
        local.createLocalSession(session)
        analytics.trackEvent(AnalyticsEvent.LiveTrackStarted(competitionId, isResumed = false, source = source, offline = offline))
        return session
    }

    /** Записывает точку, если сессия ещё пишет. */
    suspend fun recordPoint(sessionId: String, point: RunnerTrackPoint) {
        val session = local.getSession(sessionId) ?: return
        if (!session.isRecording) return
        local.addPoint(sessionId, point)
        recorderState.onFix(point.t, point.accuracy)
    }

    /**
     * Бегун нажал «Стоп» (или истёк локальный срок — [reason]): запись прекращается, буфер и
     * остановка уйдут на сервер при первой возможности.
     */
    suspend fun requestStop(
        sessionId: String,
        reason: AnalyticsEvent.LiveTrackStopReason = AnalyticsEvent.LiveTrackStopReason.MANUAL
    ) {
        val session = local.getSession(sessionId) ?: return
        if (session.stopRequested) return
        local.markStopRequested(sessionId)
        if (session.status == LiveTrackStatus.ACTIVE) reportStopped(session, reason)
    }

    /**
     * Отправляет батчи, пока есть что отправлять; ждёт интервал, пока запись идёт; делает паузы
     * после сбоев. Возвращается, когда работа закончена ([UploadStep.DONE]) или [shouldContinue]
     * вернул `false` (например, истёк лимит времени на досылку).
     */
    suspend fun runUploads(sessionId: String, shouldContinue: () -> Boolean = { true }): UploadStep {
        var failures = 0
        while (shouldContinue()) {
            when (val step = uploadStep(sessionId)) {
                UploadStep.SENT -> failures = 0
                UploadStep.IDLE -> {
                    failures = 0
                    delay(TimeUnit.SECONDS.toMillis((local.getSession(sessionId)?.uploadIntervalSec ?: 10).toLong()))
                }
                UploadStep.RETRY -> delay(BACKOFF_MS[minOf(failures++, BACKOFF_MS.lastIndex)])
                UploadStep.DONE -> return step
            }
        }
        return UploadStep.RETRY
    }

    /** Один шаг отправки: регистрация сессии, батч, остановка или вывод, что делать больше нечего. */
    suspend fun uploadStep(sessionId: String): UploadStep = uploadMutex.withLock {
        val session = local.getSession(sessionId) ?: return UploadStep.DONE
        if (!session.isRegistered) return register(session)
        val heartbeatDue = session.isRecording && clock() - lastSendAt >= HEARTBEAT_INTERVAL_MS
        val batch = local.nextBatch(sessionId, MAX_POINTS_PER_BATCH, allowEmpty = heartbeatDue)
        if (batch != null) {
            return remote.sendPoints(sessionId, batch.batchSeq, batch.points).fold(
                onSuccess = { ack ->
                    local.ackBatch(sessionId, batch.batchSeq)
                    lastSendAt = clock()
                    recorderState.onUploaded(lastSendAt)
                    applyServerStatus(session, ack)
                    UploadStep.SENT
                },
                onFailure = { error -> handleFailure(session, error, dropBatchSeq = batch.batchSeq) }
            )
        }
        when {
            session.isRecording -> UploadStep.IDLE
            session.stopRequested && !session.stopDelivered && session.status == LiveTrackStatus.ACTIVE ->
                remote.stop(sessionId).fold(
                    onSuccess = { ack ->
                        local.updateStatus(sessionId, ack.status, ack.closeReason)
                        local.markStopDelivered(sessionId)
                        UploadStep.DONE
                    },
                    onFailure = { error -> handleFailure(session, error, dropBatchSeq = null) }
                )
            else -> {
                if (session.stopRequested && !session.stopDelivered) local.markStopDelivered(sessionId)
                UploadStep.DONE
            }
        }
    }

    /**
     * Сообщает серверу о сессии, начатой на телефоне. Сервер может сразу вернуть её закрытой (о треке
     * узнали после результата или КВ) — тогда запись прекращается, а буфер досылается как обычно.
     * Отказ (не зарегистрирован, трек старше 48 ч…) — точки удаляются, причина видна бегуну.
     */
    private suspend fun register(session: RunnerTrackSession): UploadStep =
        remote.start(session.competitionId, session.sessionId, session.startedAt).fold(
            onSuccess = { started ->
                if (started.sessionId != session.sessionId) {
                    // Сервер без поддержки clientSessionId — ждём обновления, точки не теряем.
                    recorderState.onUploadFailed()
                    UploadStep.RETRY
                } else {
                    local.upsertStartedSession(
                        session.copy(
                            status = started.status,
                            closeReason = started.closeReason,
                            deadlineAt = started.deadlineAt,
                            uploadIntervalSec = started.uploadIntervalSec,
                            isRegistered = true
                        ),
                        serverLastBatchSeq = started.lastBatchSeq
                    )
                    if (session.isRecording && started.status != LiveTrackStatus.ACTIVE) {
                        reportStopped(session, started.closeReason.toStopReason())
                    }
                    UploadStep.SENT
                }
            },
            onFailure = { error ->
                if (error is LiveTrackRejectedException) {
                    local.markRejected(session.sessionId, error.message)
                    analytics.trackEvent(AnalyticsEvent.LiveTrackRejected(session.competitionId, error.httpCode))
                    if (session.isRecording) reportStopped(session, AnalyticsEvent.LiveTrackStopReason.SESSION_LOST)
                    UploadStep.DONE
                } else {
                    recorderState.onUploadFailed()
                    UploadStep.RETRY
                }
            }
        )

    /** Сервер закрыл сессию (результат, таймаут): запись прекращается, буфер ещё досылается. */
    private suspend fun applyServerStatus(session: RunnerTrackSession, ack: LiveTrackAck) {
        if (ack.status == session.status) return
        local.updateStatus(session.sessionId, ack.status, ack.closeReason)
        if (session.status == LiveTrackStatus.ACTIVE && ack.status != LiveTrackStatus.ACTIVE && !session.stopRequested) {
            reportStopped(session, ack.closeReason.toStopReason())
        }
    }

    private suspend fun handleFailure(session: RunnerTrackSession, error: Throwable, dropBatchSeq: Int?): UploadStep {
        if (error !is LiveTrackRejectedException) {
            recorderState.onUploadFailed()
            return UploadStep.RETRY
        }
        return when (error.httpCode) {
            // Сервер не знает сессию или она чужая — писать и досылать бессмысленно.
            403, 404 -> {
                local.dropPending(session.sessionId)
                if (session.status == LiveTrackStatus.ACTIVE && !session.stopRequested) {
                    reportStopped(session, AnalyticsEvent.LiveTrackStopReason.SESSION_LOST)
                }
                local.updateStatus(session.sessionId, LiveTrackStatus.STOPPED, LiveTrackCloseReason.SESSION_LOST)
                local.markStopDelivered(session.sessionId)
                UploadStep.DONE
            }
            // Прочие 4xx (битый батч): выбрасываем его, иначе он будет отклоняться бесконечно.
            else -> {
                if (dropBatchSeq != null) local.ackBatch(session.sessionId, dropBatchSeq) else local.markStopDelivered(session.sessionId)
                UploadStep.SENT
            }
        }
    }

    private fun reportStopped(session: RunnerTrackSession, reason: AnalyticsEvent.LiveTrackStopReason) {
        val durationMin = TimeUnit.MILLISECONDS.toMinutes(clock() - session.startedAt).toInt().coerceAtLeast(0)
        analytics.trackEvent(AnalyticsEvent.LiveTrackStopped(session.competitionId, reason, durationMin))
    }
}

private fun LiveTrackCloseReason?.toStopReason(): AnalyticsEvent.LiveTrackStopReason = when (this) {
    LiveTrackCloseReason.MANUAL -> AnalyticsEvent.LiveTrackStopReason.MANUAL
    LiveTrackCloseReason.RESULT_SAVED -> AnalyticsEvent.LiveTrackStopReason.RESULT_SAVED
    LiveTrackCloseReason.CONTROL_TIME -> AnalyticsEvent.LiveTrackStopReason.CONTROL_TIME
    LiveTrackCloseReason.INACTIVITY -> AnalyticsEvent.LiveTrackStopReason.INACTIVITY
    LiveTrackCloseReason.SESSION_LOST, null -> AnalyticsEvent.LiveTrackStopReason.SESSION_LOST
}
