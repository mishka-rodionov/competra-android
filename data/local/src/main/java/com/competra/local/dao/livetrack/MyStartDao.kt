package com.competra.local.dao.livetrack

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.competra.local.entities.livetrack.MyStartEntity
import kotlinx.coroutines.flow.Flow

/** DAO кэша соревнований, на которые зарегистрирован пользователь. */
@Dao
interface MyStartDao {

    @Query("SELECT * FROM my_starts ORDER BY startDate")
    fun observeAll(): Flow<List<MyStartEntity>>

    @Query("SELECT * FROM my_starts WHERE competitionId = :competitionId")
    suspend fun get(competitionId: String): MyStartEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MyStartEntity>)

    @Query("DELETE FROM my_starts")
    suspend fun deleteAll()

    /** Заменяет кэш свежим списком с сервера (отменённые регистрации исчезают). */
    @Transaction
    suspend fun replaceAll(items: List<MyStartEntity>) {
        deleteAll()
        insertAll(items)
    }
}
