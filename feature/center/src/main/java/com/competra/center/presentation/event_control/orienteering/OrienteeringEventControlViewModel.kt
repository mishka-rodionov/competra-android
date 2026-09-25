package com.competra.center.presentation.event_control.orienteering

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.center.data.event_control.OrientEventControlAction
import com.competra.center.data.event_control.OrienteeringEventControlState
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.data.navigation.CenterNavigation
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.getArguments
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.domain.repository.LoadingRepository
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.ui.BaseAction
import com.competra.ui.CompetitionServiceController
import com.competra.ui.CompetitionStartTimeRepository
import com.competra.ui.viewmodel.BaseViewModel
import com.competra.utils.constants.EventsConstants
import com.competra.utils.isValidStartTimestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel для экрана управления соревнованием по ориентированию.
 *
 * @property navigation Навигация приложения.
 * @property orienteeringCompetitionInteractor Интерактор для работы с данными соревнований.
 * @property analytics Трекер аналитических событий.
 */
class OrienteeringEventControlViewModel(
    private val navigation: Navigation,
    private val orienteeringCompetitionInteractor: OrienteeringCompetitionInteractor,
    private val serviceController: CompetitionServiceController,
    private val startTimeRepository: CompetitionStartTimeRepository,
    private val networkErrorRepository: NetworkErrorRepository,
    private val loadingRepository: LoadingRepository,
    private val analytics: AnalyticsTracker
) : BaseViewModel<OrienteeringEventControlState>(OrienteeringEventControlState()) {

    val competitionId: String? = navigation.getArguments<String>(EventsConstants.EVENT_ID.name)
    private var timerJob: Job? = null
    private var stopwatchJob: Job? = null

    private fun loadCompetitionData() {
        val id = competitionId ?: return
        viewModelScope.launch {
            loadingRepository.emit(true)
            val serverConfirmed = orienteeringCompetitionInteractor.getCompetition(id)
                ?.serverUpdatedAt != null
            if (serverConfirmed) {
                orienteeringCompetitionInteractor.fetchAndSyncFromServer(id)
                orienteeringCompetitionInteractor.fetchAndSyncParticipantsFromServer(id)
            }

            orienteeringCompetitionInteractor.tryAutoStartFromRegistration(id)

            orienteeringCompetitionInteractor.getCompetitionWithDetails(id).onSuccess { details ->
                val allChipsDistributed = details.groupsWithParticipants.all { group ->
                    group.participants.all { it.isChipGiven }
                }
                val competition = details.competition
                val isRunning = competition.competition.status == CompetitionStatus.IN_PROGRESS
                val allParticipantsFinished = isRunning &&
                        orienteeringCompetitionInteractor.areAllParticipantsFinished(id)
                // Считаем жеребьёвку проведённой если competition.isDrawConducted == true
                // ИЛИ хотя бы один участник уже имеет стартовый номер (fallback при сбросе флага сервером).
                val isDrawConducted = competition.isDrawConducted ||
                    details.groupsWithParticipants.any { group ->
                        group.participants.any { it.startNumber.isNotEmpty() }
                    }
                applyCompetitionState(
                    title = competition.competition.title,
                    competition = competition,
                    groups = details.groupsWithParticipants.map { it.group },
                    allChipsDistributed = allChipsDistributed,
                    allParticipantsFinished = allParticipantsFinished,
                    isDrawConducted = isDrawConducted
                )
            }.onFailure {
                orienteeringCompetitionInteractor.getCompetition(id)?.let { competition ->
                    applyCompetitionState(competition.competition.title, competition)
                }
                handleFailure(it)
            }
            loadingRepository.emit(false)
        }
    }

    /**
     * Применяет загруженное соревнование к стейту.
     * Использует status == IN_PROGRESS как источник истины для isCompetitionRunning,
     * поскольку startTime может быть сброшен при синхронизации с сервером.
     * Таймер обратного отсчёта возобновляется, если startTime ещё в будущем.
     */
    private fun applyCompetitionState(
        title: String,
        competition: com.competra.domain.models.orienteering.OrienteeringCompetition,
        groups: List<com.competra.domain.models.ParticipantGroup> = emptyList(),
        allChipsDistributed: Boolean = true,
        allParticipantsFinished: Boolean = false,
        isDrawConducted: Boolean = false
    ) {
        val startTime = competition.startTime
        val now = System.currentTimeMillis()
        val isFinished = competition.competition.status == CompetitionStatus.FINISHED
        val isRunning = competition.competition.status == CompetitionStatus.IN_PROGRESS

        // Репозиторий — надёжный источник startTime, не зависит от серверной синхронизации.
        // Если репозиторий заполнен — используем его (он установлен при старте через handleStartCompetition
        // или onCountdownFinished), иначе fallback на competition.startTime из БД.
        val repoStartTime = startTimeRepository.startTimeMs.value
        val effectiveStartTime = repoStartTime.takeIf { it > 0L } ?: startTime

        val isCountingDown = isRunning && effectiveStartTime != null && effectiveStartTime > now
        val remainingMillis = if (isCountingDown) effectiveStartTime!! - now else 0L
        val wasServiceRunning = stateValue.isCompetitionRunning

        updateState {
            copy(
                competitionTitle = title,
                participantGroups = groups,
                competition = competition,
                isCompetitionRunning = isRunning,
                isTimerRunning = isCountingDown,
                countdownMillis = remainingMillis,
                countdownTimerInput = competition.countdownTimer?.toString() ?: "",
                allChipsDistributed = allChipsDistributed,
                allParticipantsFinished = allParticipantsFinished,
                isDrawConducted = isDrawConducted,
                isFinished = isFinished
            )
        }

        if (isCountingDown) startTimer()

        if (isRunning) {
            if (!wasServiceRunning && repoStartTime == 0L) {
                val st = startTime ?: System.currentTimeMillis()
                startTimeRepository.set(st)
                competitionId?.let { id ->
                    viewModelScope.launch { serviceController.start(id, st) }
                }
            }
            if (!isCountingDown) startStopwatch()
        }
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            OrientEventControlAction.OpenOrientReadCard -> viewModelScope.launch {
                navigation.navigate(
                    destination = CenterNavigation.OrientReadCardRoute,
                    argument = navigation.createArguments(EventsConstants.EVENT_ID.name to competitionId)
                )
            }

            OrientEventControlAction.OpenParticipantLists -> {
                viewModelScope.launch {
                    navigation.navigate(
                        destination = CenterNavigation.ParticipantList,
                        argument = navigation.createArguments(EventsConstants.EVENT_ID.name to competitionId)
                    )
                }
            }

            OrientEventControlAction.OpenDrawParticipants -> {
                viewModelScope.launch {
                    navigation.navigate(
                        destination = CenterNavigation.DrawParticipants,
                        argument = navigation.createArguments(EventsConstants.EVENT_ID.name to competitionId)
                    )
                }
            }

            OrientEventControlAction.OpenStartGrid -> {
                viewModelScope.launch {
                    navigation.navigate(
                        destination = CenterNavigation.StartGridRoute,
                        argument = navigation.createArguments(EventsConstants.EVENT_ID.name to competitionId)
                    )
                }
            }

            OrientEventControlAction.OpenResults -> {
                viewModelScope.launch {
                    navigation.navigate(
                        destination = CenterNavigation.ParticipantResults,
                        argument = navigation.createArguments(EventsConstants.EVENT_ID.name to competitionId)
                    )
                }
            }

            OrientEventControlAction.OpenGetOrienteeringChip -> {
                viewModelScope.launch {
                    competitionId?.let {
                        navigation.navigate(CenterNavigation.GetOrienteeringChipRoute(it))
                    }
                }
            }

            OrientEventControlAction.OpenWriteChip -> viewModelScope.launch {
                navigation.navigate(destination = CenterNavigation.WriteChipRoute)
            }

            OrientEventControlAction.ShowStartConfirmDialog ->
                updateState { copy(isShowStartConfirmDialog = true) }

            OrientEventControlAction.HideStartConfirmDialog ->
                updateState { copy(isShowStartConfirmDialog = false) }

            OrientEventControlAction.StartCompetition -> {
                updateState { copy(isShowStartConfirmDialog = false) }
                handleStartCompetition()
            }

            OrientEventControlAction.ShowStopConfirmDialog ->
                updateState { copy(isShowStopConfirmDialog = true) }

            OrientEventControlAction.HideStopConfirmDialog ->
                updateState { copy(isShowStopConfirmDialog = false) }

            OrientEventControlAction.StopCompetition -> {
                updateState { copy(isShowStopConfirmDialog = false) }
                handleStopCompetition()
            }

            OrientEventControlAction.ShowCloseRegistrationDialog ->
                updateState { copy(isShowCloseRegistrationDialog = true) }

            OrientEventControlAction.HideCloseRegistrationDialog ->
                updateState { copy(isShowCloseRegistrationDialog = false) }

            OrientEventControlAction.CloseRegistration -> {
                updateState { copy(isShowCloseRegistrationDialog = false) }
                handleCloseRegistration()
            }

            OrientEventControlAction.CancelCountdown -> handleCancelCountdown()

            is OrientEventControlAction.UpdateCountdownTimerInput -> updateState {
                copy(countdownTimerInput = action.value)
            }

            OrientEventControlAction.Reload -> loadCompetitionData()

            OrientEventControlAction.StopService -> viewModelScope.launch {
                serviceController.stop()
            }
        }
    }

    /**
     * Обрабатывает нажатие на кнопку "Старт".
     * Устанавливает время старта, меняет статус на IN_PROGRESS и синхронизирует с сервером.
     */
    private fun handleStartCompetition() {
        val competition = stateValue.competition ?: return
        val countdownMinutes = stateValue.countdownTimerInput.toIntOrNull()
            ?: competition.countdownTimer
            ?: 0
        val startTime = System.currentTimeMillis() + (countdownMinutes * 60 * 1000L)

        viewModelScope.launch {
            loadingRepository.emit(true)
            val updatedCompetition = competition.copy(
                startTime = startTime,
                countdownTimer = countdownMinutes,
                competition = competition.competition.copy(
                    status = CompetitionStatus.IN_PROGRESS
                )
            )
            orienteeringCompetitionInteractor.updateCompetition(
                orienteeringCompetition = updatedCompetition,
                participantGroups = null
            )
            orienteeringCompetitionInteractor.publishCompetitionToServer(updatedCompetition)
                .onFailure { handleFailure(it) }

            // При старте по стартовой станции нет общего времени старта — участники не привязаны
            // к нему интервалом, каждый получает своё время позже, из отметки на чипе
            // (см. OrientReadCardViewModel). Пересчитывать startTime участников здесь нельзя.
            if (competition.startTimeMode != StartTimeMode.BY_START_STATION) {
                competitionId?.let { id ->
                    val intervalMs = (competition.startIntervalSeconds ?: 60) * 1000L
                    val participants = orienteeringCompetitionInteractor.getParticipants(id).getOrNull()
                    if (!participants.isNullOrEmpty()) {
                        val updatedParticipants = rebaseStartTimes(participants, startTime, intervalMs)
                        orienteeringCompetitionInteractor.updateParticipants(updatedParticipants)
                    }
                }
            }

            updateState {
                copy(
                    competition = updatedCompetition,
                    countdownMillis = countdownMinutes * 60 * 1000L,
                    isTimerRunning = true,
                    isCompetitionRunning = true
                )
            }
            startTimeRepository.set(startTime)
            startTimer()
            startStopwatch()
            loadingRepository.emit(false)
            competitionId?.let { id -> serviceController.start(id, startTime) }
        }
    }

    /**
     * Обрабатывает завершение соревнования.
     * Меняет статус на FINISHED, останавливает таймер, выставляет DNF незафинишировавшим
     * и синхронизирует с сервером.
     */
    private fun handleStopCompetition() {
        timerJob?.cancel()
        stopwatchJob?.cancel()
        startTimeRepository.clear()
        val competition = stateValue.competition
        viewModelScope.launch {
            loadingRepository.emit(true)
            if (competition != null) {
                val finished = competition.copy(
                    competition = competition.competition.copy(status = CompetitionStatus.FINISHED)
                )
                orienteeringCompetitionInteractor.updateCompetition(finished, null)
                orienteeringCompetitionInteractor.publishCompetitionToServer(finished)
                    .onFailure { handleFailure(it) }
                competitionId?.let { id ->
                    orienteeringCompetitionInteractor.markNonFinishedAsDNF(id)
                }
            }
            serviceController.stop()
            loadingRepository.emit(false)
        }
        updateState { copy(isCompetitionRunning = false, isTimerRunning = false, isFinished = true, stopwatchMillis = 0L) }
    }

    /**
     * Досрочно завершает регистрацию: фиксирует `registrationEnd` на текущий момент и ставит
     * статус [CompetitionStatus.REGISTRATION_CLOSED]. Бэкенд вычисляет статус по `registrationEnd`,
     * поэтому после синхронизации участники больше не могут зарегистрироваться или отменить
     * регистрацию, а стартовый протокол безопасно правит только организатор.
     */
    private fun handleCloseRegistration() {
        val competition = stateValue.competition ?: return
        val closed = competition.copy(
            competition = competition.competition.copy(
                registrationEnd = System.currentTimeMillis(),
                status = CompetitionStatus.REGISTRATION_CLOSED
            )
        )
        analytics.trackEvent(AnalyticsEvent.RegistrationCloseClicked(competition.competitionId))
        viewModelScope.launch {
            loadingRepository.emit(true)
            orienteeringCompetitionInteractor.updateCompetition(closed, null)
            orienteeringCompetitionInteractor.publishCompetitionToServer(closed)
                .onFailure { handleFailure(it) }
            updateState { copy(competition = closed) }
            loadingRepository.emit(false)
        }
    }

    /**
     * Отменяет предстартовый таймер и возвращает соревнование в статус DRAFT.
     */
    private fun handleCancelCountdown() {
        timerJob?.cancel()
        stopwatchJob?.cancel()
        startTimeRepository.clear()
        val competition = stateValue.competition ?: return
        val reverted = competition.copy(
            startTime = null,
            competition = competition.competition.copy(status = CompetitionStatus.DRAFT)
        )
        viewModelScope.launch {
            loadingRepository.emit(true)
            orienteeringCompetitionInteractor.updateCompetition(reverted, null)
            orienteeringCompetitionInteractor.publishCompetitionToServer(reverted)
                .onFailure { handleFailure(it) }
            serviceController.stop()
            loadingRepository.emit(false)
        }
        updateState {
            copy(
                competition = reverted,
                countdownMillis = 0L,
                stopwatchMillis = 0L,
                isTimerRunning = false,
                isCompetitionRunning = false
            )
        }
    }

    /**
     * Запускает таймер обратного отсчета.
     * Для режима USER_SET по истечении таймера пересчитывает фактическое время старта
     * и стартовые времена всех участников.
     */
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (stateValue.countdownMillis > 0) {
                delay(1000)
                updateState { copy(countdownMillis = countdownMillis - 1000) }
            }
            updateState { copy(isTimerRunning = false) }
            onCountdownFinished()
        }
    }

    /**
     * Запускает секундомер соревнования — считает прошедшее время с момента старта.
     * Обновляет [OrienteeringEventControlState.stopwatchMillis] каждые 16 мс.
     */
    private fun startStopwatch() {
        stopwatchJob?.cancel()
        stopwatchJob = viewModelScope.launch {
            while (true) {
                val startTimeMs = startTimeRepository.startTimeMs.value
                    .takeIf { it > 0L }
                    ?: stateValue.competition?.startTime
                    ?: break
                val elapsed = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(0L)
                updateState { copy(stopwatchMillis = elapsed) }
                delay(16)
            }
        }
    }

    /**
     * Вызывается после обнуления таймера.
     * Для режима USER_SET фиксирует фактическое время старта и пересчитывает
     * стартовые времена участников с учётом стартового интервала.
     */
    private fun onCountdownFinished() {
        val competition = stateValue.competition ?: return
        if (competition.startTimeMode != StartTimeMode.USER_SET) return
        val compId = competitionId ?: return

        viewModelScope.launch {
            val actualStartTime = System.currentTimeMillis()
            startTimeRepository.set(actualStartTime)
            val updatedCompetition = competition.copy(startTime = actualStartTime)
            orienteeringCompetitionInteractor.updateCompetition(updatedCompetition, null)
            orienteeringCompetitionInteractor.publishCompetitionToServer(updatedCompetition)
                .onFailure { handleFailure(it) }

            val intervalMs = (competition.startIntervalSeconds ?: 60) * 1000L
            val participants = orienteeringCompetitionInteractor.getParticipants(compId).getOrNull()
            if (!participants.isNullOrEmpty()) {
                val updated = rebaseStartTimes(participants, actualStartTime, intervalMs)
                orienteeringCompetitionInteractor.updateParticipants(updated)
            }

            updateState { copy(competition = updatedCompetition) }
            startStopwatch()
        }
    }

    /**
     * Сдвигает стартовые времена участников к новому времени старта [newStartTime],
     * сохраняя относительную структуру жеребьёвки.
     *
     * Первая стартовая минута начинается через один стартовый интервал после гонга
     * (например, при старте соревнования в 12:00 первые участники стартуют в 12:01).
     * Базой считается самое раннее валидное стартовое время — каждое время сдвигается
     * на ту же величину. Это сохраняет совместные стартовые минуты (несколько участников
     * на одну минуту при жеребьёвке по дистанциям), в отличие от пересчёта по сквозному
     * стартовому номеру, который раскидывал участников по одному на минуту.
     * Участники без валидного стартового времени остаются без изменений.
     */
    private fun rebaseStartTimes(
        participants: List<OrienteeringParticipant>,
        newStartTime: Long,
        intervalMs: Long
    ): List<OrienteeringParticipant> {
        val baseStartTime = participants
            .map { it.startTime }
            .filter { it.isValidStartTimestamp() }
            .minOrNull() ?: return participants
        val firstStartTime = newStartTime + intervalMs
        return participants.map { p ->
            if (!p.startTime.isValidStartTimestamp()) p
            else p.copy(startTime = firstStartTime + (p.startTime - baseStartTime))
        }
    }

    private fun handleFailure(throwable: Throwable) {
        viewModelScope.launch {
            val code = (throwable as? NetworkException)?.code
            networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        stopwatchJob?.cancel()
    }
}
