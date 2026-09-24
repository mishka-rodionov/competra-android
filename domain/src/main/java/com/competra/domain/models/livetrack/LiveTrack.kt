package com.competra.domain.models.livetrack

/** Статус сессии онлайн-трекинга на сервере. */
enum class LiveTrackStatus {
    /** Бегун на дистанции, точки принимаются. */
    ACTIVE,

    /** Закрыта: у участника сохранён финальный результат. */
    FINISHED,

    /** Остановлена бегуном. */
    STOPPED,

    /** Закрыта сервером по сроку или отсутствию точек. */
    TIMED_OUT
}

/** Причина закрытия сессии. [SESSION_LOST] — только на клиенте: сервер сессию не знает (404/403). */
enum class LiveTrackCloseReason { MANUAL, RESULT_SAVED, CONTROL_TIME, INACTIVITY, SESSION_LOST }

/**
 * Локальная сессия онлайн-трекинга бегуна.
 *
 * @property sessionId Id сессии на сервере.
 * @property competitionId Соревнование.
 * @property status Последний известный статус на сервере.
 * @property closeReason Причина закрытия, если сессия закрыта.
 * @property startedAt Когда бегун включил трекинг на этом устройстве (Unix ms).
 * @property deadlineAt Крайний срок сессии по данным сервера (Unix ms).
 * @property uploadIntervalSec Период отправки батчей, заданный сервером.
 * @property lastAckedBatchSeq Номер последнего подтверждённого сервером батча.
 * @property stopRequested Бегун нажал «Стоп»: запись GPS прекращена, осталось дослать буфер и
 *   сообщить серверу об остановке.
 * @property stopDelivered Сервер подтвердил остановку (или сам закрыл сессию).
 */
data class RunnerTrackSession(
    val sessionId: String,
    val competitionId: String,
    val status: LiveTrackStatus,
    val closeReason: LiveTrackCloseReason?,
    val startedAt: Long,
    val deadlineAt: Long,
    val uploadIntervalSec: Int,
    val lastAckedBatchSeq: Int,
    val stopRequested: Boolean,
    val stopDelivered: Boolean
) {
    /** Идёт ли запись GPS: сессия активна на сервере, бегун её не останавливал. */
    val isRecording: Boolean get() = status == LiveTrackStatus.ACTIVE && !stopRequested
}

/**
 * GPS-точка трека.
 *
 * @property t Время фикса (Unix ms).
 * @property lat Широта.
 * @property lon Долгота.
 * @property accuracy Точность в метрах.
 */
data class RunnerTrackPoint(
    val t: Long,
    val lat: Double,
    val lon: Double,
    val accuracy: Float?
)

/**
 * Батч точек к отправке.
 *
 * @property batchSeq Номер батча; отправляется повторно с тем же номером, пока сервер не подтвердит.
 * @property points Точки (может быть пустым — «пульс» ради статуса сессии).
 */
data class RunnerTrackBatch(
    val batchSeq: Int,
    val points: List<RunnerTrackPoint>
)

/** Ответ сервера на старт/возобновление сессии. */
data class LiveTrackStartResult(
    val sessionId: String,
    val status: LiveTrackStatus,
    val closeReason: LiveTrackCloseReason?,
    val lastBatchSeq: Int,
    val uploadIntervalSec: Int,
    val deadlineAt: Long
)

/** Ответ сервера на батч или остановку. */
data class LiveTrackAck(
    val status: LiveTrackStatus,
    val closeReason: LiveTrackCloseReason?,
    val ackedBatchSeq: Int
)

/**
 * Сервер отклонил запрос (HTTP 4xx) — повторять бессмысленно.
 *
 * @property httpCode HTTP-код ответа.
 */
class LiveTrackRejectedException(val httpCode: Int, message: String?) : Exception(message)
