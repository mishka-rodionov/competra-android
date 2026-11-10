package com.competra.data.navigation

import androidx.navigation.NavOptionsBuilder
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed class ProfileNavigation: BaseNavigation {

    @Transient
    @Contextual
    override var navOptionsBuilder: (NavOptionsBuilder.() -> Unit)? = null

    @Serializable
    data object ProfileBaseRoute : ProfileNavigation()
    @Serializable
    data object MainProfileRoute : ProfileNavigation()
    @Serializable
    data object ProfileEditorRoute : ProfileNavigation()
    @Serializable
    data object AboutAppRoute : ProfileNavigation()
    @Serializable
    data object AuthRoute : ProfileNavigation()
    @Serializable
    data class AuthCodeRoute(val email: String) : ProfileNavigation()
    @Serializable
    data object RegistrationRoute : ProfileNavigation()
    @Serializable
    data object UserRegistrationsRoute : ProfileNavigation()
    @Serializable
    data object PushPreferencesRoute : ProfileNavigation()
    /** Список инструкций для организатора (страницы на competra.ru). */
    @Serializable
    data object GuidesRoute : ProfileNavigation()
}