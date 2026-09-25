package com.competra.domain.repository.user

import com.competra.domain.models.CropRect
import com.competra.domain.models.Gender
import com.competra.domain.models.user.User

interface UserProfileRepository {

    suspend fun updateAvatarUrl(avatarUrl: String, cropRect: CropRect? = null): Result<User>

    suspend fun getProfile(): Result<User>

    /**
     * Обновляет основные данные профиля на сервере и возвращает актуального пользователя.
     *
     * @param firstName имя
     * @param lastName фамилия
     * @param middleName отчество
     * @param phoneNumber номер телефона
     * @param gender пол
     */
    suspend fun updateProfile(
        firstName: String,
        lastName: String,
        middleName: String?,
        phoneNumber: String?,
        gender: Gender
    ): Result<User>
}
