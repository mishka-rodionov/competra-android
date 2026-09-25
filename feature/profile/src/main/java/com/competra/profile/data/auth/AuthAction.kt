package com.competra.profile.data.auth

import com.competra.ui.BaseAction

sealed class AuthAction: BaseAction {

    data class AuthClicked(val email: String): AuthAction()
    data class AuthCodeEntered(val code: String): AuthAction()
    data object ToRegistration: AuthAction()
}