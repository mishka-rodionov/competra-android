package com.competra.profile.presentation.main_profile

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.data.navigation.EventsNavigation
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.ProfileNavigation
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.onboarding.OnboardingSource
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.OnboardingRequestRepository
import com.competra.domain.models.participant_link.LinkRequestStatus
import com.competra.domain.repository.participant_link.ParticipantLinkRepository
import com.competra.domain.repository.user.UserRepository
import com.competra.profile.data.ProfileAction
import com.competra.profile.data.ProfileState
import com.competra.profile.data.interactors.AuthInteractor
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel для экрана профиля.
 * Управляет получением данных пользователя и навигацией.
 *
 * @param navigation Сервис навигации.
 * @param userRepository Репозиторий для работы с данными пользователя.
 * @param authInteractor Интерактор для операций аутентификации.
 */
class ProfileViewModel(
    private val navigation: Navigation,
    private val userRepository: UserRepository,
    private val authInteractor: AuthInteractor,
    private val analytics: AnalyticsTracker,
    private val onboardingRequestRepository: OnboardingRequestRepository,
    private val networkErrorRepository: NetworkErrorRepository,
    private val participantLinkRepository: ParticipantLinkRepository,
) : BaseViewModel<ProfileState>(ProfileState()) {

    override fun onAction(action: BaseAction) {

    }

    /**
     * Обработка действий пользователя на экране профиля.
     */
    fun onAction(profileAction: ProfileAction) {
        when (profileAction) {
            ProfileAction.ToAuth -> toAuthorization()
            ProfileAction.ToRegister -> toRegistration()
            ProfileAction.ToProfileEditor -> toProfileEditor()
            ProfileAction.ToUserRegistrations -> openUserRegistrations()
            ProfileAction.ToResultLinks -> viewModelScope.launch {
                navigation.navigate(EventsNavigation.ResultLinksRoute)
            }
            ProfileAction.Logout -> logout()
            ProfileAction.ShowOnboarding -> showOnboardingAgain()
            ProfileAction.ToPushPreferences -> toPushPreferences()
            ProfileAction.ToAboutApp -> toAboutApp()
            ProfileAction.OpenDeleteAccountConfirm -> updateState { copy(showDeleteAccountConfirm = true) }
            ProfileAction.CloseDeleteAccountConfirm -> updateState { copy(showDeleteAccountConfirm = false) }
            ProfileAction.DeleteAccount -> deleteAccount()
        }
    }

    /**
     * Безвозвратно удаляет аккаунт пользователя. Сервер может отклонить запрос
     * (например, если пользователь — организатор действующих соревнований) —
     * в этом случае ошибка показывается пользователю, локальные данные не трогаются.
     */
    private fun deleteAccount() {
        analytics.trackEvent(AnalyticsEvent.AccountDeletionRequested)
        viewModelScope.launch {
            authInteractor.deleteAccount()
                .onSuccess {
                    analytics.trackEvent(AnalyticsEvent.AccountDeletionSucceeded)
                    analytics.setUserId(null)
                    updateState { copy(user = null, showDeleteAccountConfirm = false) }
                }
                .onFailure { throwable ->
                    val code = (throwable as? NetworkException)?.code
                    analytics.trackEvent(AnalyticsEvent.AccountDeletionFailed(reason = code?.toString() ?: "unknown"))
                    networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
                    updateState { copy(showDeleteAccountConfirm = false) }
                }
        }
    }

    /**
     * Переход на экран «О приложении».
     */
    private fun toAboutApp() {
        viewModelScope.launch {
            navigation.navigate(ProfileNavigation.AboutAppRoute)
        }
    }

    /**
     * Переход на экран настроек push-уведомлений.
     */
    private fun toPushPreferences() {
        viewModelScope.launch {
            navigation.navigate(ProfileNavigation.PushPreferencesRoute)
        }
    }

    /**
     * Запрашивает ручной повторный показ онбординга. Не связан с флагом
     * «просмотрено» — не проверяет и не сбрасывает его.
     */
    private fun showOnboardingAgain() {
        viewModelScope.launch {
            onboardingRequestRepository.emit(OnboardingSource.SETTINGS)
        }
    }

    /**
     * Переход на экран регистрации.
     */
    private fun toRegistration() {
        viewModelScope.launch {
            navigation.navigate(ProfileNavigation.RegistrationRoute)
        }
    }

    /**
     * Переход на экран авторизации.
     */
    private fun toAuthorization() {
        viewModelScope.launch {
            navigation.navigate(destination = ProfileNavigation.AuthRoute)
        }
    }

    /**
     * Переход на экран «Предстоящие старты» — список соревнований пользователя.
     */
    private fun openUserRegistrations() {
        viewModelScope.launch {
            navigation.navigate(ProfileNavigation.UserRegistrationsRoute)
        }
    }

    /**
     * Переход на экран редактирования профиля.
     */
    private fun toProfileEditor() {
        analytics.trackEvent(AnalyticsEvent.ProfileEditStarted)
        viewModelScope.launch {
            navigation.navigate(ProfileNavigation.ProfileEditorRoute)
        }
    }

    /**
     * Выполняет выход из аккаунта: очищает токены и данные пользователя.
     */
    private fun logout() {
        viewModelScope.launch {
            authInteractor.logout()
            analytics.trackEvent(AnalyticsEvent.Logout)
            analytics.setUserId(null)
            updateState { copy(user = null) }
        }
    }

    /**
     * Загружает данные текущего авторизованного пользователя.
     */
    fun getCurrentUser() {
        viewModelScope.launch {
            userRepository.retrieveUser().onSuccess { user ->
                analytics.setUserId(user.id)
                updateState {
                    copy(user = user)
                }
                loadLinkCounters()
            }
        }
    }

    /**
     * Счётчики для пункта «Результаты в протоколах». Ошибки не показываем — это подсказка,
     * профиль должен работать и без неё.
     */
    private fun loadLinkCounters() {
        viewModelScope.launch {
            val suggestions = participantLinkRepository.getSuggestions().getOrNull()?.size ?: 0
            val pending = participantLinkRepository.getMyRequests().getOrNull()
                ?.count { it.status == LinkRequestStatus.PENDING } ?: 0
            updateState { copy(linkSuggestionsCount = suggestions, pendingLinkRequestsCount = pending) }
        }
    }

}
