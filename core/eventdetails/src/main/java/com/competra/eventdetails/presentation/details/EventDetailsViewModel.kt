package com.competra.eventdetails.presentation.details

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.data.navigation.ClubsNavigation
import com.competra.data.navigation.EventsNavigation
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.PendingRegistrationRepository
import com.competra.data.navigation.PendingTabNavigationRepository
import com.competra.data.navigation.TabRoutes
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.cyclic_event.EventParticipantGroup
import com.competra.domain.models.cyclic_event.COMMAND_NAME_MAX_LENGTH
import com.competra.domain.models.cyclic_event.GroupEligibility
import com.competra.domain.models.cyclic_event.TeamSuggestion
import com.competra.domain.models.cyclic_event.checkGroupEligibility
import com.competra.domain.models.cyclic_event.competitionYear
import com.competra.domain.models.cyclic_event.normalizeCommandName
import com.competra.domain.models.cyclic_event.ownOptionFor
import com.competra.domain.models.cyclic_event.teamIdFor
import com.competra.domain.models.cyclic_event.teamSourceFor
import com.competra.domain.models.user.User
import com.competra.domain.repository.LoadingRepository
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.clubs.ClubRepository
import com.competra.domain.repository.events.CyclicEventDetailsRepository
import com.competra.domain.repository.user.UserRepository
import com.competra.core.tracking.CompetitionTrackingController
import com.competra.core.tracking.LiveTrackEngine
import com.competra.domain.models.cyclic_event.CyclicEventDetails
import com.competra.domain.models.events.EventStatus
import com.competra.domain.models.events.EventType
import com.competra.domain.models.livetrack.LiveTrackRejectedException
import com.competra.domain.models.livetrack.RunnerTrackSession
import com.competra.domain.repository.livetrack.LiveTrackLocalRepository
import com.competra.domain.repository.livetrack.LiveTrackViewerRepository
import com.competra.eventdetails.data.details.EventDetailsState
import com.competra.eventdetails.data.details.LiveTrackEntry
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * ViewModel для экрана деталей события.
 * Управляет загрузкой данных события, навигацией и процессом регистрации.
 *
 * @param cyclicEventDetailsRepository Репозиторий для получения деталей события.
 * @param userRepository Репозиторий пользователя.
 * @param navigation Сервис навигации.
 * @param pendingRegistrationRepository Хранилище отложенного действия регистрации.
 * @param networkErrorRepository Репозиторий для передачи сетевых ошибок в MainActivity.
 * @param clubRepository Репозиторий клубов — используется для резолва названия клуба-организатора.
 * @param liveTrackLocalRepository Локальная сессия онлайн-трека бегуна и согласие на публикацию.
 * @param liveTrackEngine Старт сессии онлайн-трекинга на сервере.
 * @param trackingController Запуск сервиса записи трека.
 * @param liveTrackViewerRepository Есть ли у соревнования онлайн-треки (кнопка для зрителя).
 * @param pendingTabNavigationRepository Отложенный переход в карточку клуба во вкладке «Клубы».
 */
