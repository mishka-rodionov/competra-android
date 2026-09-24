package com.competra.app.presentation.main

import android.app.PendingIntent
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.activity.ComponentActivity
import androidx.lifecycle.viewModelScope
import com.competra.data.navigation.BaseNavigation
import com.competra.data.navigation.EventsNavigation
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.PendingPushNavigationRepository
import com.competra.data.navigation.TabRoutes
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.onboarding.OnboardingSource
import com.competra.domain.models.orienteering.ResultConflictEvent
import com.competra.domain.models.push.PushKind
import com.competra.domain.repository.LoadingRepository
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.OnboardingRequestRepository
import com.competra.domain.repository.ResultConflictRepository
import com.competra.nfchelper.SportiduinoHelper
import com.competra.app.service.CompetitionScanEventRepository
import com.competra.app.service.CompetitionStartAlertRepository
import com.competra.app.service.NfcScanEvent
import com.competra.app.service.ParticipantStartAlert
import com.competra.ui.BaseAction
import com.competra.ui.BaseState
import com.competra.ui.CompetitionServiceCommand
import com.competra.ui.CompetitionServiceController
import com.competra.ui.WorkoutTrackingCommand
import com.competra.ui.WorkoutTrackingController
import com.competra.core.tracking.CompetitionTrackingCommand
import com.competra.core.tracking.CompetitionTrackingController
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Главная вьюмодель приложения.
 * Управляет общими процессами: навигацией, NFC и состоянием авторизации.
 *
 * @property navigation Интерфейс управления навигацией.
 * @property sportiduinoHelper Помощник для работы с NFC оборудованием.
 * @property serviceController Контроллер управления foreground-сервисом соревнования.
 * @property scanEventRepository Репозиторий событий NFC-сканирования.
 */
