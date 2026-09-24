package com.competra.remote.datasource.livetrack

import com.competra.remote.base.CommonModel
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * API онлайн-трекинга бегуна. Возвращает [Response] напрямую, а не `Result` через
 * `ResultCallAdapter`: отправщику нужен HTTP-код, чтобы отличить отказ сервера (4xx — не
 * повторять) от сбоя сети/сервера (повторить позже).
 */
interface LiveTrackRemoteDataSource {

    @POST("live-track/sessions")
    suspend fun start(@Body request: LiveTrackStartRequest): Response<CommonModel<LiveTrackSessionResponse>>

    @POST("live-track/sessions/{sessionId}/points")
    suspend fun sendPoints(
        @Path("sessionId") sessionId: String,
        @Body request: LiveTrackPointsRequest
    ): Response<CommonModel<LiveTrackAckResponse>>

    @POST("live-track/sessions/{sessionId}/stop")
    suspend fun stop(@Path("sessionId") sessionId: String): Response<CommonModel<LiveTrackAckResponse>>
}
