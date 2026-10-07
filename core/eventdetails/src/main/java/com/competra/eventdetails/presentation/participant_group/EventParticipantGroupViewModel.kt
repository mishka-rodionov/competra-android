package com.competra.eventdetails.presentation.participant_group

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.data.navigation.ClubsNavigation
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.PendingRegistrationRepository
import com.competra.data.navigation.PendingTabNavigationRepository
import com.competra.data.navigation.TabRoutes
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.cyclic_event.EventParticipantGroup
import com.competra.domain.models.cyclic_event.GroupEligibility
import com.competra.domain.models.cyclic_event.checkGroupEligibility
import com.competra.domain.models.cyclic_event.competitionYear
import com.competra.domain.models.cyclic_event.normalizeCommandName
import com.competra.domain.models.cyclic_event.teamIdFor
import com.competra.domain.models.cyclic_event.teamSourceFor
import com.competra.domain.models.cyclic_event.TeamSuggestion
import com.competra.domain.models.user.User
import com.competra.domain.repository.LoadingRepository
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.events.CyclicEventDetailsRepository
import com.competra.domain.repository.user.UserRepository
import com.competra.eventdetails.data.participant_group.EventParticipantGroupState
import com.competra.eventdetails.data.registration.RegistrationTeamState
import com.competra.eventdetails.presentation.registration.RegistrationTeamDelegate
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Вьюмодель экрана группы участников события.
 * @property repository Репозиторий для получения данных о событии.
 * @property userRepository Репозиторий пользователя.
 * @property navigation Сервис навигации.
 * @property pendingRegistrationRepository Хранилище отложенного действия регистрации.
 * @property networkErrorRepository Репозиторий для передачи сетевых ошибок в MainActivity.
 * @property analytics Трекер аналитики.
 * @property pendingTabNavigationRepository Отложенный переход в карточку клуба во вкладке «Клубы».
 */
