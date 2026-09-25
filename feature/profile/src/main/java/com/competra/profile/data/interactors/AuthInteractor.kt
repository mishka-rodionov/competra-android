package com.competra.profile.data.interactors

import com.competra.domain.models.Gender
import com.competra.domain.models.auth.Token
import com.competra.domain.models.user.User
import com.competra.domain.repository.auth.AuthRepository
import com.competra.domain.repository.auth.TokenRepository
import com.competra.domain.repository.fcm.FcmTokenRegistry
import com.competra.domain.repository.user.UserRepository

class AuthInteractor(
    private val authRepository: AuthRepository,
    private val tokenRepository: TokenRepository,
    private val userRepository: UserRepository,
    private val fcmTokenRegistry: FcmTokenRegistry,
) {

    suspend fun authorize(email: String, code: String): Result<Any> {
        return authRepository.authorize(email, code).mapCatching {
            val (user, token) = it
            retrieveTokenAndSaveUser(token, user)
        }
    }

    private suspend fun retrieveTokenAndSaveUser(
        token: Token,
        user: User
    ) {
        val accessToken = token.accessToken
        val refreshToken = token.refreshToken
        if (accessToken != null && refreshToken != null) {
            tokenRepository.saveTokens(accessToken, refreshToken)
        }
        userRepository.saveUser(user)
        runCatching { fcmTokenRegistry.refresh() }
    }

    suspend fun logout(): Result<Unit> {
        return runCatching {
            runCatching { fcmTokenRegistry.unregisterCurrent() }
            tokenRepository.clear()
            userRepository.clearUser()
        }
    }

    suspend fun register(firstName: String, lastName: String, bdate: Long, gender: Gender, email: String, privacyAccepted: Boolean): Result<Any> {
        return authRepository.register(firstName, lastName, bdate, gender, email, privacyAccepted)
    }

    /**
     * Удаляет аккаунт пользователя на сервере и очищает локальные данные (токены, профиль).
     */
    suspend fun deleteAccount(): Result<Unit> {
        return authRepository.deleteAccount().mapCatching {
            runCatching { fcmTokenRegistry.unregisterCurrent() }
            tokenRepository.clear()
            userRepository.clearUser()
        }
    }

}