package com.competra.local.entities.orienteering

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сущность участника соревнований по ориентированию для базы данных Room.
 * 
 * @property isChipGiven Флаг выдачи чипа.
 * @property isOutOfCompetition Участник вне конкурса (ВК) — без места, вне командного зачёта и рейтинга.
 */
@Entity(
    tableName = "orienteering_participants",
    foreignKeys = [
        ForeignKey(
            entity = ParticipantGroupEntity::class,
            parentColumns = ["groupId"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(name = "idx_participants_unsynced", value = ["isSynced"])]
)
data class OrienteeringParticipantEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val firstName: String,
    val lastName: String,
    val groupId: Long,
    val groupName : String,
    val competitionId: String,
    val commandName: String,
    val startNumber: String,
    val startTime: Long,
    val chipNumber: String,
    val comment: String,
    val isChipGiven: Boolean,
    @ColumnInfo(defaultValue = "0")
    val isOutOfCompetition: Boolean = false,
    val isSynced: Boolean = false,
    val remoteId: String? = null,   // ID участника на сервере
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val lastModified: Long = System.currentTimeMillis(),
    val serverUpdatedAt: Long? = null,
    val syncError: String? = null
)
