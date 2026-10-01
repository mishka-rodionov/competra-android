package com.competra.remote.di

import com.competra.domain.repository.participant_link.ParticipantLinkRepository
import com.competra.remote.datasource.orienteering.ParticipantLinkRemoteDataSource
import com.competra.remote.extension.singleRemoteDataSourceOf
import com.competra.remote.repository.orienteering.ParticipantLinkRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val participantLinkDataModule = module {
    singleRemoteDataSourceOf(ParticipantLinkRemoteDataSource::class.java)
    singleOf(::ParticipantLinkRepositoryImpl) bind ParticipantLinkRepository::class
}
