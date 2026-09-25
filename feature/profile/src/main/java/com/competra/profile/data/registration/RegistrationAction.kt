package com.competra.profile.data.registration

import com.competra.domain.models.Gender
import com.competra.ui.BaseAction

sealed class RegistrationAction: BaseAction {

    data object RegisterUser: RegistrationAction()

    data class UpdateEmail(val email: String): RegistrationAction()
    data class UpdateFirstName(val firstName: String): RegistrationAction()
    data class UpdateLastName(val lastName: String): RegistrationAction()
    data class UpdateBdate(val bdate: Long): RegistrationAction()
    data class UpdateGender(val gender: Gender): RegistrationAction()
    data class UpdatePrivacyAccepted(val accepted: Boolean): RegistrationAction()

}