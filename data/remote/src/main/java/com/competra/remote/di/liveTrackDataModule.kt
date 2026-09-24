package com.competra.remote.di

import com.competra.domain.repository.livetrack.LiveTrackRemoteRepository
import com.competra.remote.datasource.livetrack.LiveTrackRemoteDataSource
import com.competra.remote.repository.livetrack.LiveTrackRemoteRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit

/** API онлайн-трекинга бегуна: отдельный Retrofit [LIVE_TRACK_RETROFIT]. */
val liveTrackDataModule = module {
    single<LiveTrackRemoteDataSource> { get<Retrofit>(LIVE_TRACK_RETROFIT).create(LiveTrackRemoteDataSource::class.java) }
    singleOf(::LiveTrackRemoteRepositoryImpl) bind LiveTrackRemoteRepository::class
}
