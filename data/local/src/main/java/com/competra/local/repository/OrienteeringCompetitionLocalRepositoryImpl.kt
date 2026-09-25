package com.competra.local.repository

import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.GroupWithParticipantsAndResults
import com.competra.domain.models.orienteering.OrienteeringCompetitionDetails
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.repository.orienteering.OrienteeringCompetitionLocalRepository
import com.competra.domain.sync.planServerMerge
import com.competra.local.dao.OrienteeringCompetitionDao
import com.competra.local.dao.ParticipantGroupDao
import com.competra.local.dao.orienteering.OrienteeringParticipantDao
import com.competra.local.dao.orienteering.OrienteeringResultDao
import com.competra.local.dao.orienteering.DistanceDao
import com.competra.local.mappers.toDomain
import com.competra.local.mappers.toEntity

/**
 * Реализация локального репозитория для работы с соревнованиями по спортивному ориентированию.
 *
 * Все мутирующие методы принимают `markUnsynced: Boolean`. Если true (дефолт) — это вызов из
 * UI/Interactor, и перед записью в БД проставляются isSynced=false, lastModified=now,
 * syncError=null. Если false — это запись из SyncWorker или server→local pull, и значения
 * передаются как есть.
 */
class OrienteeringCompetitionLocalRepositoryImpl(
    private val orienteeringCompetitionDao: OrienteeringCompetitionDao,
    private val participantGroupDao: ParticipantGroupDao,
    private val participantDao: OrienteeringParticipantDao,
    private val orienteeringResultDao: OrienteeringResultDao,
    private val distanceDao: DistanceDao
) : OrienteeringCompetitionLocalRepository {

    private fun OrienteeringCompetition.applyUnsynced(): OrienteeringCompetition =
        copy(
            competition = competition.copy(
                isSynced = false,
                lastModified = System.currentTimeMillis(),
                syncError = null
            )
        )

    private fun ParticipantGroup.applyUnsynced(): ParticipantGroup =
        copy(isSynced = false, lastModified = System.currentTimeMillis(), syncError = null)

    private fun OrienteeringParticipant.applyUnsynced(): OrienteeringParticipant =
        copy(isSynced = false, lastModified = System.currentTimeMillis(), syncError = null)

    private fun OrienteeringResult.applyUnsynced(): OrienteeringResult =
        copy(isSynced = false, lastModified = System.currentTimeMillis(), syncError = null)

    private fun Distance.applyUnsynced(): Distance =
        copy(isSynced = false, lastModified = System.currentTimeMillis(), syncError = null)

    override suspend fun saveCompetition(
        orienteeringCompetition: OrienteeringCompetition,
        markUnsynced: Boolean
    ): Result<OrienteeringCompetition> {
        return runCatching {
            val toSave = if (markUnsynced) orienteeringCompetition.applyUnsynced() else orienteeringCompetition
            orienteeringCompetitionDao.insert(toSave.toEntity())
            val savedEntity = orienteeringCompetitionDao.getCompetitionById(toSave.competitionId)
                ?: throw IllegalStateException("Failed to fetch saved competition with id = ${toSave.competitionId}")
            savedEntity.toDomain()
        }
    }

    override suspend fun saveCompetitions(
        orienteeringCompetition: List<OrienteeringCompetition>,
        markUnsynced: Boolean
    ): Result<List<OrienteeringCompetition>> {
        return runCatching {

            if (orienteeringCompetition.isEmpty()) return@runCatching emptyList()

            val prepared = if (markUnsynced) orienteeringCompetition.map { it.applyUnsynced() } else orienteeringCompetition

            // 1. Вставляем (IGNORE — существующие записи не перезаписываются,
            //    чтобы избежать CASCADE DELETE дочерних distances и participant_groups)
            orienteeringCompetitionDao.insertAll(prepared.map { it.toEntity() })

            // 2. Возвращаем актуальные сохранённые записи по их UUID
            prepared.mapNotNull { orienteeringCompetitionDao.getCompetitionById(it.competitionId)?.toDomain() }
        }
    }

    override suspend fun updateCompetition(
        orienteeringCompetition: OrienteeringCompetition,
        markUnsynced: Boolean
    ): Result<Any> {
        return runCatching {
            // Защита от затирания sync-метаданных: если из UI пришла модель без serverUpdatedAt
            // (собрана из in-memory state мастера создания), но в БД оно уже проставлено
            // SyncWorker'ом — сохраняем его, чтобы conflict-detection на сервере не ломался.
            // Идентичность (competitionId) клиент знает с момента создания, переводить local↔remote
            // больше не нужно.
            val merged = if (markUnsynced) {
                val existing = orienteeringCompetitionDao
                    .getCompetitionById(orienteeringCompetition.competitionId)
                    ?.toDomain()
                val preservedServerUpdatedAt =
                    orienteeringCompetition.competition.serverUpdatedAt
                        ?: existing?.competition?.serverUpdatedAt
                orienteeringCompetition.copy(
                    competition = orienteeringCompetition.competition.copy(
                        serverUpdatedAt = preservedServerUpdatedAt
                    )
                ).applyUnsynced()
            } else {
                orienteeringCompetition
            }
            orienteeringCompetitionDao.update(merged.toEntity())
        }
    }

    override suspend fun getCompetitionWithDetails(competitionId: String): Result<OrienteeringCompetitionDetails> {
        return runCatching {
            orienteeringCompetitionDao.getCompetitionWithDetails(competitionId).toDomain()
        }
    }

    override suspend fun getCompetitionsByUserid(userId: String): Result<List<OrienteeringCompetition>> {
        return runCatching {

            orienteeringCompetitionDao
                .getCompetitionsByUserId(userId)
                .map { it.toDomain() }
        }
    }

    override suspend fun getCompetition(competitionId: String): Result<OrienteeringCompetition?> {
        return runCatching {
            orienteeringCompetitionDao.getCompetitionById(competitionId)?.toDomain()
        }
    }

    override suspend fun saveParticipantsGroups(
        participantGroups: List<ParticipantGroup>,
        markUnsynced: Boolean
    ): Result<Any> {
        return runCatching {
            val prepared = if (markUnsynced) participantGroups.map { it.applyUnsynced() } else participantGroups
            participantGroupDao.insertAll(prepared.map(ParticipantGroup::toEntity))
        }
    }

    override suspend fun updateParticipantsGroups(
        competitionId: String,
        participantGroups: List<ParticipantGroup>,
        markUnsynced: Boolean
    ): Result<Any> {
        return runCatching {
            val existingGroups = participantGroupDao.getGroupsForCompetition(competitionId)
            val existingByRemoteId = existingGroups
                .filter { it.remoteId != null }
                .associateBy { it.remoteId!! }
            val existingByGroupId = existingGroups.associateBy { it.groupId }

            val incomingRemoteIds = participantGroups.mapNotNull { it.remoteId }.toSet()
            existingGroups
                .filter { it.remoteId != null && it.remoteId !in incomingRemoteIds }
                .forEach { participantGroupDao.delete(it) }

            val prepared = if (markUnsynced) participantGroups.map { it.applyUnsynced() } else participantGroups
            prepared.forEach { group ->
                val entity = group.toEntity().copy(competitionId = competitionId)
                val existing = entity.remoteId?.let { existingByRemoteId[it] }
                    ?: existingByGroupId[entity.groupId].takeIf { entity.groupId != 0L }
                if (existing != null) {
                    participantGroupDao.updateParticipantGroup(entity.copy(groupId = existing.groupId))
                } else {
                    participantGroupDao.insert(entity)
                }
            }
        }
    }

    override suspend fun mergeParticipantGroupsFromServer(
        competitionId: String,
        serverGroups: List<ParticipantGroup>
    ): Result<Any> {
        return runCatching {
            // Включает и помеченные на удаление (isDeleted) — иначе их «воскресит» вставка с сервера
            val existingGroups = participantGroupDao.getGroupsForCompetition(competitionId)
            val plan = planServerMerge(
                local = existingGroups,
                server = serverGroups.filter { it.remoteId != null },
                localKey = { it.remoteId },
                serverKey = { it.remoteId!! },
                isLocalSynced = { it.isSynced },
                isLocalOnServer = { it.remoteId != null }
            )

            plan.toDelete.forEach { participantGroupDao.delete(it) }
            plan.toUpdate.forEach { (local, server) ->
                participantGroupDao.updateParticipantGroup(
                    server.toEntity().copy(competitionId = competitionId, groupId = local.groupId)
                )
            }
            plan.toInsert.forEach { server ->
                participantGroupDao.insert(server.toEntity().copy(competitionId = competitionId))
            }
        }
    }

    override suspend fun updateParticipantGroup(
        participantGroup: ParticipantGroup,
        markUnsynced: Boolean
    ): Result<Any> {
        return runCatching {
            val merged = if (markUnsynced) {
                val existing = if (participantGroup.groupId != 0L) {
                    participantGroupDao.getCertainParticipantGroup(participantGroup.groupId).toDomain()
                } else null
                participantGroup.copy(
                    remoteId = participantGroup.remoteId ?: existing?.remoteId,
                    serverUpdatedAt = participantGroup.serverUpdatedAt ?: existing?.serverUpdatedAt
                ).applyUnsynced()
            } else {
                participantGroup
            }
            participantGroupDao.updateParticipantGroup(merged.toEntity())
        }
    }

    override suspend fun saveParticipant(
        participant: OrienteeringParticipant,
        markUnsynced: Boolean
    ): Result<OrienteeringParticipant?> {
        return runCatching {
            val toSave = if (markUnsynced) participant.applyUnsynced() else participant
            participantDao.insertParticipant(toSave.toEntity())
            toSave
        }
    }

    override suspend fun getParticipants(competitionId: String): Result<List<OrienteeringParticipant>> {
        return runCatching {
            participantDao.getAllParticipants(competitionId).map { it.toDomain() }
        }
    }

    override suspend fun updateParticipants(
        participants: List<OrienteeringParticipant>,
        markUnsynced: Boolean
    ): Result<Any> {
        return runCatching {
            val prepared = if (markUnsynced) participants.map { it.applyUnsynced() } else participants
            participantDao.updateAll(prepared.map { it.toEntity() })
        }
    }

    override suspend fun deleteParticipant(participantId: String): Result<Unit> {
        return runCatching { participantDao.deleteParticipantById(participantId) }
    }

    override suspend fun deleteCompetition(competitionId: String): Result<Unit> {
        return runCatching {
            orienteeringResultDao.deleteResultsByCompetitionId(competitionId)
            participantDao.deleteParticipantsByCompetitionId(competitionId)
            participantGroupDao.deleteGroupsForCompetition(competitionId)
            distanceDao.deleteDistancesByCompetitionId(competitionId)
            orienteeringCompetitionDao.deleteById(competitionId)
        }
    }

    override suspend fun markCompetitionDeleted(competitionId: String): Result<Unit> {
        return runCatching { orienteeringCompetitionDao.markDeleted(competitionId) }
    }

    override suspend fun getParticipantByChipNumber(
        competitionId: String,
        chipNumber: Int
    ): Result<OrienteeringParticipant> {
        return runCatching {
            participantDao.getParticipantByChipNumber(
                competitionId = competitionId,
                chipNumber = chipNumber
            ).toDomain()
        }
    }

    override suspend fun getParticipantGroup(groupId: Long): Result<ParticipantGroup> {
        return runCatching {
            participantGroupDao.getCertainParticipantGroup(groupId).toDomain()
        }
    }

    override suspend fun saveParticipantResult(
        orienteeringResult: OrienteeringResult,
        markUnsynced: Boolean
    ): Result<Any> {
        return runCatching {
            val toSave = if (markUnsynced) orienteeringResult.applyUnsynced() else orienteeringResult
            orienteeringResultDao.insertResult(toSave.toEntity())
        }
    }

    override suspend fun getResultByParticipant(participantId: String): Result<OrienteeringResult?> {
        return runCatching {
            orienteeringResultDao.getResultForParticipant(participantId)?.toDomain()
        }
    }

    override suspend fun getResultForGroup(
        competitionId: String,
        groupId: Long
    ): Result<List<OrienteeringResult>> {
        return runCatching {
            orienteeringResultDao.getResultsForCompetitionGroupDirect(competitionId = competitionId, groupId = groupId).map { it.toDomain() }
        }
    }

    override suspend fun updateResults(
        orienteeringResult: List<OrienteeringResult>,
        markUnsynced: Boolean
    ): Result<Any> {
        return runCatching {
            val prepared = if (markUnsynced) orienteeringResult.map { it.applyUnsynced() } else orienteeringResult
            orienteeringResultDao.updateResults(prepared.map { it.toEntity() })
        }
    }

    override suspend fun saveResults(
        orienteeringResult: List<OrienteeringResult>,
        markUnsynced: Boolean
    ): Result<Any> {
        return runCatching {
            val prepared = if (markUnsynced) orienteeringResult.map { it.applyUnsynced() } else orienteeringResult
            orienteeringResultDao.insertResults(prepared.map { it.toEntity() })
        }
    }

    override suspend fun getResultByGroups(competitionId: String): Result<List<GroupWithParticipantsAndResults>> {
        return runCatching {
            orienteeringResultDao.getProtocolByCompetition(competitionId).map { it.toDomain() }
        }
    }

    override suspend fun updateIsEditableForCompetition(
        competitionId: String,
        isEditable: Boolean
    ): Result<Any> {
        return runCatching {
            orienteeringResultDao.updateIsEditableForCompetition(competitionId, isEditable)
        }
    }

    override suspend fun saveDistance(distance: Distance, markUnsynced: Boolean): Result<Long> {
        return runCatching {
            val toSave = if (markUnsynced) distance.applyUnsynced() else distance
            distanceDao.insertDistance(toSave.toEntity())
        }
    }

    override suspend fun getDistanceById(distanceId: Long): Result<Distance?> {
        return runCatching {
            distanceDao.getDistanceById(distanceId)?.toDomain()
        }
    }

    override suspend fun getDistances(competitionId: String): Result<List<Distance>> {
        return runCatching {
            distanceDao.getDistancesForCompetition(competitionId).map { it.toDomain() }
        }
    }

    override suspend fun updateDistance(distance: Distance, markUnsynced: Boolean): Result<Any> {
        return runCatching {
            val merged = if (markUnsynced) {
                val existing = distanceDao.getDistanceById(distance.id)?.toDomain()
                distance.copy(
                    remoteId = distance.remoteId ?: existing?.remoteId,
                    serverUpdatedAt = distance.serverUpdatedAt ?: existing?.serverUpdatedAt,
                    // Карта на Android только читается с сервера — форма редактирования её не знает
                    // и присылает null; без подстановки локальная правка стирала бы карту.
                    map = distance.map ?: existing?.map
                ).applyUnsynced()
            } else {
                distance
            }
            distanceDao.updateDistance(merged.toEntity())
        }
    }

    override suspend fun getUnsyncedCompetitions(): List<OrienteeringCompetition> =
        orienteeringCompetitionDao.getUnsynced().map { it.toDomain() }

    override suspend fun getCompetitionsMarkedForDeletion(): List<OrienteeringCompetition> =
        orienteeringCompetitionDao.getMarkedForDeletion().map { it.toDomain() }

    override suspend fun getUnsyncedGroups(): List<ParticipantGroup> =
        participantGroupDao.getUnsynced().map { it.toDomain() }

    override suspend fun getGroupsMarkedForDeletion(): List<ParticipantGroup> =
        participantGroupDao.getMarkedForDeletion().map { it.toDomain() }

    override suspend fun getUnsyncedDistances(): List<Distance> =
        distanceDao.getUnsynced().map { it.toDomain() }

    override suspend fun getDistancesMarkedForDeletion(): List<Distance> =
        distanceDao.getMarkedForDeletion().map { it.toDomain() }

    override suspend fun getUnsyncedParticipants(): List<OrienteeringParticipant> =
        participantDao.getUnsynced().map { it.toDomain() }

    override suspend fun getParticipantsMarkedForDeletion(): List<OrienteeringParticipant> =
        participantDao.getMarkedForDeletion().map { it.toDomain() }

    override suspend fun getUnsyncedResults(): List<OrienteeringResult> =
        orienteeringResultDao.getUnsynced().map { it.toDomain() }

    override suspend fun getResultsMarkedForDeletion(): List<OrienteeringResult> =
        orienteeringResultDao.getMarkedForDeletion().map { it.toDomain() }

    override suspend fun purgeGroupLocally(groupId: Long) {
        val group = participantGroupDao.getCertainParticipantGroup(groupId)
        participantGroupDao.delete(group)
    }

    override suspend fun purgeDistanceLocally(distanceId: Long) {
        distanceDao.deleteDistance(distanceId)
    }

    override suspend fun purgeResultLocally(resultId: Long) {
        orienteeringResultDao.deleteResultById(resultId)
    }
}
