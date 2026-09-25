package com.competra.profile.data.registration

import android.util.Patterns
import com.competra.domain.models.Gender
import com.competra.ui.BaseState

/**
 * Состояние экрана регистрации.
 *
 * @property email адрес электронной почты
 * @property firstName имя
 * @property lastName фамилия
 * @property bdate дата рождения в epoch millis, 0 — не выбрана
 * @property gender пол, null — не выбран
 * @property privacyAccepted согласие с политикой конфиденциальности
 * @property isLoading идёт отправка запроса на регистрацию
 */
data class RegistrationState(
    var email: String = "",
    var firstName: String = "",
    var lastName: String = "",
    var bdate: Long = 0L,
    val gender: Gender? = null,
    var privacyAccepted: Boolean = false,
    val isLoading: Boolean = false
): BaseState {

    /** Email введён и соответствует формату адреса. */
    val isEmailValid: Boolean
        get() = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    /** Все обязательные поля заполнены и согласие получено — можно отправлять форму. */
    val canSubmit: Boolean
        get() = firstName.isNotBlank() &&
                lastName.isNotBlank() &&
                bdate != 0L &&
                gender != null &&
                isEmailValid &&
                privacyAccepted &&
                !isLoading
}