class EventDetailsViewModel(
    private val cyclicEventDetailsRepository: CyclicEventDetailsRepository,
    private val userRepository: UserRepository,
    private val navigation: Navigation,
    private val pendingRegistrationRepository: PendingRegistrationRepository,
    private val networkErrorRepository: NetworkErrorRepository,
    private val loadingRepository: LoadingRepository,
    private val analytics: AnalyticsTracker,
    private val clubRepository: ClubRepository,
    private val liveTrackLocalRepository: LiveTrackLocalRepository,
    private val liveTrackEngine: LiveTrackEngine,
    private val trackingController: CompetitionTrackingController,
    private val liveTrackViewerRepository: LiveTrackViewerRepository,
    private val pendingTabNavigationRepository: PendingTabNavigationRepository,
) : BaseViewModel<EventDetailsState>(
    EventDetailsState(eventDetails = null)
) {

    private var currentUser: User? = null
    private var liveTrackSession: RunnerTrackSession? = null
    private var liveTrackJob: Job? = null
    private var teamOptionsJob: Job? = null
    private var clubMatchJob: Job? = null

    private val _liveTrackPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Запросы экрану проверить/запросить разрешения на геолокацию перед стартом трекинга
     * (нужен Activity-контекст, поэтому это делает экран и отвечает [EventDetailsAction.LiveTrackPermissionsResult]).
     */
    val liveTrackPermissionRequests: SharedFlow<Unit> = _liveTrackPermissionRequests.asSharedFlow()

    override fun onAction(action: BaseAction) {
        when (action) {
            is EventDetailsAction.OnGroupClick -> navigateToGroup(action.group)
            is EventDetailsAction.ToResults -> navigateToResults()
            is EventDetailsAction.ToLiveResults -> navigateToLiveResults()
            is EventDetailsAction.ShowRegistrationDialog -> showRegistrationDialog()
            is EventDetailsAction.HideRegistrationDialog -> hideRegistrationDialog()
            is EventDetailsAction.SelectGroup -> selectGroup(action.group)
            is EventDetailsAction.CommandNameChanged -> changeCommandName(action.commandName)
            is EventDetailsAction.SelectTeamSuggestion -> changeCommandName(action.suggestion.label)
            is EventDetailsAction.OpenClub -> openClub(action.clubId)
            is EventDetailsAction.ConfirmRegistration -> confirmRegistration()
            is EventDetailsAction.OpenProfile -> {
                hideRegistrationDialog()
                viewModelScope.launch { navigation.switchTab(TabRoutes.PROFILE) }
            }
            is EventDetailsAction.CancelRegistration -> cancelRegistration()
            is EventDetailsAction.ToLiveTracks -> navigateToLiveTracks()
            is EventDetailsAction.LiveTrackClick -> onLiveTrackClick()
            is EventDetailsAction.LiveTrackConsentAccepted -> onLiveTrackConsentAccepted()
            is EventDetailsAction.LiveTrackConsentDismissed -> updateState { copy(isLiveTrackConsentVisible = false) }
            is EventDetailsAction.LiveTrackPermissionsResult -> onLiveTrackPermissionsResult(action.locationGranted)
        }
    }

    /**
     * Инициализация экрана. Загрузка пользователя и детальной информации о событии.
     * @param eventId Идентификатор события.
     */
    fun initialize(eventId: String) {
        viewModelScope.launch {
            loadingRepository.emit(true)
            currentUser = userRepository.retrieveUser().getOrNull()

            cyclicEventDetailsRepository.getEventDetails(eventId, currentUser?.id)
                .onSuccess { details ->
                    updateState {
                        copy(
                            eventDetails = details,
                            isUserRegistered = details?.isUserRegistered ?: false,
                            groupEligibility = groupEligibility(details, currentUser)
                        )
                    }
                    loadOrganizerClubName(details?.organizingClubId)
                    observeLiveTrack(eventId)
                    loadHasLiveTracks(eventId)
                }
                .onFailure {
                    handleFailure(it)
                }
            loadingRepository.emit(false)

            // Проверить отложенную регистрацию: если вернулись после авторизации
            val pending = pendingRegistrationRepository.pending.value
            if (pending != null && pending.eventId == eventId && pending.groupId == null) {
                pendingRegistrationRepository.clear()
                openRegistrationSheet()
            }
        }
    }

    /**
     * Резолвит название клуба-организатора по [clubId]. Ошибка не критична для экрана —
     * блок организатора просто не отображается.
     */
    private fun loadOrganizerClubName(clubId: String?) {
        if (clubId == null) return
        viewModelScope.launch {
            clubRepository.getById(clubId)
                .onSuccess { club ->
                    updateState { copy(organizerClubName = club.name) }
                }
        }
    }

    private fun showRegistrationDialog() {
        viewModelScope.launch {
            val eventId = stateValue.eventDetails?.eventId
            if (eventId != null) {
                analytics.trackEvent(AnalyticsEvent.EventRegisterClicked(eventId))
            }
            if (!userRepository.isAuthorized()) {
                if (eventId == null) return@launch
                pendingRegistrationRepository.set(eventId)
                navigation.switchTab(TabRoutes.PROFILE)
                return@launch
            }
            openRegistrationSheet()
        }
    }

    /** Показывает BottomSheet регистрации и подгружает подсказки для поля «Команда». */
    private fun openRegistrationSheet() {
        updateState { copy(isRegistrationSheetVisible = true) }
        val eventId = stateValue.eventDetails?.eventId ?: return
        teamOptionsJob?.cancel()
        teamOptionsJob = viewModelScope.launch {
            // Ошибка не критична: поле остаётся обычным текстовым, без подсказок.
            cyclicEventDetailsRepository.getRegistrationTeamOptions(eventId)
                .onSuccess { options ->
                    updateState {
                        copy(
                            teamOptions = options,
                            // Не перетираем то, что пользователь успел ввести, пока шёл запрос.
                            commandName = if (isCommandNameEdited) commandName else options.suggestedCommandName.orEmpty()
                        )
                    }
                    scheduleClubMatch()
                }
        }
    }

    private fun hideRegistrationDialog() {
        teamOptionsJob?.cancel()
        clubMatchJob?.cancel()
        updateState {
            copy(
                isRegistrationSheetVisible = false,
                selectedGroup = null,
                commandName = "",
                isCommandNameEdited = false,
                clubMatches = emptyList()
            )
        }
    }

    private fun changeCommandName(commandName: String) {
        updateState {
            copy(
                commandName = commandName.take(COMMAND_NAME_MAX_LENGTH),
                isCommandNameEdited = true,
                clubMatches = emptyList()
            )
        }
        scheduleClubMatch()
    }

    /**
     * Ищет клуб с названием, совпадающим с подписью команды, чтобы предложить вступить в него.
     * Не ищет, если подпись — своя команда/клуб. Запрос откладывается, пока пользователь печатает.
     */
    private fun scheduleClubMatch() {
        clubMatchJob?.cancel()
        val commandName = stateValue.commandName
        val normalized = normalizeCommandName(commandName) ?: return
        if (normalized.length < CLUB_MATCH_MIN_LENGTH) return
        if (stateValue.teamOptions.ownOptionFor(commandName) != null) return
        clubMatchJob = viewModelScope.launch {
            delay(CLUB_MATCH_DEBOUNCE_MS)
            cyclicEventDetailsRepository.matchClubs(normalized)
                .onSuccess { matches ->
                    updateState { if (this.commandName == commandName) copy(clubMatches = matches) else this }
                }
        }
    }

    /** Переход в карточку клуба (вкладка «Клубы») из подсказки под полем команды. */
    private fun openClub(clubId: String) {
        analytics.trackEvent(AnalyticsEvent.ClubJoinHintClicked(clubId))
        hideRegistrationDialog()
        pendingTabNavigationRepository.set(TabRoutes.CLUBS, ClubsNavigation.ClubDetailRoute(clubId))
        viewModelScope.launch { navigation.switchTab(TabRoutes.CLUBS) }
    }

    private fun selectGroup(group: EventParticipantGroup) {
        if (stateValue.groupEligibility[group.groupId] is GroupEligibility.NotEligible) return
        updateState { copy(selectedGroup = group) }
    }

    /** Проверка пола и возраста для каждой группы; пусто — пользователь не вошёл. */
    private fun groupEligibility(details: CyclicEventDetails?, user: User?): Map<String, GroupEligibility> {
        if (details == null || user == null) return emptyMap()
        val year = competitionYear(details.startDate, details.timeZoneId)
        return details.participantGroups.associate { group ->
            group.groupId to checkGroupEligibility(
                groupTitle = group.title,
                groupGender = group.gender,
                minAge = group.minAge,
                maxAge = group.maxAge,
                userGender = user.gender,
                userBirthDate = user.birthDate,
                competitionYear = year
            )
        }
    }

    /**
     * Подтверждение регистрации с данными текущего пользователя.
     */
    private fun confirmRegistration() {
        val selectedGroup = stateValue.selectedGroup ?: return
        val eventId = stateValue.eventDetails?.eventId ?: return
        val user = currentUser ?: return

        val commandName = stateValue.commandName
        val teamOptions = stateValue.teamOptions

        viewModelScope.launch {
            updateState { copy(isRegistering = true, error = null) }
            cyclicEventDetailsRepository.registerToEvent(
                eventId = eventId,
                groupId = selectedGroup.groupId,
                firstName = user.firstName,
                lastName = user.lastName,
                commandName = normalizeCommandName(commandName),
                teamId = teamOptions.teamIdFor(commandName)
            )
                .onSuccess {
                    val teamSource = teamOptions.teamSourceFor(commandName)
                    analytics.trackEvent(
                        AnalyticsEvent.EventRegistered(
                            eventId = eventId,
                            teamSource = AnalyticsEvent.RegistrationTeamSource.valueOf(teamSource.name)
                        )
                    )
                    clubMatchJob?.cancel()
                    updateState {
                        copy(
                            isRegistering = false,
                            isRegistrationSheetVisible = false,
                            selectedGroup = null,
                            commandName = "",
                            isCommandNameEdited = false,
                            clubMatches = emptyList(),
                            isUserRegistered = true
                        )
                    }
                    refreshLiveTrackEntry()
                }
                .onFailure { e ->
                    updateState {
                        copy(
                            isRegistering = false,
                            error = e.message
                        )
                    }
                    handleFailure(e)
                }
        }
    }

    /**
     * Отмена регистрации.
     */
    private fun cancelRegistration() {
        val eventId = stateValue.eventDetails?.eventId ?: return

        viewModelScope.launch {
            updateState { copy(isRegistering = true, error = null) }
            cyclicEventDetailsRepository.cancelRegistration(eventId)
                .onSuccess {
                    updateState { copy(isRegistering = false, isUserRegistered = false) }
                    refreshLiveTrackEntry()
                }
                .onFailure { e ->
                    updateState { copy(isRegistering = false, error = e.message) }
                    handleFailure(e)
                }
        }
    }

    private fun navigateToGroup(group: EventParticipantGroup) {
        val eventId = stateValue.eventDetails?.eventId ?: return
        viewModelScope.launch {
            navigation.navigate(
                EventsNavigation.EventParticipantGroupRoute(
                    eventId = eventId,
                    participantGroup = group
                )
            )
        }
    }

    private fun navigateToResults() {
        val eventId = stateValue.eventDetails?.eventId ?: return
        viewModelScope.launch {
            navigation.navigate(EventsNavigation.EventResultsRoute(eventId = eventId))
        }
    }

    private fun navigateToLiveResults() {
        val eventId = stateValue.eventDetails?.eventId ?: return
        analytics.trackEvent(AnalyticsEvent.EventLiveResultsOpened(eventId))
        viewModelScope.launch {
            navigation.navigate(EventsNavigation.LiveResultsRoute(eventId = eventId))
        }
    }

    /** Следит за локальной сессией онлайн-трека этого соревнования (она живёт в Room). */
    private fun observeLiveTrack(eventId: String) {
        liveTrackJob?.cancel()
        liveTrackJob = viewModelScope.launch {
            liveTrackLocalRepository.observeSession(eventId).collect { session ->
                liveTrackSession = session
                refreshLiveTrackEntry()
            }
        }
    }

    private fun refreshLiveTrackEntry() {
        val details = stateValue.eventDetails
        val session = liveTrackSession
        val entry = when {
            session != null && (session.isRecording || (session.stopRequested && !session.stopDelivered)) -> LiveTrackEntry.RECORDING
            details != null && stateValue.isUserRegistered && isLiveTrackAvailable(details) -> LiveTrackEntry.START
            else -> LiveTrackEntry.HIDDEN
        }
        updateState { copy(liveTrackEntry = entry) }
    }

    /**
     * Трекинг включают только в день соревнования по ориентированию (± сутки — часовые пояса и
     * многодневки; окончательно решает сервер).
     */
    private fun isLiveTrackAvailable(details: CyclicEventDetails): Boolean {
        if (details.eventType != EventType.CyclicEvent.Orienteering) return false
        if (details.status == EventStatus.FINISHED || details.status == EventStatus.CANCELLED) return false
        val day = TimeUnit.DAYS.toMillis(1)
        val now = System.currentTimeMillis()
        return now in (details.startDate - day)..(maxOf(details.endDate, details.startDate) + day)
    }

    private fun onLiveTrackClick() {
        when (stateValue.liveTrackEntry) {
            LiveTrackEntry.RECORDING -> navigateToLiveTrackRunner()
            LiveTrackEntry.START -> viewModelScope.launch {
                if (liveTrackLocalRepository.isConsentGiven()) {
                    _liveTrackPermissionRequests.emit(Unit)
                } else {
                    updateState { copy(isLiveTrackConsentVisible = true) }
                }
            }
            LiveTrackEntry.HIDDEN -> Unit
        }
    }

    private fun onLiveTrackConsentAccepted() {
        updateState { copy(isLiveTrackConsentVisible = false) }
        viewModelScope.launch {
            liveTrackLocalRepository.setConsentGiven()
            _liveTrackPermissionRequests.emit(Unit)
        }
    }

    private fun onLiveTrackPermissionsResult(locationGranted: Boolean) {
        if (!locationGranted) {
            viewModelScope.launch {
                networkErrorRepository.emit(
                    NetworkErrorEvent(code = null, message = "Без доступа к геолокации трек не записать")
                )
            }
            return
        }
        startLiveTrack()
    }

    /** Старт сессии на сервере → запуск сервиса записи → экран записи. */
    private fun startLiveTrack() {
        val eventId = stateValue.eventDetails?.eventId ?: return
        if (stateValue.isStartingLiveTrack) return
        viewModelScope.launch {
            updateState { copy(isStartingLiveTrack = true) }
            liveTrackEngine.start(eventId)
                .onSuccess { session ->
                    trackingController.startRecording(session.sessionId)
                    navigation.navigate(EventsNavigation.LiveTrackRunnerRoute(eventId = eventId))
                }
                .onFailure { error ->
                    // Отказ сервера (не тот день, уже есть результат…) — показываем его текст.
                    val rejected = error as? LiveTrackRejectedException
                    networkErrorRepository.emit(
                        NetworkErrorEvent(
                            code = rejected?.httpCode,
                            message = rejected?.message ?: "Сервер онлайн-трекинга недоступен, попробуйте ещё раз"
                        )
                    )
                }
            updateState { copy(isStartingLiveTrack = false) }
        }
    }

    /** Есть ли треки для зрителя. Ошибка не критична — кнопка просто не появится (кроме идущего старта). */
    private fun loadHasLiveTracks(eventId: String) {
        viewModelScope.launch {
            liveTrackViewerRepository.distances(eventId).onSuccess { distances ->
                updateState { copy(hasLiveTracks = distances.isNotEmpty()) }
            }
        }
    }

    private fun navigateToLiveTracks() {
        val eventId = stateValue.eventDetails?.eventId ?: return
        analytics.trackEvent(AnalyticsEvent.EventLiveTracksOpened(eventId))
        viewModelScope.launch {
            navigation.navigate(EventsNavigation.LiveTracksRoute(eventId = eventId))
        }
    }

    private fun navigateToLiveTrackRunner() {
        val eventId = stateValue.eventDetails?.eventId ?: return
        viewModelScope.launch {
            navigation.navigate(EventsNavigation.LiveTrackRunnerRoute(eventId = eventId))
        }
    }

    private fun handleFailure(throwable: Throwable) {
        viewModelScope.launch {
            val code = (throwable as? NetworkException)?.code
            networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
        }
    }

    private companion object {
        /** Пауза после последнего ввода перед поиском клуба по названию. */
        const val CLUB_MATCH_DEBOUNCE_MS = 500L

        /** Короче — не ищем клуб: слишком много случайных совпадений. */
        const val CLUB_MATCH_MIN_LENGTH = 2
    }
}

