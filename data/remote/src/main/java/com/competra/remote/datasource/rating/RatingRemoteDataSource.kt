package com.competra.remote.datasource.rating

import com.competra.remote.base.CommonModel
import com.competra.remote.base.PagedResponse
import com.competra.remote.request.rating.AddCompetitionToRatingRequest
import com.competra.remote.request.rating.CreateRatingRequest
import com.competra.remote.request.rating.MergeStandingsRequest
import com.competra.remote.request.rating.SetGroupMappingRequest
import com.competra.remote.request.rating.UpdateRatingRequest
import com.competra.remote.response.rating.AddCompetitionToRatingResponse
import com.competra.remote.response.rating.RatingCompetitionResponse
import com.competra.remote.response.rating.RatingGroupMappingSuggestionResponse
import com.competra.remote.response.rating.RatingResponse
import com.competra.remote.response.rating.RatingSearchResponse
import com.competra.remote.response.rating.RatingStandingsResponse
import com.competra.remote.response.orienteering.OrganizerLinkResultResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface RatingRemoteDataSource {

    @GET("ratings")
    suspend fun search(
        @Query("query") query: String?,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Result<CommonModel<PagedResponse<RatingSearchResponse>>>

    @GET("clubs/{clubId}/ratings")
    suspend fun listForClub(@Path("clubId") clubId: String): Result<CommonModel<List<RatingResponse>>>

    @GET("ratings/{id}")
    suspend fun getById(@Path("id") id: String): Result<CommonModel<RatingResponse>>

    @POST("clubs/{clubId}/ratings")
    suspend fun create(@Path("clubId") clubId: String, @Body request: CreateRatingRequest): Result<CommonModel<RatingResponse>>

    @PUT("ratings/{id}")
    suspend fun update(@Path("id") id: String, @Body request: UpdateRatingRequest): Result<CommonModel<RatingResponse>>

    @DELETE("ratings/{id}")
    suspend fun delete(@Path("id") id: String): Result<CommonModel<Any>>

    @GET("ratings/{id}/competitions")
    suspend fun listCompetitions(@Path("id") id: String): Result<CommonModel<List<RatingCompetitionResponse>>>

    @POST("ratings/{id}/competitions")
    suspend fun addCompetition(
        @Path("id") id: String,
        @Body request: AddCompetitionToRatingRequest
    ): Result<CommonModel<AddCompetitionToRatingResponse>>

    @DELETE("ratings/{id}/competitions/{competitionId}")
    suspend fun removeCompetition(
        @Path("id") id: String,
        @Path("competitionId") competitionId: String
    ): Result<CommonModel<Any>>

    @GET("ratings/{id}/competitions/{competitionId}/mapping-suggestions")
    suspend fun getGroupMappingSuggestions(
        @Path("id") id: String,
        @Path("competitionId") competitionId: String
    ): Result<CommonModel<List<RatingGroupMappingSuggestionResponse>>>

    @PUT("ratings/{id}/competitions/{competitionId}/mapping")
    suspend fun setGroupMapping(
        @Path("id") id: String,
        @Path("competitionId") competitionId: String,
        @Body request: SetGroupMappingRequest
    ): Result<CommonModel<Any>>

    @GET("ratings/{id}/standings")
    suspend fun getStandings(
        @Path("id") id: String,
        @Query("groupId") groupId: Long
    ): Result<CommonModel<RatingStandingsResponse>>

    /** Объединить дубли в таблице рейтинга: привязать к аккаунту основной строки или переименовать в её ФИО. */
    @POST("ratings/{id}/standings/merge")
    suspend fun mergeStandings(
        @Path("id") id: String,
        @Body request: MergeStandingsRequest
    ): Result<CommonModel<OrganizerLinkResultResponse>>
}
