package com.competra.eventdetails.presentation.live_track_runner

import androidx.lifecycle.viewModelScope
import com.competra.core.tracking.CompetitionTrackingController
import com.competra.core.tracking.LiveTrackEngine
import com.competra.core.tracking.LiveTrackRecorderSnapshot
import com.competra.core.tracking.LiveTrackRecorderState
import com.competra.domain.models.livetrack.RunnerTrackSession
import com.competra.domain.repository.livetrack.LiveTrackLocalRepository
import com.competra.ui.BaseAction
import com.competra.ui.BaseState
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Период обновления «N с назад» и длительности записи на экране. */
private const val CLOCK_TICK_MS = 1_000L

/**
 * Состояние экрана онлайн-трека бегуна.
 *
 * @property session Последняя локальная сессия соревнования или `null`.
 * @property pendingPoints Точек в очереди на отправку.
 * @property recorder Живое состояние сервиса записи (GPS, последняя отправка).
 * @property now Текущее время для «N с назад» (тикает раз в секунду).
 * @property isStopConfirmVisible Видимость подтверждения остановки.
 */
data class LiveTrackRunnerState(
    val session: RunnerTrackSession? = null,
    val pendingPoints: Int = 0,
    val recorder: LiveTrackRecorderSnapshot = LiveTrackRecorderSnapshot(),
    val now: Long = System.currentTimeMillis(),
    val isStopConfirmVisible: Boolean = false
) : BaseState {

    /** Сессия должна писать, но сервис её не пишет (систему убила/перезагрузка) — нужно продолжить. */
    val isInterrupted: Boolean
        get() = session?.isRecording == true && recorder.recordingSessionId != session.sessionId
}

/** Действия экрана онлайн-трека. */
sealed interface LiveTrackRunnerAction : BaseAction {
    /** Нажата «Остановить трек» — показать подтверждение. */
    data object StopClick : LiveTrackRunnerAction

    /** Остановка подтверждена. */
    data object ConfirmStop : LiveTrackRunnerAction

    /** Подтверждение закрыто без остановки. */
    data object DismissStop : LiveTrackRunnerAction

    /** Продолжить прерванную запись. */
    data object ResumeRecording : LiveTrackRunnerAction
}

/**
 * ViewModel экрана онлайн-трека бегуна: показывает состояние записи и передачи из Room и
 * [LiveTrackRecorderState], останавливает и возобновляет запись. Карты и позиции здесь нет.
 */
class LiveTrackRunnerViewModel(
    private val local: LiveTrackLocalRepository,
    private val recorderState: LiveTrackRecorderState,
    private val engine: LiveTrackEngine,
    private val trackingController: CompetitionTrackingController
) : BaseViewModel<LiveTrackRunnerState>(LiveTrackRunnerState()) {

    private var observeJob: Job? = null

    /** Начинает следить за сессией соревнования [eventId]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun initialize(eventId: String) {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            val sessions = local.observeSession(eventId)
            val pending = sessions.map { it?.sessionId }.flatMapLatest { id ->
                if (id == null) flowOf(0) else local.observePendingCount(id)
            }
            combine(sessions, pending, recorderState.snapshot) { session, pendingCount, recorder ->
                Triple(session, pendingCount, recorder)
            }.collect { (session, pendingCount, recorder) ->
                updateState { copy(session = session, pendingPoints = pendingCount, recorder = recorder) }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                updateState { copy(now = System.currentTimeMillis()) }
                delay(CLOCK_TICK_MS)
            }
        }
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            is LiveTrackRunnerAction.StopClick -> updateState { copy(isStopConfirmVisible = true) }
            is LiveTrackRunnerAction.DismissStop -> updateState { copy(isStopConfirmVisible = false) }
            is LiveTrackRunnerAction.ConfirmStop -> stop()
            is LiveTrackRunnerAction.ResumeRecording -> resume()
        }
    }

    private fun stop() {
        updateState { copy(isStopConfirmVisible = false) }
        val sessionId = stateValue.session?.sessionId ?: return
        viewModelScope.launch {
            engine.requestStop(sessionId)
            // Сервис прекратит GPS и дошлёт буфер; если он не запущен — запустится ради досылки.
            trackingController.stopRecording(sessionId)
        }
    }

    private fun resume() {
        val sessionId = stateValue.session?.sessionId ?: return
        viewModelScope.launch { trackingController.startRecording(sessionId) }
    }
}
