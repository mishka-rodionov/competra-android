package com.competra.domain.repository.orienteering

import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.PastParticipation

interface OrienteeringCompetitionRemoteRepository {

    suspend fun createCompetition(competition: OrienteeringCompetition): Result<OrienteeringCompetition>

    suspend fun createParticipantsGroupsForCompetition(competitionId: String, participantGroups: List<ParticipantGroup>): Result<List<ParticipantGroup>>

    suspend fun publishGroupsForCompetition(competitionId: String, participantGroups: List<ParticipantGroup>): Result<List<ParticipantGroup>>

    suspend fun getCompetitionById(competitionId: String): Result<OrienteeringCompetition>

    suspend fun getCompetitionParticipantsGroups(competitionId: String): Result<List<ParticipantGroup>>

    suspend fun updateCompetition(competition: OrienteeringCompetition): Result<OrienteeringCompetition>

    suspend fun updateCompetitionParticipantsGroups(competitionId: String, participantGroups: List<ParticipantGroup>): Result<List<ParticipantGroup>>

    suspend fun deleteCompetition(competitionId: String): Result<Unit>

    suspend fun deleteCompetitionParticipantsGroups(competitionId: String): Result<Unit>

    suspend fun getCompetitionsByUserid(): Result<List<OrienteeringCompetition>>

    /**
     * Соревнования, на которые текущий пользователь зарегистрирован как участник.
     */
    suspend fun getRegisteredCompetitions(): Result<List<OrienteeringCompetition>>

    suspend fun publishDistancesForCompetition(
        competitionId: String,
        distances: List<Distance>
    ): Result<List<Distance>>

    suspend fun getDistancesForCompetition(
        competitionId: String
    ): Result<List<Distance>>

    suspend fun saveParticipant(participant: OrienteeringParticipant): Result<OrienteeringParticipant>

    suspend fun saveParticipants(participants: List<OrienteeringParticipant>): Result<List<OrienteeringParticipant>>

    suspend fun getParticipantsForCompetition(competitionId: String): Result<List<OrienteeringParticipant>>

    /**
     * Участия во всех соревнованиях текущего пользователя-организатора на сервере, кроме
     * [excludeCompetitionId] и тестовых. Источник подсказок [com.competra.domain.models.orienteering.ParticipantHistory].
     */
    suspend fun getParticipantHistory(excludeCompetitionId: String): Result<List<PastParticipation>>

    suspend fun saveResult(result: OrienteeringResult): Result<OrienteeringResult>

    suspend fun getResultsByCompetition(competitionId: String): Result<List<OrienteeringResult>>

    /**
     * Удаление сущностей на сервере (физическое). Используется SyncCenterWorker
     * после soft-delete на клиенте: запись помечена isDeleted=true,
     * Worker отправляет DELETE и при успехе физически удаляет локальную запись.
     */
    suspend fun deleteCompetitionRemotely(competitionId: String): Result<Unit>
    suspend fun deleteGroupRemotely(remoteGroupId: Long): Result<Unit>
    suspend fun deleteDistanceRemotely(remoteDistanceId: Long): Result<Unit>
    suspend fun deleteParticipantRemotely(participantId: String): Result<Unit>
    suspend fun deleteResultRemotely(resultId: String): Result<Unit>
}