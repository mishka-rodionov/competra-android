package com.competra.eventdetails.presentation.participant_group

import androidx.lifecycle.viewModelScope
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.PendingRegistrationRepository
import com.competra.data.navigation.TabRoutes
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.cyclic_event.EventParticipantGroup
import com.competra.domain.models.cyclic_event.GroupEligibility
import com.competra.domain.models.cyclic_event.checkGroupEligibility
import com.competra.domain.models.cyclic_event.competitionYear
import com.competra.domain.models.user.User
import com.competra.domain.repository.LoadingRepository
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.events.CyclicEventDetailsRepository
import com.competra.domain.repository.user.UserRepository
import com.competra.eventdetails.data.participant_group.EventParticipantGroupState
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

/**
 * Вьюмодель экрана группы участников события.
 * @property repository Репозиторий для получения данных о событии.
 * @property userRepository Репозиторий пользователя.
 * @property navigation Сервис навигации.
 * @property pendingRegistrationRepository Хранилище отложенного действия регистрации.
 * @property networkErrorRepository Репозиторий для передачи сетевых ошибок в MainActivity.
 */
class EventParticipantGroupViewModel(
    private val repository: CyclicEventDetailsRepository,
    private val userRepository: UserRepository,
    private val navigation: Navigation,
    private val pendingRegistrationRepository: PendingRegistrationRepository,
    private val networkErrorRepository: NetworkErrorRepository,
    private val loadingRepository: LoadingRepository
) : BaseViewModel<EventParticipantGroupState>(EventParticipantGroupState()) {

    private var currentUser: User? = null

    override fun onAction(action: BaseAction) {
        when (action) {
            is EventParticipantGroupAction.RegisterUser -> registerUser()
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

            repository.getParticipants(eventId, group.groupId)
                .onSuccess { participants ->
                    val isRegistered = currentUser?.id?.let { userId ->
                        participants.any { it.userId == userId }
                    } ?: false
                    updateState {
                        copy(
                            participants = participants,
                            isUserRegistered = isRegistered,
                            isLoading = false
                        )
                    }
                }
                .onFailure {
                    updateState { copy(isLoading = false) }
                    handleFailure(it)
                }
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
     * Регистрация пользователя в группу.
     * Если пользователь не авторизован — сохраняет отложенное действие и переходит на Profile таб.
     */
    private fun registerUser() {
        val eventId = stateValue.eventId ?: return
        val group = stateValue.participantGroup ?: return

        viewModelScope.launch {
            if (!userRepository.isAuthorized()) {
                pendingRegistrationRepository.set(eventId, group.groupId)
                navigation.switchTab(TabRoutes.PROFILE)
                return@launch
            }

            val user = currentUser ?: return@launch
            // Кнопка в этом случае неактивна, но сюда же ведёт отложенная регистрация после входа.
            if (stateValue.eligibility is GroupEligibility.NotEligible) return@launch
            updateState { copy(isRegistering = true) }
            repository.registerToEvent(
                eventId = eventId,
                groupId = group.groupId,
                firstName = user.firstName,
                lastName = user.lastName
            )
                .onSuccess {
                    updateState { copy(isRegistering = false, isUserRegistered = true, isUserRegisteredInEvent = true) }
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
                    updateState { copy(isRegistering = false, isUserRegistered = false, isUserRegisteredInEvent = false) }
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
    data object RegisterUser : EventParticipantGroupAction
    data object CancelRegistration : EventParticipantGroupAction
    /** Перейти в профиль, чтобы указать пол/дату рождения. */
    data object OpenProfile : EventParticipantGroupAction
}
