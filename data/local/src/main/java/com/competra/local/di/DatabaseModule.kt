package com.competra.local.di

import com.competra.local.dao.OrienteeringCompetitionDao
import com.competra.local.dao.ParticipantGroupDao
import com.competra.local.dao.UserDao
import com.competra.local.dao.orienteering.DistanceDao
import com.competra.local.dao.orienteering.OrienteeringParticipantDao
import com.competra.local.dao.orienteering.OrienteeringResultDao
import com.competra.local.dao.diary.WorkoutDao
import com.competra.local.dao.livetrack.LiveTrackDao
import com.competra.local.database.DatabaseHelper
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val databaseModule = module {
    singleOf(::DatabaseHelper)
    single<UserDao> { get<DatabaseHelper>().db.userDao() }
    single<OrienteeringCompetitionDao> { get<DatabaseHelper>().db.orienteeringCompetitionDao() }
    single<ParticipantGroupDao> { get<DatabaseHelper>().db.participantGroupsDao() }
    single<OrienteeringParticipantDao> { get<DatabaseHelper>().db.orienteeringParticipantDao() }
    single<OrienteeringResultDao> { get<DatabaseHelper>().db.orienteeringResultDao() }
    single<DistanceDao> { get<DatabaseHelper>().db.distanceDao() }
    single<WorkoutDao> { get<DatabaseHelper>().db.workoutDao() }
    single<LiveTrackDao> { get<DatabaseHelper>().db.liveTrackDao() }
}