class MainViewModel(
    private val navigation: Navigation,
    private val sportiduinoHelper: SportiduinoHelper,
    private val serviceController: CompetitionServiceController,
    private val workoutTrackingController: WorkoutTrackingController,
    private val competitionTrackingController: CompetitionTrackingController,
    private val scanEventRepository: CompetitionScanEventRepository,
    private val startAlertRepository: CompetitionStartAlertRepository,
    private val resultConflictRepository: ResultConflictRepository,
    private val networkErrorRepository: NetworkErrorRepository,
    private val loadingRepository: LoadingRepository,
    private val onboardingRequestRepository: OnboardingRequestRepository,
    private val pendingPushNavigationRepository: PendingPushNavigationRepository
) : BaseViewModel<BaseState>(object : BaseState {}) {

    /**
     * Поток событий состояния загрузки для отображения глобального лоадера.
     */
    val loadingEvent: StateFlow<Boolean> = loadingRepository.loadingEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Поток базовых эффектов навигации (например, BackRoute).
     */
    val baseNavigationEffect: SharedFlow<BaseNavigation> = navigation.baseNavigationEffect

    /**
     * Поток команд переключения таба нижней навигации.
     */
    val switchTabEffect: SharedFlow<String> = navigation.switchTabEffect

    /**
     * Команды запуска/остановки foreground-сервиса, читаются в MainActivity.
     */
    val serviceCommands: SharedFlow<CompetitionServiceCommand> = serviceController.commands

    /**
     * Команды старт/пауза/резюм/стоп сервиса live-трекинга тренировки, читаются в MainActivity.
     */
    val workoutTrackingCommands: SharedFlow<WorkoutTrackingCommand> = workoutTrackingController.commands

    /**
     * Команды запуска/остановки сервиса онлайн-трекинга бегуна на соревновании, читаются в MainActivity.
     */
    val competitionTrackingCommands: SharedFlow<CompetitionTrackingCommand> = competitionTrackingController.commands

    private val _currentScanEvent = MutableStateFlow<NfcScanEvent?>(null)

    /**
     * Текущее событие NFC-сканирования для отображения баннера. Обнуляется через 4 секунды.
     */
    val currentScanEvent: StateFlow<NfcScanEvent?> = _currentScanEvent.asStateFlow()

    private val _conflictEvent = MutableStateFlow<ResultConflictEvent?>(null)

    /**
     * Текущее событие конфликта результата для отображения bottom sheet диалога.
     */
    val conflictEvent: StateFlow<ResultConflictEvent?> = _conflictEvent.asStateFlow()

    private val _networkErrorEvent = MutableStateFlow<NetworkErrorEvent?>(null)

    /**
     * Текущее событие сетевой ошибки для отображения bottom sheet диалога.
     */
    val networkErrorEvent: StateFlow<NetworkErrorEvent?> = _networkErrorEvent.asStateFlow()

    private val _startAlertEvent = MutableStateFlow<ParticipantStartAlert?>(null)

    /**
     * Текущее предстартовое оповещение для отображения UI-баннера.
     * Обнуляется через 3 секунды после старта участника.
     */
    val startAlertEvent: StateFlow<ParticipantStartAlert?> = _startAlertEvent.asStateFlow()

    private var onboardingRequestCounter = 0L

    private val _onboardingRequest = MutableStateFlow<OnboardingOverlayRequest?>(null)

    /**
     * Текущий запрос на ручной повторный показ онбординга (из настроек профиля).
     * Каждый запрос получает уникальный [OnboardingOverlayRequest.requestId], чтобы
     * оверлей всегда создавался с чистым состоянием, а не переиспользовал прошлый показ.
     */
    val onboardingRequest: StateFlow<OnboardingOverlayRequest?> = _onboardingRequest.asStateFlow()

    init {
        viewModelScope.launch {
            scanEventRepository.events.collect { event ->
                _currentScanEvent.value = event
                delay(4000)
                _currentScanEvent.compareAndSet(event, null)
            }
        }
        viewModelScope.launch {
            startAlertRepository.events.collectLatest { event ->
                _startAlertEvent.value = event
                if (event is ParticipantStartAlert.Started) {
                    delay(3000)
                    _startAlertEvent.compareAndSet(event, null)
                }
            }
        }
        viewModelScope.launch {
            resultConflictRepository.events.collect { event ->
                _conflictEvent.value = event
            }
        }
        viewModelScope.launch {
            networkErrorRepository.events.collect { event ->
                _networkErrorEvent.value = event
            }
        }
        viewModelScope.launch {
            onboardingRequestRepository.events.collect { source ->
                _onboardingRequest.value = OnboardingOverlayRequest(source, onboardingRequestCounter++)
            }
        }
    }

    /** Закрывает оверлей повторного показа онбординга. */
    fun dismissOnboarding() {
        _onboardingRequest.value = null
    }

    /** Закрывает диалог сетевой ошибки. */
    fun dismissNetworkError() {
        _networkErrorEvent.value = null
    }

    /** Применяет конфликтующий результат: вызывает onApply и закрывает диалог. */
    fun applyConflict() {
        val event = _conflictEvent.value ?: return
        viewModelScope.launch {
            event.onApply()
            _conflictEvent.value = null
        }
    }

    /** Отменяет конфликт без сохранения данных. */
    fun cancelConflict() {
        _conflictEvent.value = null
    }

    override fun onAction(action: BaseAction) {
        // Базовая обработка действий
    }

    /**
     * Подписка на специфичные для модуля эффекты навигации.
     */
    suspend fun collectNavigationEffect(navigationHandler: (BaseNavigation) -> Unit, destination: BaseNavigation) {
//        viewModelScope.launch(Dispatchers.Main) {
        navigation.collectNavigationEffect(navigationHandler, destination)
//        }
    }

    fun setNfcAdapter(nfcAdapter: NfcAdapter) = sportiduinoHelper.setNfcAdapter(nfcAdapter)

    fun enableReaderMode(activity: ComponentActivity, handleTag: (Tag) -> Unit) {
        sportiduinoHelper.enableReaderMode(activity, handleTag)
    }

    fun disableReaderMode(activity: ComponentActivity) = sportiduinoHelper.disableReaderMode(activity)

    fun onNewTagDetected(tag: Tag) {
        viewModelScope.launch {
            sportiduinoHelper.onNewTagDetected(tag)
        }
    }

    /**
     * Обрабатывает тап по push-уведомлению (extras из [MainActivity.onCreate]/[MainActivity.onNewIntent]).
     * Кладёт целевой роут в [PendingPushNavigationRepository] и переключает таб на Events — сам
     * переход выполняет NavHost этого таба, как только становится активным (см. MainScreen).
     */
    fun onPushNavigation(competitionId: String?, kind: String?) {
        if (competitionId.isNullOrBlank()) return
        val route = when (kind) {
            PushKind.COMPETITION_START_REMINDER, PushKind.DAY_BEFORE_REMINDER ->
                EventsNavigation.EventDetailsRoute(eventId = competitionId)
            PushKind.RESULTS_PUBLISHED ->
                EventsNavigation.EventResultsRoute(eventId = competitionId)
            else -> return
        }
        pendingPushNavigationRepository.set(route)
        viewModelScope.launch {
            navigation.switchTab(TabRoutes.EVENTS)
        }
    }
}

/**
 * Запрос на показ оверлея онбординга посреди сессии, с уникальным [requestId] —
 * чтобы каждый повторный показ создавал свежий OnboardingViewModel, а не переиспользовал
 * состояние (текущий слайд, флаг завершения) с прошлого раза.
 */
data class OnboardingOverlayRequest(
    val source: OnboardingSource,
    val requestId: Long
)
