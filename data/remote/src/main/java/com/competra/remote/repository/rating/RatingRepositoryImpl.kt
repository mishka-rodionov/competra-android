package com.competra.remote.repository.rating

import com.competra.remote.request.rating.MergeStandingsRequest
import com.competra.domain.models.PagedResult
import com.competra.domain.models.rating.AddCompetitionToRatingResult
import com.competra.domain.models.rating.RatingCompetition
import com.competra.domain.models.rating.RatingGroup
import com.competra.domain.models.rating.RatingGroupMappingSuggestion
import com.competra.domain.models.rating.RatingSeries
import com.competra.domain.models.rating.RatingStandings
import com.competra.domain.models.rating.RatingSummary
import com.competra.domain.repository.rating.RatingRepository
import com.competra.remote.datasource.rating.RatingRemoteDataSource
import com.competra.remote.request.rating.AddCompetitionToRatingRequest
import com.competra.remote.request.rating.CreateRatingRequest
import com.competra.remote.request.rating.GroupMappingEntry
import com.competra.remote.request.rating.RatingGroupRequest
import com.competra.remote.request.rating.SetGroupMappingRequest
import com.competra.remote.request.rating.UpdateRatingRequest
import com.competra.remote.response.mappers.toDomain

class RatingRepositoryImpl(
    private val ratingRemoteDataSource: RatingRemoteDataSource
) : RatingRepository {

    override suspend fun search(query: String?, page: Int, limit: Int): Result<PagedResult<RatingSummary>> {
        return ratingRemoteDataSource.search(query, page, limit).mapCatching { response ->
            val paged = response.result
            PagedResult(
                items = paged?.items.orEmpty().map { it.toDomain() },
                hasMore = paged?.hasMore ?: false
            )
        }
    }

    override suspend fun listForClub(clubId: String): Result<List<RatingSeries>> {
        return ratingRemoteDataSource.listForClub(clubId).mapCatching { it.result!!.map { rating -> rating.toDomain() } }
    }

    override suspend fun getById(ratingId: String): Result<RatingSeries> {
        return ratingRemoteDataSource.getById(ratingId).mapCatching { it.result!!.toDomain() }
    }

    override suspend fun create(clubId: String, name: String, groups: List<RatingGroup>): Result<RatingSeries> {
        return ratingRemoteDataSource.create(clubId, CreateRatingRequest(name, groups.map { it.toRequest() }))
            .mapCatching { it.result!!.toDomain() }
    }

    override suspend fun update(ratingId: String, name: String, groups: List<RatingGroup>): Result<RatingSeries> {
        return ratingRemoteDataSource.update(ratingId, UpdateRatingRequest(name, groups.map { it.toRequest() }))
            .mapCatching { it.result!!.toDomain() }
    }

    override suspend fun delete(ratingId: String): Result<Unit> {
        return ratingRemoteDataSource.delete(ratingId).mapCatching { Unit }
    }

    override suspend fun listCompetitions(ratingId: String): Result<List<RatingCompetition>> {
        return ratingRemoteDataSource.listCompetitions(ratingId).mapCatching { it.result!!.map { competition -> competition.toDomain() } }
    }

    override suspend fun addCompetition(ratingId: String, competitionId: String): Result<AddCompetitionToRatingResult> {
        return ratingRemoteDataSource.addCompetition(ratingId, AddCompetitionToRatingRequest(competitionId))
            .mapCatching { it.result!!.toDomain() }
    }

    override suspend fun removeCompetition(ratingId: String, competitionId: String): Result<Unit> {
        return ratingRemoteDataSource.removeCompetition(ratingId, competitionId).mapCatching { Unit }
    }

    override suspend fun getGroupMappingSuggestions(ratingId: String, competitionId: String): Result<List<RatingGroupMappingSuggestion>> {
        return ratingRemoteDataSource.getGroupMappingSuggestions(ratingId, competitionId)
            .mapCatching { it.result!!.map { suggestion -> suggestion.toDomain() } }
    }

    override suspend fun setGroupMapping(ratingId: String, competitionId: String, mappings: Map<Long, Long>): Result<Unit> {
        val request = SetGroupMappingRequest(mappings.map { (participantGroupId, ratingGroupId) ->
            GroupMappingEntry(participantGroupId, ratingGroupId)
        })
        return ratingRemoteDataSource.setGroupMapping(ratingId, competitionId, request).mapCatching { Unit }
    }

    override suspend fun getStandings(ratingId: String, ratingGroupId: Long): Result<RatingStandings> {
        return ratingRemoteDataSource.getStandings(ratingId, ratingGroupId).mapCatching { it.result!!.toDomain() }
    }

    override suspend fun mergeStandings(
        ratingId: String,
        ratingGroupId: Long,
        targetKey: String,
        sourceKeys: List<String>
    ): Result<Int> =
        ratingRemoteDataSource.mergeStandings(ratingId, MergeStandingsRequest(ratingGroupId, targetKey, sourceKeys))
            .mapCatching { it.result?.updatedCount ?: 0 }

    private fun RatingGroup.toRequest() = RatingGroupRequest(
        id = id.takeIf { it != 0L },
        title = title,
        gender = gender?.name,
        minAge = minAge,
        maxAge = maxAge,
        orderIndex = orderIndex
    )
}
