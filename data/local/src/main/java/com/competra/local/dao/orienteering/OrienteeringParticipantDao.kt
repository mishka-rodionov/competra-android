package com.competra.local.dao.orienteering

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.competra.local.entities.orienteering.OrienteeringParticipantEntity

@Dao
interface OrienteeringParticipantDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<OrienteeringParticipantEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipant(participant: OrienteeringParticipantEntity): Long

    @Update
    suspend fun updateParticipant(participant: OrienteeringParticipantEntity)

    @Update
    suspend fun updateAll(participants: List<OrienteeringParticipantEntity>)

    @Query("SELECT * FROM orienteering_participants WHERE id = :id")
    suspend fun getParticipantById(id: String): OrienteeringParticipantEntity?

    @Query("SELECT * FROM orienteering_participants WHERE groupId = :groupId AND isDeleted = 0")
    suspend fun getParticipantsByGroupId(groupId: Long): List<OrienteeringParticipantEntity>

    /** Участники соревнования без помеченных на удаление (soft-delete). */
    @Query("SELECT * FROM orienteering_participants WHERE competitionId = :competitionId AND isDeleted = 0")
    suspend fun getAllParticipants(competitionId: String): List<OrienteeringParticipantEntity>

    /**
     * Все участники соревнования, включая помеченных на удаление. Нужно для слияния с серверным
     * снимком: иначе помеченный участник попал бы во вставку и «воскрес».
     */
    @Query("SELECT * FROM orienteering_participants WHERE competitionId = :competitionId")
    suspend fun getAllParticipantsIncludingDeleted(competitionId: String): List<OrienteeringParticipantEntity>

    @Query("SELECT * FROM orienteering_participants WHERE competitionId = :competitionId AND chipNumber = :chipNumber AND isDeleted = 0")
    suspend fun getParticipantByChipNumber(competitionId: String, chipNumber: Int): OrienteeringParticipantEntity

    @Query("DELETE FROM orienteering_participants WHERE id = :id")
    suspend fun deleteParticipantById(id: String)

    /**
     * Помечает участника на удаление (soft-delete): isDeleted=1, isSynced=0.
     * SyncCenterWorker подхватит запись через [getMarkedForDeletion], отправит DELETE
     * на сервер и физически удалит локально.
     */
    @Query("UPDATE orienteering_participants SET isDeleted = 1, isSynced = 0, syncError = NULL, lastModified = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM orienteering_participants WHERE competitionId = :competitionId")
    suspend fun deleteParticipantsByCompetitionId(competitionId: String)

    @Query("SELECT * FROM orienteering_participants WHERE isSynced = 0 AND isDeleted = 0")
    suspend fun getUnsynced(): List<OrienteeringParticipantEntity>

    @Query("SELECT * FROM orienteering_participants WHERE isSynced = 0 AND isDeleted = 1")
    suspend fun getMarkedForDeletion(): List<OrienteeringParticipantEntity>
}
