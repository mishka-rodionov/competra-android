package com.competra.remote.repository.orienteering

import android.util.Log
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.repository.orienteering.OrienteeringCompetitionRemoteRepository
import com.competra.remote.datasource.orienteering.OrienteeringCompetitionRemoteDataSource
import com.competra.remote.request.mappers.toRequest
import com.competra.remote.request.orienteering.ParticipantGroupPublishRequest
import com.competra.remote.response.mappers.toDomain

/**
 * Реализация удаленного репозитория для соревнований по спортивному ориентированию.
 *
 * @param orienteeringCompetitionRemoteDataSource Источник данных для соревнований по спортивному ориентированию.
 */
data class OrienteeringCompetitionRemoteRepositoryImpl(
    private val orienteeringCompetitionRemoteDataSource: OrienteeringCompetitionRemoteDataSource
) : OrienteeringCompetitionRemoteRepository {

    override suspend fun createCompetition(competition: OrienteeringCompetition): Result<OrienteeringCompetition> {
        return orienteeringCompetitionRemoteDataSource.createOrienteeringCompetition(competition.toRequest())
            .mapCatching { it.result!!.toDomain() }
    }

    override suspend fun createParticipantsGroupsForCompetition(
        competitionId: String,
        participantGroups: List<ParticipantGroup>
    ): Result<List<ParticipantGroup>> {
        return orienteeringCompetitionRemoteDataSource.createCompetitionParticipantGroup(
            participantGroups.map { it.toRequest() })
            .mapCatching { it.result!!.map { gr -> gr.toDomain() } }
    }

    override suspend fun publishGroupsForCompetition(
        competitionId: String,
        participantGroups: List<ParticipantGroup>
    ): Result<List<ParticipantGroup>> {
        return orienteeringCompetitionRemoteDataSource.publishParticipantGroups(
            participantGroups.map { group ->
                ParticipantGroupPublishRequest(
                    groupId = group.remoteId,  // null для новых, Long для существующих
                    competitionId = competitionId,
                    title = group.title,
                    gender = group.gender?.name,
                    minAge = group.minAge,
                    maxAge = group.maxAge,
                    distanceId = group.distanceId,
                    maxParticipants = group.maxParticipants,
                    timeLimitMinutes = group.timeLimitMinutes,
                    scorePenaltyPerMinute = group.scorePenaltyPerMinute,
                    maxLatenessMinutes = group.maxLatenessMinutes,
                    teamCountedResults = group.teamCountedResults ?: 0
                )
            }
        ).mapCatching { response ->
            // Сопоставляем локальные группы с ответом сервера по порядку
            participantGroups.zip(response.result!!) { localGroup, serverGroup ->
                localGroup.copy(
                    remoteId = serverGroup.groupId,
                    isSynced = true,
                    lastModified = System.currentTimeMillis(),
                    serverUpdatedAt = serverGroup.updatedAt.takeIf { it > 0L }
                )
            }
        }
    }

    override suspend fun publishDistancesForCompetition(
        competitionId: String,
        distances: List<Distance>
    ): Result<List<Distance>> {
        val requests = distances.map { it.toRequest() }
        return orienteeringCompetitionRemoteDataSource.saveDistances(requests)
            .mapCatching { response ->
                // Сопоставляем локальные дистанции с серверными по порядку (zip),
                // сохраняя локальный id и проставляя remoteId из ответа
                distances.zip(response.result!!) { localDist, serverDist ->
                    localDist.copy(
                        remoteId = serverDist.id,
                        isSynced = true,
                        serverUpdatedAt = serverDist.updatedAt.takeIf { it > 0L }
                    )
                }
            }
    }

    override suspend fun getCompetitionById(competitionId: String): Result<OrienteeringCompetition> {
        return orienteeringCompetitionRemoteDataSource.getCompetitionById(competitionId)
            .mapCatching { it.result!!.toDomain() }
    }

    override suspend fun getCompetitionParticipantsGroups(competitionId: String): Result<List<ParticipantGroup>> {
        return orienteeringCompetitionRemoteDataSource.getParticipantGroups(competitionId)
            .mapCatching { it.result!!.map { gr -> gr.toDomain() } }
    }

    override suspend fun getDistancesForCompetition(
        competitionId: String
    ): Result<List<Distance>> {
        return orienteeringCompetitionRemoteDataSource.getDistances(competitionId)
            .mapCatching { it.result!!.map { dist -> dist.toDomain(competitionId) } }
    }

    override suspend fun updateCompetition(competition: OrienteeringCompetition): Result<OrienteeringCompetition> {
        TODO("Not yet implemented")
    }

    override suspend fun updateCompetitionParticipantsGroups(
        competitionId: String,
        participantGroups: List<ParticipantGroup>
    ): Result<List<ParticipantGroup>> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteCompetition(competitionId: String): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteCompetitionParticipantsGroups(competitionId: String): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun getCompetitionsByUserid(): Result<List<OrienteeringCompetition>> {
        return orienteeringCompetitionRemoteDataSource.getCompetitionsByUserid()
            .mapCatching { it.result!!.map { comp -> comp.toDomain() } }
    }

    override suspend fun getRegisteredCompetitions(): Result<List<OrienteeringCompetition>> {
        return orienteeringCompetitionRemoteDataSource.getRegisteredCompetitions()
            .mapCatching { it.result!!.map { comp -> comp.toDomain() } }
    }

    override suspend fun saveParticipant(participant: OrienteeringParticipant): Result<OrienteeringParticipant> {
        return orienteeringCompetitionRemoteDataSource.saveParticipant(participant.toRequest())
            .mapCatching { it.result!!.toDomain() }
    }

    override suspend fun saveParticipants(participants: List<OrienteeringParticipant>): Result<List<OrienteeringParticipant>> {
        return orienteeringCompetitionRemoteDataSource
            .saveParticipants(participants.map { it.toRequest() })
            .mapCatching { it.result!!.map { r -> r.toDomain() } }
    }

    override suspend fun getParticipantsForCompetition(competitionId: String): Result<List<OrienteeringParticipant>> {
        return orienteeringCompetitionRemoteDataSource.getParticipantsByCompetition(competitionId)
            .mapCatching {
                it.result!!
                    .map { p -> p.toDomain() }
            }
            .onFailure {
                Log.d("LOG_TAG", "getParticipantsForCompetition: ${it.message}")
            }
    }

    override suspend fun saveResult(result: OrienteeringResult): Result<OrienteeringResult> {
        return orienteeringCompetitionRemoteDataSource.saveResult(result.toRequest())
            .mapCatching { it.result!!.toDomain() }
    }

    override suspend fun getResultsByCompetition(competitionId: String): Result<List<OrienteeringResult>> {
        return orienteeringCompetitionRemoteDataSource.getResultsByCompetition(competitionId)
            .mapCatching { it.result!!.map { r -> r.toDomain() } }
    }

    override suspend fun deleteCompetitionRemotely(competitionId: String): Result<Unit> =
        orienteeringCompetitionRemoteDataSource.deleteCompetition(competitionId).mapCatching { }

    override suspend fun deleteGroupRemotely(remoteGroupId: Long): Result<Unit> =
        orienteeringCompetitionRemoteDataSource.deleteParticipantGroup(remoteGroupId).mapCatching { }

    override suspend fun deleteDistanceRemotely(remoteDistanceId: Long): Result<Unit> =
        orienteeringCompetitionRemoteDataSource.deleteDistance(remoteDistanceId).mapCatching { }

    override suspend fun deleteParticipantRemotely(participantId: String): Result<Unit> =
        orienteeringCompetitionRemoteDataSource.deleteParticipant(participantId).mapCatching { }

    override suspend fun deleteResultRemotely(resultId: String): Result<Unit> =
        orienteeringCompetitionRemoteDataSource.deleteResult(resultId).mapCatching { }
}
