package com.competra.local.entities.orienteering

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.competra.domain.models.Competition
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.OvertimePolicy
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.local.converters.CompetitionConverters

/**
 * Сущность соревнования по ориентированию для базы данных Room.
 * 
 * @property competitionId Глобально-уникальный клиентский UUID соревнования (PK, единый id на всех платформах)
 * @property competition Базовая информация о соревновании
 * @property direction Направление
 * @property punchingSystem Система отметки
 * @property startTimeMode Режим начала соревнования
 * @property countdownTimer Время отсчета перед стартом (в минутах)
 * @property startTime Фактическое время начала соревнования (timestamp)
 * @property controlTimeMinutes Контрольное время соревнования в минутах (умолчание для групп)
 * @property overtimePolicy Что делать с результатами, превысившими КВ
 */
@Entity(
    tableName = "orienteering_competitions",
    indices = [Index(name = "idx_competitions_unsynced", value = ["isSynced"])]
)
@TypeConverters(CompetitionConverters::class)
data class OrienteeringCompetitionEntity(
    @PrimaryKey
    val competitionId: String,
    @Embedded
    val competition: Competition,
    val direction: OrienteeringDirection,
    val punchingSystem: PunchingSystem,
    val startTimeMode: StartTimeMode,
    val countdownTimer: Int? = null,
    val startTime: Long? = null,
    val startIntervalSeconds: Int? = null,
    val controlTimeMinutes: Int? = null,
    @ColumnInfo(defaultValue = "IGNORE")
    val overtimePolicy: OvertimePolicy = OvertimePolicy.DEFAULT,
    @ColumnInfo(defaultValue = "0")
    val isDrawConducted: Boolean = false,
    // Хранит orient.updatedAt (таблица OrienteeringCompetitions), а НЕ comp.updatedAt.
    // Сервер при conflict-check сравнивает именно с orient.updatedAt: comp.updatedAt
    // обновляется автоматически планировщиком статусов и не отражает изменений ориентирования.
    @ColumnInfo(name = "orient_server_updated_at")
    val serverUpdatedAt: Long? = null
)
