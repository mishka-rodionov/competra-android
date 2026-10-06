package com.competra.domain.models.livetrack

import java.util.concurrent.TimeUnit

/** Яркий «хвост» за маркером при просмотре: последние столько миллисекунд пути. */
val REPLAY_TAIL_MS: Long = TimeUnit.MINUTES.toMillis(2)

/** Шкала времени просмотра. */
enum class ReplayTimeMode {
    /** Все по общим часам: где кто был в 10:42. Позиция ползунка — Unix ms. */
    REAL_TIME,

    /** Каждый от своего старта — все выбегают одновременно. Позиция ползунка — ms от старта. */
    MASS_START
}

/** Что делает участник в момент просмотра. */
enum class ReplayRunnerState {
    /** Ещё не стартовал (только в [ReplayTimeMode.REAL_TIME]). */
    NOT_STARTED,

    /** На дистанции, позиция известна. */
    RUNNING,

    /** На дистанции, но точек нет (разрыв связи) — показывается последняя известная точка. */
    NO_DATA,

    /** Финишировал (или трек закончился). */
    FINISHED
}

/**
 * Положение участника в момент просмотра.
 *
 * @property point Точка на треке (между фиксами — интерполированная), её `t` — момент просмотра.
 */
data class ReplayPosition(
    val point: ViewerTrackPoint,
    val state: ReplayRunnerState
)

/**
 * Завершённый трек для просмотра: точки только от старта до финиша участника.
 *
 * @property startAt Старт участника (Unix ms) — из результата, иначе первая точка трека.
 * @property finishAt Финиш участника (Unix ms) — из результата, иначе последняя точка трека.
 */
data class ReplayTrack(
    val track: ViewerTrack,
    val startAt: Long,
    val finishAt: Long
) {
    /** Точки трека от старта до финиша. */
    val points: List<ViewerTrackPoint> get() = track.points

    /** Время на дистанции, ms. */
    val duration: Long get() = finishAt - startAt

    /** Момент (Unix ms) для позиции ползунка [position] в режиме [mode]. */
    fun timeAt(position: Long, mode: ReplayTimeMode): Long =
        if (mode == ReplayTimeMode.REAL_TIME) position else startAt + position

    /**
     * Положение в момент [t]: между фиксами — линейная интерполяция; в разрыве дольше
     * [TRACK_GAP_MS] — последняя известная точка ([ReplayRunnerState.NO_DATA]).
     */
    fun positionAt(t: Long): ReplayPosition {
        val first = points.first()
        val last = points.last()
        return when {
            t < startAt -> ReplayPosition(first.copy(t = t), ReplayRunnerState.NOT_STARTED)
            t <= first.t -> ReplayPosition(first.copy(t = t), ReplayRunnerState.RUNNING)
            t >= finishAt -> ReplayPosition(last.copy(t = t), ReplayRunnerState.FINISHED)
            t >= last.t -> ReplayPosition(
                last.copy(t = t),
                if (t - last.t > TRACK_GAP_MS) ReplayRunnerState.NO_DATA else ReplayRunnerState.RUNNING
            )
            else -> {
                val i = lastIndexAtOrBefore(t)
                val a = points[i]
                val b = points[i + 1]
                if (b.t - a.t > TRACK_GAP_MS) {
                    ReplayPosition(a.copy(t = t), ReplayRunnerState.NO_DATA)
                } else {
                    val k = if (b.t == a.t) 0.0 else (t - a.t).toDouble() / (b.t - a.t)
                    ReplayPosition(ViewerTrackPoint(t, a.lat + (b.lat - a.lat) * k, a.lon + (b.lon - a.lon) * k), ReplayRunnerState.RUNNING)
                }
            }
        }
    }

    /**
     * Путь за последние [length] ms до момента [t], заканчивающийся текущим положением. Разрывы
     * дольше [TRACK_GAP_MS] не соединяются. До старта — пусто.
     */
    fun tail(t: Long, length: Long = REPLAY_TAIL_MS): List<List<ViewerTrackPoint>> {
        if (t < startAt) return emptyList()
        val now = minOf(t, finishAt)
        val from = now - length
        val window = points.filter { it.t > from && it.t <= now }.toMutableList()
        val position = positionAt(now)
        if (position.state == ReplayRunnerState.RUNNING && window.lastOrNull()?.t != now) window += position.point
        return window.splitByGaps()
    }

    private fun lastIndexAtOrBefore(t: Long): Int {
        var lo = 0
        var hi = points.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (points[mid].t <= t) lo = mid else hi = mid - 1
        }
        return lo
    }
}

/**
 * Трек для просмотра, обрезанный по старту и финишу из результата ([startTime]/[finishTime], Unix ms):
 * без разминки и дороги обратно. Если результата нет или после обрезки остаётся меньше двух точек —
 * весь трек. `null` — точек меньше двух.
 */
fun ViewerTrack.toReplay(startTime: Long?, finishTime: Long?): ReplayTrack? {
    if (points.size < 2) return null
    val start = startTime ?: points.first().t
    val finish = finishTime?.takeIf { it > start } ?: points.last().t
    val trimmed = points.filter { it.t in start..finish }
    return if (trimmed.size >= 2) {
        ReplayTrack(copy(points = trimmed), start, finish)
    } else {
        ReplayTrack(this, points.first().t, points.last().t)
    }
}

/** Диапазон ползунка: в реальном времени — от первого старта до последнего финиша, иначе 0…самое долгое время. */
fun List<ReplayTrack>.replayRange(mode: ReplayTimeMode): LongRange? {
    if (isEmpty()) return null
    return when (mode) {
        ReplayTimeMode.REAL_TIME -> minOf { it.startAt }..maxOf { it.finishAt }
        ReplayTimeMode.MASS_START -> 0L..maxOf { it.duration }
    }
}

private fun List<ViewerTrackPoint>.splitByGaps(): List<List<ViewerTrackPoint>> {
    val result = mutableListOf<MutableList<ViewerTrackPoint>>()
    forEach { point ->
        val current = result.lastOrNull()
        if (current == null || point.t - current.last().t > TRACK_GAP_MS) result += mutableListOf(point) else current += point
    }
    return result
}
