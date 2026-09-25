package com.competra.remote.repository.user

import com.competra.domain.models.CropRect
import com.competra.domain.models.Gender
import com.competra.domain.models.user.User
import com.competra.domain.repository.user.UserProfileRepository
import com.competra.remote.datasource.auth.AuthRemoteDataSource
import com.competra.remote.request.user.UserProfileUpdateRequest
import com.competra.remote.response.mappers.toDomain

class UserProfileRepositoryImpl(
    private val authRemoteDataSource: AuthRemoteDataSource
) : UserProfileRepository {

    override suspend fun updateAvatarUrl(avatarUrl: String, cropRect: CropRect?): Result<User> {
        return authRemoteDataSource.updateProfile(
            UserProfileUpdateRequest(
                avatarUrl = avatarUrl,
                avatarCropX = cropRect?.x,
                avatarCropY = cropRect?.y,
                avatarCropWidth = cropRect?.width,
                avatarCropHeight = cropRect?.height
            )
        ).mapCatching { it.result!!.toDomain() }
    }

    override suspend fun getProfile(): Result<User> {
        return authRemoteDataSource.getProfile()
            .mapCatching { it.result!!.toDomain() }
    }

    override suspend fun updateProfile(
        firstName: String,
        lastName: String,
        middleName: String?,
        phoneNumber: String?,
        gender: Gender
    ): Result<User> {
        return authRemoteDataSource.updateProfile(
            UserProfileUpdateRequest(
                firstName = firstName,
                lastName = lastName,
                middleName = middleName,
                phoneNumber = phoneNumber,
                gender = gender
            )
        ).mapCatching { it.result!!.toDomain() }
    }
}
