package com.competra.remote.repository.orienteering

import com.competra.domain.models.participant_link.CompetitionLinkRequest
import com.competra.domain.models.participant_link.LinkRequest
import com.competra.domain.models.participant_link.LinkRequestSource
import com.competra.domain.models.participant_link.LinkSuggestion
import com.competra.domain.repository.participant_link.ParticipantLinkRepository
import com.competra.remote.datasource.orienteering.ParticipantLinkRemoteDataSource
import com.competra.remote.request.orienteering.CreateParticipantLinkRequest
import com.competra.remote.request.orienteering.ReviewParticipantLinkRequest
import com.competra.remote.response.mappers.toDomain

class ParticipantLinkRepositoryImpl(
    private val remoteDataSource: ParticipantLinkRemoteDataSource
) : ParticipantLinkRepository {

    override suspend fun getSuggestions(): Result<List<LinkSuggestion>> =
        remoteDataSource.getSuggestions().mapCatching { it.result!!.map { s -> s.toDomain() } }

    override suspend fun createRequests(
        participantIds: List<String>,
        source: LinkRequestSource
    ): Result<List<LinkRequest>> =
        remoteDataSource.createRequests(CreateParticipantLinkRequest(participantIds, source.name))
            .mapCatching { it.result!!.map { r -> r.toDomain() } }

    override suspend fun getMyRequests(): Result<List<LinkRequest>> =
        remoteDataSource.getMyRequests().mapCatching { it.result!!.map { r -> r.toDomain() } }

    override suspend fun cancelRequest(requestId: String): Result<Unit> =
        remoteDataSource.cancelRequest(requestId).mapCatching { Unit }

    override suspend fun getCompetitionRequests(competitionId: String): Result<List<CompetitionLinkRequest>> =
        remoteDataSource.getCompetitionRequests(competitionId).mapCatching { it.result!!.map { r -> r.toDomain() } }

    override suspend fun getPendingCounts(): Result<Map<String, Int>> =
        remoteDataSource.getPendingCounts().mapCatching { it.result.orEmpty() }

    override suspend fun reviewRequest(requestId: String, approve: Boolean, comment: String?): Result<Unit> =
        remoteDataSource.reviewRequest(requestId, ReviewParticipantLinkRequest(approve, comment)).mapCatching { Unit }

    override suspend fun unlinkParticipant(participantId: String): Result<Unit> =
        remoteDataSource.unlinkParticipant(participantId).mapCatching { Unit }
}
