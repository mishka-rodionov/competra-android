package com.competra.remote.datasource.livetrack

import com.competra.remote.base.CommonModel
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Публичные эндпоинты онлайн-треков для зрителей (`/api/live-track`). */
interface LiveTrackViewerDataSource {

    @GET("live-track/competitions/{competitionId}/distances")
    suspend fun distances(@Path("competitionId") competitionId: String): Result<CommonModel<List<TrackedDistanceDto>>>

    @GET("live-track/distances/{distanceId}/live")
    suspend fun live(
        @Path("distanceId") distanceId: Long,
        @Query("since") since: String?
    ): Result<CommonModel<LiveSnapshotDto>>

    @GET("live-track/distances/{distanceId}/tracks")
    suspend fun tracks(@Path("distanceId") distanceId: Long): Result<CommonModel<List<ArchivedTrackDto>>>
}
