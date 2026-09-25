package com.competra.profile.presentation.registration

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.ProfileNavigation
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.profile.data.interactors.AuthInteractor
import com.competra.profile.data.registration.RegistrationAction
import com.competra.profile.data.registration.RegistrationState
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

class RegistrationViewModel(
    private val navigation: Navigation,
    private val authInteractor: AuthInteractor,
    private val networkErrorRepository: NetworkErrorRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<RegistrationState>(RegistrationState()) {

    override fun onAction(action: BaseAction) {
        when (action) {
            RegistrationAction.RegisterUser -> registerUser()
            is RegistrationAction.UpdateEmail -> updateState { copy(email = action.email) }
            is RegistrationAction.UpdateFirstName -> updateState { copy(firstName = action.firstName) }
            is RegistrationAction.UpdateLastName -> updateState { copy(lastName = action.lastName) }
            is RegistrationAction.UpdateBdate -> updateState { copy(bdate = action.bdate) }
            is RegistrationAction.UpdateGender -> updateState { copy(gender = action.gender) }
            is RegistrationAction.UpdatePrivacyAccepted -> updateState { copy(privacyAccepted = action.accepted) }
        }
    }

    fun registerUser() {
        val gender = state.value.gender
        if (!state.value.canSubmit || gender == null) return
        analytics.trackEvent(AnalyticsEvent.RegistrationSubmitted)
        updateState { copy(isLoading = true) }
        viewModelScope.launch {
            with(state.value) {
                val trimmedEmail = email.trim()
                authInteractor.register(
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    bdate = bdate,
                    gender = gender,
                    email = trimmedEmail,
                    privacyAccepted = privacyAccepted
                ).onSuccess {
                    analytics.trackEvent(AnalyticsEvent.RegistrationSuccess)
                    navigation.navigate(destination = ProfileNavigation.AuthCodeRoute(trimmedEmail))
                }.onFailure {
                    handleFailure(it)
                }
            }
            updateState { copy(isLoading = false) }
        }
    }

    private fun handleFailure(throwable: Throwable) {
        viewModelScope.launch {
            val code = (throwable as? NetworkException)?.code
            networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
        }
    }

}
