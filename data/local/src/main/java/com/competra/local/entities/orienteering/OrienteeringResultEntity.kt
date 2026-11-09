package com.competra.local.entities.orienteering


import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.SplitTime
import com.competra.local.converters.ResultConverters

/**
 * Entity (сущность) для представления результатов участника в соревновании по спортивному ориентированию.
 * Хранит информацию о времени старта, финиша, итоговом времени, месте, штрафах и сплитах.
 *
 * @param id Уникальный идентификатор результата (автогенерируемый)
 * @param competitionId Идентификатор соревнования (внешний ключ)
 * @param groupId Идентификатор группы (внешний ключ)
 * @param participantId Идентификатор участника (внешний ключ)
 * @param startTime Время старта в миллисекундах (Unix timestamp)
 * @param finishTime Время финиша в миллисекундах (Unix timestamp)
 * @param totalTime Общее время прохождения дистанции в миллисекундах
 * @param rank Занятое место в соревновании
 * @param status Статус результата (например, финишировал, сошёл, не стартовал)
 * @param penaltyTime Штрафное время в миллисекундах
 * @param splits Список отметок на контрольных пунктах (сплитов)
 * @param isEditable Флаг возможности редактирования результата
 * @param isEdited Флаг того, что результат был изменен вручную
 * @param isStatusManual Статус выставлен организатором вручную
 * @param readAt Время считывания чипа на финише (мс)
 */
@Entity(
    tableName = "orienteering_results",
    foreignKeys = [
        ForeignKey(
            entity = OrienteeringCompetitionEntity::class,
            parentColumns = ["competitionId"],
            childColumns = ["competitionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = OrienteeringParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["participantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["competitionId"]),
        Index(value = ["participantId"]),
        Index(name = "idx_results_unsynced", value = ["isSynced"])
    ]
)
@TypeConverters(ResultConverters::class)
data class OrienteeringResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val competitionId: String,
    val groupId: Long,
    val participantId: String,
    val startTime: Long? = null,
    val finishTime: Long? = null,
    val totalTime: Long? = null,
    @Deprecated("Использовать rank")
    val rank: Int? = null,
    val status: ResultStatus,
    val penaltyTime: Long = 0,
    val totalScore: Int? = null,
    val scorePenalty: Int = 0,
    val splits: List<SplitTime>? = null,
    val isEditable: Boolean = true,
    val isEdited: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isStatusManual: Boolean = false,
    val readAt: Long? = null,
    val isSynced: Boolean = false,
    val remoteId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val lastModified: Long = System.currentTimeMillis(),
    val serverUpdatedAt: Long? = null,
    val syncError: String? = null
)
