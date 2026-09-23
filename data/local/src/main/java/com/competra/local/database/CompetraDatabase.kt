package com.competra.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.competra.local.converters.CompetitionConverters
import com.competra.local.converters.ControlPointConverters
import com.competra.local.converters.ResultConverters
import com.competra.local.converters.UserConverter
import com.competra.local.dao.OrienteeringCompetitionDao
import com.competra.local.dao.ParticipantGroupDao
import com.competra.local.dao.UserDao
import com.competra.local.dao.orienteering.OrienteeringParticipantDao
import com.competra.local.dao.orienteering.OrienteeringResultDao
import com.competra.local.dao.orienteering.DistanceDao
import com.competra.local.dao.diary.WorkoutDao
import com.competra.local.entities.orienteering.DistanceEntity
import com.competra.local.entities.orienteering.OrganizerEntity
import com.competra.local.entities.orienteering.OrienteeringCompetitionEntity
import com.competra.local.entities.orienteering.OrienteeringParticipantEntity
import com.competra.local.entities.orienteering.OrienteeringResultEntity
import com.competra.local.entities.orienteering.ParticipantGroupEntity
import com.competra.local.entities.orienteering.StageEntity
import com.competra.local.entities.diary.BikeDetailsEntity
import com.competra.local.entities.diary.RunDetailsEntity
import com.competra.local.entities.diary.SkiDetailsEntity
import com.competra.local.entities.diary.WorkoutEntity
import com.competra.local.entities.user.UserEntity

// v42: переход идентичности соревнования на единый клиентский UUID
// (competitions.id/competitionId: Long → String). Локальная схема пересоздаётся
// через fallbackToDestructiveMigration; данные подтянутся с сервера при следующем pull.
// v43: поле isTest у соревнования (тестовые скрыты из публичной ленты).
// v44: crop rect для аватара пользователя и обложки соревнования.
// v45: тренировочный дневник (workouts + run/bike/ski_details).
// v46: live GPS-трекинг — колонка trackEncoded в workouts.
// v47: формат "по выбору" (score-О) — лимит времени/штраф/порог у ParticipantGroup,
// totalScore/scorePenalty у OrienteeringResult.
// v48: опциональный клуб-организатор соревнования (organizingClubId).
// v49: номер стартового КП дистанции (startControlPoint) — для StartTimeMode.BY_START_STATION.
private const val DB_VERSION = 50

/**
 * Основной класс базы данных приложения (Room).
 */
@Database(
    entities = [
        OrienteeringCompetitionEntity::class,
        ParticipantGroupEntity::class,
        OrienteeringParticipantEntity::class,
        UserEntity::class,
        OrienteeringResultEntity::class,
        DistanceEntity::class,
        OrganizerEntity::class,
        StageEntity::class,
        WorkoutEntity::class,
        RunDetailsEntity::class,
        BikeDetailsEntity::class,
        SkiDetailsEntity::class
    ],
    version = DB_VERSION,
    exportSchema = false
)
@TypeConverters(
    CompetitionConverters::class,
    UserConverter::class,
    ControlPointConverters::class,
    ResultConverters::class
)
abstract class CompetraDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun orienteeringCompetitionDao(): OrienteeringCompetitionDao
    abstract fun participantGroupsDao(): ParticipantGroupDao
    abstract fun orienteeringParticipantDao(): OrienteeringParticipantDao
    abstract fun orienteeringResultDao(): OrienteeringResultDao
    abstract fun distanceDao(): DistanceDao
    abstract fun workoutDao(): WorkoutDao
}
