package com.competra.remote.repository.livetrack

import com.competra.domain.diary.TrackCodec
import com.competra.domain.models.livetrack.LiveTrackCloseReason
import com.competra.domain.models.livetrack.LiveTrackSnapshot
import com.competra.domain.models.livetrack.LiveTrackStatus
import com.competra.domain.models.livetrack.TrackedDistance
import com.competra.domain.models.livetrack.ViewerTrack
import com.competra.domain.models.livetrack.ViewerTrackPoint
import com.competra.domain.repository.livetrack.LiveTrackViewerRepository
import com.competra.remote.datasource.livetrack.ArchivedTrackDto
import com.competra.remote.datasource.livetrack.LiveSessionDto
import com.competra.remote.datasource.livetrack.LiveTrackViewerDataSource

/** [LiveTrackViewerRepository] поверх публичных эндпоинтов процесса трекинга. */
class LiveTrackViewerRepositoryImpl(
    private val dataSource: LiveTrackViewerDataSource
) : LiveTrackViewerRepository {

    override suspend fun distances(competitionId: String): Result<List<TrackedDistance>> =
        dataSource.distances(competitionId).mapCatching { response ->
            response.result.orEmpty().map { TrackedDistance(it.distanceId, it.name, it.activeCount, it.totalCount) }
        }

    override suspend fun live(distanceId: Long, cursor: String?): Result<LiveTrackSnapshot> =
        dataSource.live(distanceId, cursor).mapCatching { response ->
            val dto = checkNotNull(response.result)
            LiveTrackSnapshot(
                cursor = dto.cursor,
                reset = dto.reset,
                serverTime = dto.serverTime,
                sessions = dto.sessions.orEmpty().map { it.toDomain() }
            )
        }

    override suspend fun tracks(distanceId: Long): Result<List<ViewerTrack>> =
        dataSource.tracks(distanceId).mapCatching { response -> response.result.orEmpty().map { it.toDomain() } }
}

private fun LiveSessionDto.toDomain() = ViewerTrack(
    sessionId = sessionId,
    participantId = participantId,
    displayName = displayName.orEmpty(),
    groupName = groupName,
    startNumber = startNumber,
    status = status.toStatus(),
    closeReason = closeReason.toCloseReason(),
    startedAt = startedAt,
    lastPointAt = lastPointAt,
    points = points.orEmpty().mapNotNull { raw ->
        if (raw.size < 3) null else ViewerTrackPoint(t = raw[0].toLong(), lat = raw[1], lon = raw[2])
    }
)

private fun ArchivedTrackDto.toDomain(): ViewerTrack {
    val points = TrackCodec.decode(startedAt, trackEncoded).map { ViewerTrackPoint(it.timestampMs, it.lat, it.lon) }
    return ViewerTrack(
        sessionId = sessionId,
        participantId = participantId,
        displayName = displayName.orEmpty(),
        groupName = groupName,
        startNumber = startNumber,
        status = status.toStatus(),
        closeReason = closeReason.toCloseReason(),
        startedAt = startedAt,
        lastPointAt = points.maxOfOrNull { it.t },
        points = points
    )
}

private fun String.toStatus(): LiveTrackStatus = runCatching { LiveTrackStatus.valueOf(this) }.getOrDefault(LiveTrackStatus.ACTIVE)

private fun String?.toCloseReason(): LiveTrackCloseReason? = this?.let { runCatching { LiveTrackCloseReason.valueOf(it) }.getOrNull() }
