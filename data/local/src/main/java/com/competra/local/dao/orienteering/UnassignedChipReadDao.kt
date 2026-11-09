package com.competra.local.dao.orienteering

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.competra.local.entities.orienteering.UnassignedChipReadEntity
import kotlinx.coroutines.flow.Flow

/** DAO очереди неразобранных считываний (режим «Регистрация на финише»). */
@Dao
interface UnassignedChipReadDao {

    @Query("SELECT * FROM unassigned_chip_reads WHERE competitionId = :competitionId ORDER BY readAt DESC")
    fun observe(competitionId: String): Flow<List<UnassignedChipReadEntity>>

    @Query("SELECT COUNT(*) FROM unassigned_chip_reads WHERE competitionId = :competitionId")
    suspend fun count(competitionId: String): Int

    @Query("SELECT * FROM unassigned_chip_reads WHERE competitionId = :competitionId AND chipNumber = :chipNumber")
    suspend fun get(competitionId: String, chipNumber: Int): UnassignedChipReadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UnassignedChipReadEntity)

    @Query("DELETE FROM unassigned_chip_reads WHERE competitionId = :competitionId AND chipNumber = :chipNumber")
    suspend fun delete(competitionId: String, chipNumber: Int)
}
