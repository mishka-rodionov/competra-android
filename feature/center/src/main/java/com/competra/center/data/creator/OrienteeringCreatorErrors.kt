package com.competra.center.data.creator

data class OrienteeringCreatorErrors(
    val isEmptyAddress: Boolean = false,
    val isEmptyGroup: Boolean = false,
    val isGroupTitleError: Boolean = false,
    val isGroupDistanceError: Boolean = false,
    val isCountOfControlsError: Boolean = false,
    val isMaxTimeError: Boolean = false,
    val isEmptyRegistrationStart: Boolean = false,
    val isEmptyRegistrationEnd: Boolean = false,
    val isEmptyContactPhone: Boolean = false,
    /** Регистрация открывается позже старта соревнования. */
    val isRegistrationStartAfterCompetition: Boolean = false
) {

    fun checkErrors() : Boolean {
        return !isEmptyAddress && !isEmptyGroup && !isGroupTitleError && !isGroupDistanceError
            && !isCountOfControlsError && !isMaxTimeError
            && !isEmptyRegistrationStart && !isEmptyRegistrationEnd
            && !isEmptyContactPhone && !isRegistrationStartAfterCompetition
    }

}
