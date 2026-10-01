package com.competra.app.di

import com.competra.domain.repository.LoadingRepository
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.OnboardingRequestRepository
import com.competra.domain.repository.ResultConflictRepository
import com.competra.app.presentation.main.MainViewModel
import com.competra.app.service.CompetitionStartAlertRepository
import com.competra.app.service.CompetitionServiceControllerImpl
import com.competra.app.service.WorkoutTrackingControllerImpl
import com.competra.ui.CompetitionServiceController
import com.competra.ui.CompetitionStartTimeRepository
import com.competra.ui.WorkoutTrackingController
import com.competra.ui.WorkoutTrackingRepository
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val mainModule = module {
    viewModelOf(::MainViewModel)
    single { CompetitionStartAlertRepository() }
    single<CompetitionServiceController> { CompetitionServiceControllerImpl() }
    single { CompetitionStartTimeRepository() }
    single<WorkoutTrackingController> { WorkoutTrackingControllerImpl() }
    single { WorkoutTrackingRepository() }
    single { ResultConflictRepository() }
    single { NetworkErrorRepository() }
    single { LoadingRepository() }
    single { OnboardingRequestRepository() }
}