package com.competra.remote.livetrack

import com.competra.domain.models.livetrack.LiveTrackCloseReason
import com.competra.domain.models.livetrack.LiveTrackStatus
import com.competra.remote.base.CommonModel
import com.competra.remote.datasource.livetrack.ArchivedTrackDto
import com.competra.remote.datasource.livetrack.LiveSnapshotDto
import com.competra.remote.datasource.livetrack.LiveTrackViewerDataSource
import com.competra.remote.datasource.livetrack.TrackedDistanceDto
import com.competra.remote.repository.livetrack.LiveTrackViewerRepositoryImpl
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Разбор реальных ответов процесса трекинга (сняты с локального прогона сервера). */
class LiveTrackViewerRepositoryImplTest {

    private val gson = Gson()

    private val liveJson = """
        {"result":{"cursor":"f7978191:2","reset":false,"serverTime":1790203240000,"sessions":[
          {"sessionId":"s1","participantId":"lt-test-p1","displayName":"Иванов Иван","groupName":"М21","startNumber":101,
           "status":"ACTIVE","startedAt":1790203237000,"lastPointAt":1790203239000,
           "points":[[1790203238000,55.1,37.6],[1790203239000,55.2,37.6]]}]},"status":1}
    """.trimIndent()

    private val tracksJson = """
        {"result":[{"sessionId":"s1","participantId":"lt-test-p1","displayName":"Иванов Иван","groupName":"М21",
          "startNumber":101,"status":"STOPPED","closeReason":"MANUAL","startedAt":1790203237000,"closedAt":1790203250000,
          "trackEncoded":"5505000:3760000:0;5510000:3760000:1"}],"status":1}
    """.trimIndent()

    private val dataSource = object : LiveTrackViewerDataSource {
        override suspend fun distances(competitionId: String) = Result.success(
            gson.fromJson<CommonModel<List<TrackedDistanceDto>>>(
                """{"result":[{"distanceId":1,"name":"LT Длинная","activeCount":1,"totalCount":1}],"status":1}""",
                object : TypeToken<CommonModel<List<TrackedDistanceDto>>>() {}.type
            )
        )
        override suspend fun live(distanceId: Long, since: String?) = Result.success(
            gson.fromJson<CommonModel<LiveSnapshotDto>>(liveJson, object : TypeToken<CommonModel<LiveSnapshotDto>>() {}.type)
        )
        override suspend fun tracks(distanceId: Long) = Result.success(
            gson.fromJson<CommonModel<List<ArchivedTrackDto>>>(tracksJson, object : TypeToken<CommonModel<List<ArchivedTrackDto>>>() {}.type)
        )
    }

    private val repository = LiveTrackViewerRepositoryImpl(dataSource)

    @Test
    fun `live snapshot points are parsed from t-lat-lon arrays without losing millisecond precision`() = runBlocking {
        val snapshot = repository.live(1, null).getOrThrow()

        val session = snapshot.sessions.single()
        assertEquals("f7978191:2", snapshot.cursor)
        assertEquals(LiveTrackStatus.ACTIVE, session.status)
        assertEquals(listOf(1790203238000L, 1790203239000L), session.points.map { it.t })
        assertEquals(55.2, session.points.last().lat, 1e-9)
    }

    @Test
    fun `archived track is decoded from TrackCodec relative to startedAt`() = runBlocking {
        val track = repository.tracks(1).getOrThrow().single()

        assertEquals(LiveTrackCloseReason.MANUAL, track.closeReason)
        assertEquals(listOf(1790203237000L, 1790203238000L), track.points.map { it.t })
        assertEquals(55.05, track.points.first().lat, 1e-9)
        assertEquals(1790203238000L, track.lastPointAt)
    }

    @Test
    fun `tracked distances are mapped`() = runBlocking {
        assertEquals(1L, repository.distances("c").getOrThrow().single().distanceId)
    }
}
