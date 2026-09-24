package com.competra.local.di

import com.competra.domain.repository.livetrack.LiveTrackLocalRepository
import com.competra.local.repository.livetrack.LiveTrackLocalRepositoryImpl
import com.competra.domain.repository.auth.TokenRepository
import com.competra.domain.repository.diary.WorkoutLocalRepository
import com.competra.domain.repository.onboarding.OnboardingRepository
import com.competra.domain.repository.orienteering.OrienteeringCompetitionLocalRepository
import com.competra.domain.repository.push.PushPreferencesRepository
import com.competra.domain.repository.user.UserRepository
import com.competra.local.repository.OrienteeringCompetitionLocalRepositoryImpl
import com.competra.local.repository.auth.TokenRepositoryImpl
import com.competra.local.repository.diary.WorkoutLocalRepositoryImpl
import com.competra.local.repository.onboarding.OnboardingRepositoryImpl
import com.competra.local.repository.push.PushPreferencesRepositoryImpl
import com.competra.local.repository.user.UserRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val localModule = module {
    singleOf(::TokenRepositoryImpl) bind TokenRepository::class
    singleOf(::OrienteeringCompetitionLocalRepositoryImpl) bind OrienteeringCompetitionLocalRepository::class
    singleOf(::UserRepositoryImpl) bind UserRepository::class
    singleOf(::OnboardingRepositoryImpl) bind OnboardingRepository::class
    singleOf(::WorkoutLocalRepositoryImpl) bind WorkoutLocalRepository::class
    singleOf(::PushPreferencesRepositoryImpl) bind PushPreferencesRepository::class
    singleOf(::LiveTrackLocalRepositoryImpl) bind LiveTrackLocalRepository::class
}