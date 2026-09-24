package com.competra.domain.models.livetrack

import java.util.concurrent.TimeUnit

/** Разрыв между точками больше этого — трек не соединяется линией (потеря сигнала/связи). */
val TRACK_GAP_MS: Long = TimeUnit.SECONDS.toMillis(30)

/** Активный участник без новых точек дольше этого показывается «серым» — нет данных. */
val TRACK_STALE_MS: Long = TimeUnit.SECONDS.toMillis(60)

/**
 * Дистанция соревнования, по которой есть онлайн-треки.
 *
 * @property distanceId Серверный id дистанции.
 * @property name Название дистанции.
 * @property activeCount Участников на дистанции сейчас.
 * @property totalCount Всего треков по дистанции.
 */
data class TrackedDistance(
    val distanceId: Long,
    val name: String?,
    val activeCount: Int,
    val totalCount: Int
)

/** Точка трека для зрителя. */
data class ViewerTrackPoint(
    val t: Long,
    val lat: Double,
    val lon: Double
)

/**
 * Трек участника у зрителя (метаданные + накопленные точки, отсортированные по времени).
 *
 * @property lastPointAt Время последней точки по данным сервера (Unix ms).
 */
data class ViewerTrack(
    val sessionId: String,
    val participantId: String,
    val displayName: String,
    val groupName: String?,
    val startNumber: Int?,
    val status: LiveTrackStatus,
    val closeReason: LiveTrackCloseReason?,
    val startedAt: Long,
    val lastPointAt: Long?,
    val points: List<ViewerTrackPoint>
) {
    /** Участник ещё на дистанции. */
    val isActive: Boolean get() = status == LiveTrackStatus.ACTIVE

    /** Активный участник, от которого давно нет точек (по часам сервера [serverTime]). */
    fun isStale(serverTime: Long): Boolean {
        if (!isActive) return false
        val last = lastPointAt ?: startedAt
        return serverTime - last > TRACK_STALE_MS
    }

    /** Отрезки трека: точки с разрывом больше [gapMs] не соединяются линией. */
    fun segments(gapMs: Long = TRACK_GAP_MS, since: Long? = null): List<List<ViewerTrackPoint>> {
        val visible = if (since == null) points else points.filter { it.t >= since }
        val result = mutableListOf<MutableList<ViewerTrackPoint>>()
        visible.forEach { point ->
            val current = result.lastOrNull()
            if (current == null || point.t - current.last().t > gapMs) result += mutableListOf(point) else current += point
        }
        return result
    }
}

/**
 * Изменения по сессии из ответа `live`: метаданные и новые точки (в порядке приёма сервером).
 */
typealias ViewerSessionUpdate = ViewerTrack

/**
 * Ответ `live`.
 *
 * @property cursor Непрозрачный курсор для следующего запроса.
 * @property reset Курсор не узнан сервером (перезапуск): точки сессий в ответе — полные.
 * @property serverTime Время сервера (Unix ms).
 */
data class LiveTrackSnapshot(
    val cursor: String,
    val reset: Boolean,
    val serverTime: Long,
    val sessions: List<ViewerSessionUpdate>
)
