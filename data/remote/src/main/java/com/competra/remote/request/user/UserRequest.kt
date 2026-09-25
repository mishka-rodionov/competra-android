package com.competra.remote.request.user

import com.competra.domain.models.Gender
import com.google.gson.annotations.SerializedName

/**
 * Модель запроса для создания или обновления пользователя.
 *
 * @property firstName Имя пользователя.
 * @property lastName Фамилия пользователя.
 * @property birthDate Дата рождения пользователя (в миллисекундах).
 * @property email Электронная почта пользователя.
 * @property gender Пол пользователя.
 * @property privacyAccepted Согласие на обработку персональных данных.
 */
data class UserRequest(
    @SerializedName("first_name")
    val firstName: String,
    @SerializedName("last_name")
    val lastName: String,
    @SerializedName("birth_date")
    val birthDate: Long,
    @SerializedName("email")
    val email: String,
    @SerializedName("gender")
    val gender: Gender,
    @SerializedName("privacy_accepted")
    val privacyAccepted: Boolean
)
