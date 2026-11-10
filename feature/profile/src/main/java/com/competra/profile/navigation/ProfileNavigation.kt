package com.competra.profile.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.competra.data.navigation.ProfileNavigation
import com.competra.eventdetails.navigation.eventDetailsGraph
import com.competra.profile.presentation.about_app.AboutAppScreen
import com.competra.profile.presentation.auth.AuthScreen
import com.competra.profile.presentation.auth_code.AuthCodeScreen
import com.competra.profile.presentation.guides.GuidesScreen
import com.competra.profile.presentation.main_profile.ProfileScreen
import com.competra.profile.presentation.profile_editor.ProfileEditorScreen
import com.competra.profile.presentation.push_preferences.PushPreferencesScreen
import com.competra.profile.presentation.registration.RegistrationScreen
import com.competra.profile.presentation.user_registrations.UserRegistrationsScreen

fun NavGraphBuilder.profileNavigation() {
    composable<ProfileNavigation.MainProfileRoute> { ProfileScreen() }
    composable<ProfileNavigation.ProfileEditorRoute> { ProfileEditorScreen() }
    composable<ProfileNavigation.AboutAppRoute> { AboutAppScreen() }
    composable<ProfileNavigation.AuthRoute> { AuthScreen() }
    composable<ProfileNavigation.AuthCodeRoute> {
        val route = it.toRoute<ProfileNavigation.AuthCodeRoute>()
        AuthCodeScreen(userEmail = route.email)
    }
    composable<ProfileNavigation.RegistrationRoute> { RegistrationScreen() }
    composable<ProfileNavigation.UserRegistrationsRoute> { UserRegistrationsScreen() }
    composable<ProfileNavigation.PushPreferencesRoute> { PushPreferencesScreen() }
    composable<ProfileNavigation.GuidesRoute> { GuidesScreen() }
    eventDetailsGraph()
}