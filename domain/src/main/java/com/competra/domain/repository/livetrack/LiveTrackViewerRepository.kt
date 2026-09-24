package com.competra.domain.repository.livetrack

import com.competra.domain.models.livetrack.LiveTrackSnapshot
import com.competra.domain.models.livetrack.TrackedDistance
import com.competra.domain.models.livetrack.ViewerTrack

/** Публичное API онлайн-треков для зрителей (без авторизации). */
interface LiveTrackViewerRepository {

    /** Дистанции соревнования, по которым есть треки. */
    suspend fun distances(competitionId: String): Result<List<TrackedDistance>>

    /** Живой снимок дистанции: новые точки после [cursor] (`null` — с начала). */
    suspend fun live(distanceId: Long, cursor: String?): Result<LiveTrackSnapshot>

    /** Архив треков дистанции (полные треки, в т.ч. закрытые давно). */
    suspend fun tracks(distanceId: Long): Result<List<ViewerTrack>>
}
