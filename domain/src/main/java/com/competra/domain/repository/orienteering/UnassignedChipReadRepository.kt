package com.competra.domain.repository.orienteering

import com.competra.domain.models.orienteering.UnassignedChipRead
import kotlinx.coroutines.flow.Flow

/** Локальная очередь неразобранных считываний режима «Регистрация на финише». */
interface UnassignedChipReadRepository {

    /** Неразобранные считывания соревнования, свежие сверху. */
    fun observe(competitionId: String): Flow<List<UnassignedChipRead>>

    /** Число неразобранных считываний соревнования. */
    suspend fun count(competitionId: String): Int

    /** Считывание чипа [chipNumber] или `null`. */
    suspend fun get(competitionId: String, chipNumber: Int): UnassignedChipRead?

    /** Сохраняет считывание; повторное считывание того же чипа заменяет запись. */
    suspend fun save(read: UnassignedChipRead)

    /** Удаляет считывание (после привязки к участнику или как ошибочное). */
    suspend fun delete(competitionId: String, chipNumber: Int)
}
