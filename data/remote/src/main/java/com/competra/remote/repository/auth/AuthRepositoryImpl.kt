package com.competra.remote.repository.auth

import com.competra.domain.models.Gender
import com.competra.domain.models.auth.Token
import com.competra.domain.models.user.User
import com.competra.domain.repository.auth.AuthRepository
import com.competra.domain.repository.auth.TokenRepository
import com.competra.remote.datasource.auth.AuthRemoteDataSource
import com.competra.remote.request.auth.AuthCodeRequest
import com.competra.remote.request.auth.EmailRequest
import com.competra.remote.request.user.UserRequest
import com.competra.remote.response.mappers.toDomain

class AuthRepositoryImpl(
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val tokenRepository: TokenRepository
) : AuthRepository {

    override suspend fun login(email: String): Result<Any> {
        return authRemoteDataSource.login(EmailRequest(email))
    }

    override suspend fun authorize(email: String, code: String): Result<Pair<User, Token>> {
        return authRemoteDataSource.sendAuthCode(AuthCodeRequest(email, code))/*.onSuccess {
            Log.d("LOG_TAG", "sendAuthCode: ${it}")
            it.result?.let { authResponse ->
                tokenRepository.saveTokens(
                    authResponse.token.accessToken,
                    authResponse.token.refreshToken
                )
            }
        }.onFailure {
            Log.d("LOG_TAG", "sendAuthCode: $it")
        }*/.mapCatching { it.result!!.user.toDomain() to it.result.token.toDomain() }
    }

    override suspend fun register(
        firstName: String,
        lastName: String,
        bdate: Long,
        gender: Gender,
        email: String,
        privacyAccepted: Boolean
    ): Result<Any> {
        return authRemoteDataSource.register(
            UserRequest(
                firstName = firstName,
                lastName = lastName,
                birthDate = bdate,
                email = email,
                gender = gender,
                privacyAccepted = privacyAccepted
            )
        )
    }

    override suspend fun deleteAccount(): Result<Any> {
        return authRemoteDataSource.deleteAccount()
    }
}