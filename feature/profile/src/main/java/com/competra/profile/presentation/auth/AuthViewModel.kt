package com.competra.profile.presentation.auth

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.ProfileNavigation
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.auth.AuthRepository
import com.competra.profile.data.auth.AuthAction
import com.competra.profile.data.auth.AuthState
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

class AuthViewModel(
    private val navigation: Navigation,
    private val authRepository: AuthRepository,
    private val networkErrorRepository: NetworkErrorRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<AuthState>(AuthState()) {

    override fun onAction(action: BaseAction) {

        when (action) {
            is AuthAction.AuthClicked -> {
                if (stateValue.isLoading) return

                analytics.trackEvent(
                    AnalyticsEvent.AuthLoginRequested(action.email.substringAfter('@', ""))
                )
                updateState { copy(isLoading = true) }
                viewModelScope.launch {
                    authRepository.login(action.email).onSuccess {
                        updateState { copy(isLoading = false) }
                        navigation.navigate(destination = ProfileNavigation.AuthCodeRoute(action.email))
                    }.onFailure {
                        updateState { copy(isLoading = false) }
                        handleFailure(it)
                    }
                }
            }

            AuthAction.ToRegistration -> viewModelScope.launch {
                navigation.navigate(destination = ProfileNavigation.RegistrationRoute)
            }

            else -> {}
        }

    }

    private fun handleFailure(throwable: Throwable) {
        viewModelScope.launch {
            val code = (throwable as? NetworkException)?.code
            networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
        }
    }

}