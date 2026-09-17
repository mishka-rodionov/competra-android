package com.competra.local.entities.orienteering

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.competra.domain.models.Competition
import com.competra.domain.models.orienteering.ControlPoint
import com.competra.local.converters.ControlPointConverters

@Entity(
    tableName = "distances",
    foreignKeys = [
        ForeignKey(
            entity = OrienteeringCompetitionEntity::class,
            parentColumns = ["competitionId"],
            childColumns = ["competitionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("competitionId"),
        Index(name = "idx_distances_unsynced", value = ["isSynced"])
    ]
)
@TypeConverters(ControlPointConverters::class)
data class DistanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val remoteId: Long? = null,
    val competitionId: String,
    val name: String? = null,               // например, "Длинная дистанция"
    val lengthMeters: Int,                  // длина в метрах
    val climbMeters: Int,                   // набор высоты
    val controlsCount: Int,                 // количество КП
    val description: String? = null,
    // Поля синхронизации
    val isSynced: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val serverUpdatedAt: Long? = null,
    val syncError: String? = null,
    val controlPoints: List<ControlPoint> = emptyList(),
    val finishControlPoint: Int? = null,
    val startControlPoint: Int? = null
)
