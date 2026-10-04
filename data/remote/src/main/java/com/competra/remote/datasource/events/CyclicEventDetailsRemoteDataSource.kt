package com.competra.remote.datasource.events

import com.competra.remote.base.CommonModel
import com.competra.remote.request.events.RegisterEventRequest
import com.competra.remote.response.events.ClubMatchResponse
import com.competra.remote.response.events.CompetitionDetailResponse
import com.competra.remote.response.events.ParticipantPublicResponse
import com.competra.remote.response.events.RegistrationTeamOptionsResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CyclicEventDetailsRemoteDataSource {

    @GET("event/orienteering/competitions/public/{eventId}")
    suspend fun getEventDetails(
        @Path("eventId") eventId: String,
        @Query("userId") userId: String? = null
    ): Result<CommonModel<CompetitionDetailResponse>>

    @POST("event/orienteering/register")
    suspend fun registerToEvent(@Body request: RegisterEventRequest): Result<CommonModel<Unit>>

    @DELETE("event/orienteering/register/{competitionId}")
    suspend fun cancelRegistration(@Path("competitionId") competitionId: String): Result<CommonModel<Unit>>

    @GET("event/orienteering/competitions/{competitionId}/registration-team-options")
    suspend fun getRegistrationTeamOptions(
        @Path("competitionId") competitionId: String
    ): Result<CommonModel<RegistrationTeamOptionsResponse>>

    @GET("clubs/match")
    suspend fun matchClubs(@Query("name") name: String): Result<CommonModel<List<ClubMatchResponse>>>

    @GET("event/orienteering/participants")
    suspend fun getParticipantsByGroup(@Query("groupId") groupId: String): Result<CommonModel<List<ParticipantPublicResponse>>>

}