class EventParticipantGroupViewModel(
    private val repository: CyclicEventDetailsRepository,
    private val userRepository: UserRepository,
    private val navigation: Navigation,
    private val pendingRegistrationRepository: PendingRegistrationRepository,
    private val networkErrorRepository: NetworkErrorRepository,
    private val loadingRepository: LoadingRepository,
    private val analytics: AnalyticsTracker,
    private val pendingTabNavigationRepository: PendingTabNavigationRepository
) : BaseViewModel<EventParticipantGroupState>(EventParticipantGroupState()) {

    private var currentUser: User? = null

    private val registrationTeam = RegistrationTeamDelegate(repository, viewModelScope)

    /** Состояние поля «Клуб/команда» в шторке регистрации. */
    val registrationTeamState: StateFlow<RegistrationTeamState> = registrationTeam.state

    override fun onAction(action: BaseAction) {
        when (action) {
            is EventParticipantGroupAction.RegisterUser -> registerUser()
            is EventParticipantGroupAction.HideRegistrationSheet -> hideRegistrationSheet()
            is EventParticipantGroupAction.CommandNameChanged -> registrationTeam.changeCommandName(action.commandName)
            is EventParticipantGroupAction.SelectTeamSuggestion -> registrationTeam.changeCommandName(action.suggestion.label)
            is EventParticipantGroupAction.OpenClub -> openClub(action.clubId)
            is EventParticipantGroupAction.ConfirmRegistration -> confirmRegistration()
            is EventParticipantGroupAction.CancelRegistration -> cancelRegistration()
            is EventParticipantGroupAction.OpenProfile -> viewModelScope.launch { navigation.switchTab(TabRoutes.PROFILE) }
        }
    }

    /**
     * Инициализация данных экрана.
     * @param eventId Идентификатор события.
     * @param group Данные группы участников.
     */
    fun initialize(eventId: String, group: EventParticipantGroup) {
        updateState { copy(eventId = eventId, participantGroup = group, isLoading = true) }
        viewModelScope.launch {
            loadingRepository.emit(true)
            currentUser = userRepository.retrieveUser().getOrNull()

            repository.getEventDetails(eventId, currentUser?.id)
                .onSuccess { details ->
                    val year = details?.let { competitionYear(it.startDate, it.timeZoneId) }
                    val user = currentUser
                    updateState {
                        copy(
                            eventStatus = details?.status,
                            isUserRegisteredInEvent = details?.isUserRegistered ?: false,
                            competitionYear = year,
                            eligibility = if (user != null && year != null) {
                                checkGroupEligibility(
                                    groupTitle = group.title,
                                    groupGender = group.gender,
                                    minAge = group.minAge,
                                    maxAge = group.maxAge,
                                    userGender = user.gender,
                                    userBirthDate = user.birthDate,
                                    competitionYear = year
                                )
                            } else null
                        )
                    }
                }

            loadParticipants()
            updateState { copy(isLoading = false) }
            loadingRepository.emit(false)

            // Проверить отложенную регистрацию: если вернулись после авторизации
            val pending = pendingRegistrationRepository.pending.value
            if (pending != null && pending.eventId == eventId && pending.groupId == group.groupId) {
                pendingRegistrationRepository.clear()
                registerUser()
            }
        }
    }

    /**
     * Загружает участников группы и определяет, зарегистрирован ли в ней текущий пользователь.
     */
    private suspend fun loadParticipants() {
        val eventId = stateValue.eventId ?: return
        val group = stateValue.participantGroup ?: return
        repository.getParticipants(eventId, group.groupId)
            .onSuccess { participants ->
                val isRegistered = currentUser?.id?.let { userId ->
                    participants.any { it.userId == userId }
                } ?: false
                updateState { copy(participants = participants, isUserRegistered = isRegistered) }
            }
            .onFailure { handleFailure(it) }
    }

    /**
     * Нажата «Зарегистрироваться»: открывает шторку с полем команды.
     * Если пользователь не авторизован — сохраняет отложенное действие и переходит на Profile таб.
     */
    private fun registerUser() {
        val eventId = stateValue.eventId ?: return
        val group = stateValue.participantGroup ?: return

        viewModelScope.launch {
            analytics.trackEvent(AnalyticsEvent.EventRegisterClicked(eventId))
            if (!userRepository.isAuthorized()) {
                pendingRegistrationRepository.set(eventId, group.groupId)
                navigation.switchTab(TabRoutes.PROFILE)
                return@launch
            }

            if (currentUser == null) return@launch
            // Кнопка в этом случае неактивна, но сюда же ведёт отложенная регистрация после входа.
            if (stateValue.eligibility is GroupEligibility.NotEligible) return@launch
            updateState { copy(isRegistrationSheetVisible = true) }
            registrationTeam.load(eventId)
        }
    }

    private fun hideRegistrationSheet() {
        registrationTeam.reset()
        updateState { copy(isRegistrationSheetVisible = false) }
    }

    /** Переход в карточку клуба (вкладка «Клубы») из подсказки под полем команды. */
    private fun openClub(clubId: String) {
        analytics.trackEvent(AnalyticsEvent.ClubJoinHintClicked(clubId))
        hideRegistrationSheet()
        pendingTabNavigationRepository.set(TabRoutes.CLUBS, ClubsNavigation.ClubDetailRoute(clubId))
        viewModelScope.launch { navigation.switchTab(TabRoutes.CLUBS) }
    }

    /**
     * Регистрация в группу с подписью команды из шторки; после успеха перезагружает список участников.
     */
    private fun confirmRegistration() {
        val eventId = stateValue.eventId ?: return
        val group = stateValue.participantGroup ?: return
        val user = currentUser ?: return
        val commandName = registrationTeam.state.value.commandName
        val teamOptions = registrationTeam.state.value.teamOptions

        viewModelScope.launch {
            updateState { copy(isRegistering = true) }
            repository.registerToEvent(
                eventId = eventId,
                groupId = group.groupId,
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
                    registrationTeam.reset()
                    updateState {
                        copy(
                            isRegistrationSheetVisible = false,
                            isUserRegistered = true,
                            isUserRegisteredInEvent = true
                        )
                    }
                    loadParticipants()
                    updateState { copy(isRegistering = false) }
                }
                .onFailure {
                    updateState { copy(isRegistering = false) }
                    handleFailure(it)
                }
        }
    }

    /**
     * Отмена регистрации пользователя.
     */
    private fun cancelRegistration() {
        val eventId = stateValue.eventId ?: return
        viewModelScope.launch {
            updateState { copy(isRegistering = true) }
            repository.cancelRegistration(eventId)
                .onSuccess {
                    updateState { copy(isUserRegistered = false, isUserRegisteredInEvent = false) }
                    loadParticipants()
                    updateState { copy(isRegistering = false) }
                }
                .onFailure {
                    updateState { copy(isRegistering = false) }
                    handleFailure(it)
                }
        }
    }

    private fun handleFailure(throwable: Throwable) {
        viewModelScope.launch {
            val code = (throwable as? NetworkException)?.code
            networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
        }
    }
}

/**
 * Действия на экране группы участников события.
 */
sealed interface EventParticipantGroupAction : BaseAction {
    /** «Зарегистрироваться»: открыть шторку регистрации (или войти, если не авторизован). */
    data object RegisterUser : EventParticipantGroupAction

    /** Закрыть шторку регистрации. */
    data object HideRegistrationSheet : EventParticipantGroupAction

    /** Изменился текст поля «Клуб/команда». */
    data class CommandNameChanged(val commandName: String) : EventParticipantGroupAction

    /** Выбрана подсказка под полем «Команда» (своя команда/клуб или подпись из протокола). */
    data class SelectTeamSuggestion(val suggestion: TeamSuggestion) : EventParticipantGroupAction

    /** Открыть карточку клуба из подсказки «такой клуб есть в Competra». */
    data class OpenClub(val clubId: String) : EventParticipantGroupAction

    /** Подтвердить регистрацию в шторке. */
    data object ConfirmRegistration : EventParticipantGroupAction
    data object CancelRegistration : EventParticipantGroupAction
    /** Перейти в профиль, чтобы указать пол/дату рождения. */
    data object OpenProfile : EventParticipantGroupAction
}
