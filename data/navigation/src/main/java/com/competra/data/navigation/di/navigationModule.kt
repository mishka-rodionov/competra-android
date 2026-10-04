package com.competra.data.navigation.di

import com.competra.data.navigation.Navigation
import com.competra.data.navigation.NavigationImpl
 import com.competra.data.navigation.PendingTabNavigationRepository
 import com.competra.data.navigation.PendingRegistrationRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val navigationModule = module {
    singleOf<Navigation>(::NavigationImpl)
    single { PendingRegistrationRepository() }
    single { PendingTabNavigationRepository() }
}