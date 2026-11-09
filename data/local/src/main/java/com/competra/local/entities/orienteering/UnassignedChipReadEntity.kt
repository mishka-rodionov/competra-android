package com.competra.local.entities.orienteering

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.TypeConverters
import com.competra.domain.models.orienteering.SplitTime
import com.competra.local.converters.ResultConverters

/**
 * Неразобранное считывание чипа (режим «Регистрация на финише»); на сервер не синхронизируется.
 * См. [com.competra.domain.models.orienteering.UnassignedChipRead].
 */
@Entity(
    tableName = "unassigned_chip_reads",
    primaryKeys = ["competitionId", "chipNumber"],
    foreignKeys = [
        ForeignKey(
            entity = OrienteeringCompetitionEntity::class,
            parentColumns = ["competitionId"],
            childColumns = ["competitionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(ResultConverters::class)
data class UnassignedChipReadEntity(
    val competitionId: String,
    val chipNumber: Int,
    val readAt: Long,
    val clearTime: Long,
    val splits: List<SplitTime>,
)
