package com.competra.local.entities.orienteering

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.competra.domain.models.Gender
import com.competra.domain.models.orienteering.ControlPoint
import com.competra.local.converters.CompetitionConverters
import com.competra.local.converters.ControlPointConverters
import com.competra.local.converters.UserConverter

/**
 * Entity (сущность) для представления группы участников в соревновании по спортивному ориентированию.
 * Группа определяет параметры дистанции для определенной категории участников
 * (например, "Мужчины элита", "Женщины 16-17 лет" и т.д.).
 *
 * @property groupId Уникальный идентификатор группы (автогенерируемый)
 * @property competitionId Идентификатор соревнования, к которому относится группа (внешний ключ)
 * @property title Название группы (например, "М21", "Ж16", "Open")
 * @property distance Длина дистанции в километрах
 * @property countOfControls Количество контрольных пунктов (КП) на дистанции
 * @property timeLimitMinutes Лимит времени для формата "по выбору" (BY_CHOICE), в минутах
 * @property scorePenaltyPerMinute Штраф в очках за минуту опоздания сверх лимита (BY_CHOICE)
 * @property maxLatenessMinutes Порог сильного опоздания, после которого результат обнуляется (BY_CHOICE)
 * @property controlPoints Список контрольных пунктов с их координатами и порядком прохождения
 */
@Entity(
    tableName = "participant_groups",
    foreignKeys = [
        ForeignKey(
            entity = OrienteeringCompetitionEntity::class,
            parentColumns = ["competitionId"],
            childColumns = ["competitionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(name = "idx_groups_unsynced", value = ["isSynced"])]
)
@TypeConverters(ControlPointConverters::class, UserConverter::class)
data class ParticipantGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val groupId: Long = 0,
    val competitionId: String,
    val title: String,
    val gender: Gender?,                   // MALE, FEMALE, MIXED, null – не задано
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val distanceId: Long,                  // ссылка на дистанцию
    val maxParticipants: Int? = null,      // лимит для группы
    val timeLimitMinutes: Int? = null,         // лимит времени для формата "по выбору" (BY_CHOICE)
    val scorePenaltyPerMinute: Int? = null,    // штраф в очках за минуту опоздания сверх лимита
    val maxLatenessMinutes: Int? = null,       // порог сильного опоздания → результат обнуляется
    val teamCountedResults: Int? = null,       // своё N командного зачёта; null — как у соревнования
    // Поля синхронизации
    val remoteId: Long? = null,            // ID группы на сервере
    val isSynced: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val serverUpdatedAt: Long? = null,
    val syncError: String? = null
)

