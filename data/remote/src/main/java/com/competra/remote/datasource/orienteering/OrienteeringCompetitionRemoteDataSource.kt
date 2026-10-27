package com.competra.remote.datasource.orienteering

import com.competra.remote.base.CommonModel
import com.competra.remote.request.orienteering.DistanceRequest
import com.competra.remote.request.orienteering.OrienteeringCompetitionRequest
import com.competra.remote.request.orienteering.OrienteeringParticipantRequest
import com.competra.remote.request.orienteering.OrienteeringResultRequest
import com.competra.remote.request.orienteering.ParticipantGroupPublishRequest
import com.competra.remote.request.orienteering.ParticipantGroupRequest
import com.competra.remote.response.orienteering.DistanceResponse
import com.competra.remote.response.orienteering.OrienteeringCompetitionResponse
import com.competra.remote.response.orienteering.OrienteeringParticipantResponse
import com.competra.remote.response.orienteering.OrienteeringResultResponse
import com.competra.remote.response.orienteering.ParticipantGroupResponse
import com.competra.remote.response.orienteering.ParticipantHistoryResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OrienteeringCompetitionRemoteDataSource {

    @POST("event/orienteering/save/competitions")
    suspend fun createOrienteeringCompetition(@Body request: OrienteeringCompetitionRequest): Result<CommonModel<OrienteeringCompetitionResponse>>

    @POST("event/orienteering/save/participantGroup")
    suspend fun createCompetitionParticipantGroup(@Body request: List<ParticipantGroupRequest>): Result<CommonModel<List<ParticipantGroupResponse>>>

    @POST("event/orienteering/save/participantGroup")
    suspend fun publishParticipantGroups(@Body request: List<ParticipantGroupPublishRequest>): Result<CommonModel<List<ParticipantGroupResponse>>>

    /** Публичное получение соревнования по id — без ограничения по владельцу/участию. */
    @GET("event/orienteering/competitions/{id}")
    suspend fun getCompetitionById(@Path("id") id: String): Result<CommonModel<OrienteeringCompetitionResponse>>

    @GET("event/orienteering/competitions")
    suspend fun getCompetitionsByUserid(): Result<CommonModel<List<OrienteeringCompetitionResponse>>>

    @GET("event/orienteering/competitions/registered")
    suspend fun getRegisteredCompetitions(): Result<CommonModel<List<OrienteeringCompetitionResponse>>>

    @POST("event/orienteering/save/participant")
    suspend fun saveParticipant(@Body request: OrienteeringParticipantRequest): Result<CommonModel<OrienteeringParticipantResponse>>

    @POST("event/orienteering/save/participants")
    suspend fun saveParticipants(@Body requests: List<OrienteeringParticipantRequest>): Result<CommonModel<List<OrienteeringParticipantResponse>>>

    @POST("event/orienteering/save/result")
    suspend fun saveResult(@Body request: OrienteeringResultRequest): Result<CommonModel<OrienteeringResultResponse>>

    @POST("event/orienteering/save/distances")
    suspend fun saveDistances(@Body request: List<DistanceRequest>): Result<CommonModel<List<DistanceResponse>>>

    @GET("event/orienteering/distances")
    suspend fun getDistances(@Query("competitionId") competitionId: String): Result<CommonModel<List<DistanceResponse>>>

    @GET("event/orienteering/participantGroups")
    suspend fun getParticipantGroups(@Query("competitionId") competitionId: String): Result<CommonModel<List<ParticipantGroupResponse>>>

    @GET("event/orienteering/participants/competition")
    suspend fun getParticipantsByCompetition(@Query("competitionId") competitionId: String): Result<CommonModel<List<OrienteeringParticipantResponse>>>

    /** Участия во всех соревнованиях текущего пользователя-организатора, кроме тестовых. */
    @GET("event/orienteering/participants/history")
    suspend fun getParticipantHistory(@Query("excludeCompetitionId") excludeCompetitionId: String): Result<CommonModel<List<ParticipantHistoryResponse>>>

    @GET("event/orienteering/results/competition")
    suspend fun getResultsByCompetition(@Query("competitionId") competitionId: String): Result<CommonModel<List<OrienteeringResultResponse>>>

    @POST("event/orienteering/save/results")
    suspend fun saveResults(@Body requests: List<OrienteeringResultRequest>): Result<CommonModel<List<OrienteeringResultResponse>>>

    @DELETE("event/orienteering/competitions/{id}")
    suspend fun deleteCompetition(@Path("id") id: String): Result<CommonModel<Any>>

    @DELETE("event/orienteering/participantGroups/{id}")
    suspend fun deleteParticipantGroup(@Path("id") id: Long): Result<CommonModel<Any>>

    @DELETE("event/orienteering/participants/{id}")
    suspend fun deleteParticipant(@Path("id") id: String): Result<CommonModel<Any>>

    @DELETE("event/orienteering/results/{id}")
    suspend fun deleteResult(@Path("id") id: String): Result<CommonModel<Any>>

    @DELETE("event/orienteering/distances/{id}")
    suspend fun deleteDistance(@Path("id") id: Long): Result<CommonModel<Any>>

}