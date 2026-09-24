package com.competra.domain.repository.livetrack

import com.competra.domain.models.livetrack.LiveTrackCloseReason
import com.competra.domain.models.livetrack.LiveTrackStatus
import com.competra.domain.models.livetrack.RunnerTrackBatch
import com.competra.domain.models.livetrack.RunnerTrackPoint
import com.competra.domain.models.livetrack.RunnerTrackSession
import kotlinx.coroutines.flow.Flow

/**
 * Локальный буфер онлайн-трекинга бегуна: сессия и неотправленные точки переживают потерю сети,
 * убийство процесса и перезапуск телефона.
 */
interface LiveTrackLocalRepository {

    /** Последняя сессия соревнования (активная или завершённая) или `null`. */
    fun observeSession(competitionId: String): Flow<RunnerTrackSession?>

    /** Сессия, которой ещё есть что делать: пишет GPS или досылает буфер/остановку. */
    suspend fun findUnfinishedSession(): RunnerTrackSession?

    /** Сессия по id. */
    suspend fun getSession(sessionId: String): RunnerTrackSession?

    /**
     * Сохраняет сессию после старта/возобновления на сервере и согласует нумерацию батчей:
     * батчи с номером не больше [serverLastBatchSeq] сервер уже принял — они считаются
     * подтверждёнными, нумерация продолжается после большего из номеров.
     */
    suspend fun upsertStartedSession(session: RunnerTrackSession, serverLastBatchSeq: Int)

    /** Записывает точку в буфер сессии. */
    suspend fun addPoint(sessionId: String, point: RunnerTrackPoint)

    /**
     * Батч к отправке: уже назначенный, но не подтверждённый (повтор с тем же номером), либо новый
     * из неотправленных точек (до [maxPoints]). Без точек возвращает пустой батч только при
     * [allowEmpty] (пульс ради статуса), иначе `null`.
     */
    suspend fun nextBatch(sessionId: String, maxPoints: Int, allowEmpty: Boolean): RunnerTrackBatch?

    /** Сервер подтвердил батч: точки удаляются, номер фиксируется. */
    suspend fun ackBatch(sessionId: String, batchSeq: Int)

    /** Число неотправленных точек сессии. */
    fun observePendingCount(sessionId: String): Flow<Int>

    /** Есть ли у сессии неотправленные точки или назначенный батч. */
    suspend fun hasPending(sessionId: String): Boolean

    /** Обновляет статус сессии по ответу сервера. */
    suspend fun updateStatus(sessionId: String, status: LiveTrackStatus, closeReason: LiveTrackCloseReason?)

    /** Бегун нажал «Стоп». */
    suspend fun markStopRequested(sessionId: String)

    /** Сервер подтвердил остановку или сам закрыл сессию. */
    suspend fun markStopDelivered(sessionId: String)

    /** Удаляет неотправленные точки (сессия потеряна на сервере). */
    suspend fun dropPending(sessionId: String)

    /** Давал ли пользователь согласие на публикацию трека. */
    suspend fun isConsentGiven(): Boolean

    /** Запоминает согласие на публикацию трека. */
    suspend fun setConsentGiven()
}
