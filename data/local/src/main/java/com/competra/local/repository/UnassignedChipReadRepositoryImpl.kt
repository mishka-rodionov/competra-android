package com.competra.local.repository

import com.competra.domain.models.orienteering.UnassignedChipRead
import com.competra.domain.repository.orienteering.UnassignedChipReadRepository
import com.competra.local.dao.orienteering.UnassignedChipReadDao
import com.competra.local.entities.orienteering.UnassignedChipReadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [UnassignedChipReadRepository] поверх Room. */
class UnassignedChipReadRepositoryImpl(
    private val dao: UnassignedChipReadDao,
) : UnassignedChipReadRepository {

    override fun observe(competitionId: String): Flow<List<UnassignedChipRead>> =
        dao.observe(competitionId).map { list -> list.map { it.toDomain() } }

    override suspend fun count(competitionId: String): Int = dao.count(competitionId)

    override suspend fun get(competitionId: String, chipNumber: Int): UnassignedChipRead? =
        dao.get(competitionId, chipNumber)?.toDomain()

    override suspend fun save(read: UnassignedChipRead) = dao.upsert(read.toEntity())

    override suspend fun delete(competitionId: String, chipNumber: Int) = dao.delete(competitionId, chipNumber)
}

private fun UnassignedChipReadEntity.toDomain() = UnassignedChipRead(
    competitionId = competitionId,
    chipNumber = chipNumber,
    readAt = readAt,
    clearTime = clearTime,
    splits = splits,
)

private fun UnassignedChipRead.toEntity() = UnassignedChipReadEntity(
    competitionId = competitionId,
    chipNumber = chipNumber,
    readAt = readAt,
    clearTime = clearTime,
    splits = splits,
)
