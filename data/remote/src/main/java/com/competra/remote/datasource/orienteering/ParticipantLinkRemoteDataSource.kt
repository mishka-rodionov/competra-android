package com.competra.remote.datasource.orienteering

import com.competra.remote.base.CommonModel
import com.competra.remote.request.orienteering.CreateParticipantLinkRequest
import com.competra.remote.request.orienteering.ReviewParticipantLinkRequest
import com.competra.remote.response.orienteering.CompetitionLinkRequestResponse
import com.competra.remote.response.orienteering.LinkRequestResponse
import com.competra.remote.response.orienteering.LinkSuggestionResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Заявки на привязку вручную внесённых участников к аккаунтам (eSport: ParticipantLinkRouting.kt). */
interface ParticipantLinkRemoteDataSource {

    @GET("event/orienteering/link-requests/suggestions")
    suspend fun getSuggestions(): Result<CommonModel<List<LinkSuggestionResponse>>>

    @POST("event/orienteering/link-requests")
    suspend fun createRequests(@Body request: CreateParticipantLinkRequest): Result<CommonModel<List<LinkRequestResponse>>>

    @GET("event/orienteering/link-requests/mine")
    suspend fun getMyRequests(): Result<CommonModel<List<LinkRequestResponse>>>

    @DELETE("event/orienteering/link-requests/{id}")
    suspend fun cancelRequest(@Path("id") requestId: String): Result<CommonModel<Any>>

    @GET("event/orienteering/link-requests/competition")
    suspend fun getCompetitionRequests(
        @Query("competitionId") competitionId: String
    ): Result<CommonModel<List<CompetitionLinkRequestResponse>>>

    @GET("event/orienteering/link-requests/pending-counts")
    suspend fun getPendingCounts(): Result<CommonModel<Map<String, Int>>>

    @PUT("event/orienteering/link-requests/{id}")
    suspend fun reviewRequest(
        @Path("id") requestId: String,
        @Body request: ReviewParticipantLinkRequest
    ): Result<CommonModel<LinkRequestResponse>>

    @POST("event/orienteering/participants/{id}/unlink")
    suspend fun unlinkParticipant(@Path("id") participantId: String): Result<CommonModel<Any>>
}
