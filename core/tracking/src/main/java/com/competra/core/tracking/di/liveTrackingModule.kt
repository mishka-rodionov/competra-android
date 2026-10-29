package com.competra.core.tracking.di

import com.competra.core.tracking.CompetitionTrackingController
import com.competra.core.tracking.CompetitionTrackingControllerImpl
import com.competra.core.tracking.GpsLocationSource
import com.competra.core.tracking.LiveTrackEngine
import com.competra.core.tracking.LiveTrackFlushWorker
import com.competra.core.tracking.LiveTrackRecorderState
import com.competra.core.tracking.LiveTrackStarter
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

/** Онлайн-трекинг бегуна: движок, запуск из UI, состояние записи, контроллер сервиса, воркер досылки. */
val liveTrackingModule = module {
    single { LiveTrackRecorderState() }
    single { LiveTrackEngine(get(), get(), get(), get()) }
    single<CompetitionTrackingController> { CompetitionTrackingControllerImpl() }
    single { LiveTrackStarter(get(), get(), get(), androidContext()) }
    factory { GpsLocationSource(androidContext()) }
    workerOf(::LiveTrackFlushWorker)
}