/**
 * Действия на экране деталей события.
 */
sealed interface EventDetailsAction : BaseAction {
    data class OnGroupClick(val group: EventParticipantGroup) : EventDetailsAction
    data object ToResults : EventDetailsAction
    data object ToLiveResults : EventDetailsAction
    data object ShowRegistrationDialog : EventDetailsAction
    data object HideRegistrationDialog : EventDetailsAction
    data class SelectGroup(val group: EventParticipantGroup) : EventDetailsAction
    data class CommandNameChanged(val commandName: String) : EventDetailsAction

    /** Выбрана подсказка под полем «Команда» (своя команда/клуб или подпись из протокола). */
    data class SelectTeamSuggestion(val suggestion: TeamSuggestion) : EventDetailsAction

    /** Открыть карточку клуба из подсказки «такой клуб есть в Competra». */
    data class OpenClub(val clubId: String) : EventDetailsAction
    data object ConfirmRegistration : EventDetailsAction
    /** Перейти в профиль, чтобы указать пол/дату рождения. */
    data object OpenProfile : EventDetailsAction
    data object CancelRegistration : EventDetailsAction

    /** Зритель открывает онлайн-треки участников. */
    data object ToLiveTracks : EventDetailsAction

    /** Кнопка онлайн-трека: включить или открыть экран идущей записи. */
    data object LiveTrackClick : EventDetailsAction

    /** Бегун согласился на публикацию трека. */
    data object LiveTrackConsentAccepted : EventDetailsAction

    /** Бегун закрыл диалог согласия. */
    data object LiveTrackConsentDismissed : EventDetailsAction

    /** Экран проверил/запросил разрешения; [locationGranted] — есть точная геолокация. */
    data class LiveTrackPermissionsResult(val locationGranted: Boolean) : EventDetailsAction
}
