package com.competra.domain.models.livetrack

import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Окно сглаживания скорости: в лесу GPS «прыгает» на десятки метров, и скорость между соседними
 * точками шумная. Скорость в точке — смещение между краями окна вокруг неё, делённое на время.
 */
val SPEED_WINDOW_MS: Long = TimeUnit.SECONDS.toMillis(20)

/** Число ступеней цвета шкалы скорости: соседние отрезки одной ступени рисуются одной линией. */
const val SPEED_COLOR_STEPS = 16

/** Концы шкалы — перцентили скорости самого участника: выбросы GPS и стояние на старте не растягивают её. */
private const val SLOW_PERCENTILE = 0.05
private const val FAST_PERCENTILE = 0.95

/** Разброс скоростей меньше этого (м/с) — шкалы нет, весь трек одного «среднего» цвета. */
private const val MIN_SPEED_RANGE = 0.05

private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Кусок трека одного цвета.
 *
 * @property level Ступень скорости: 0 — самая медленная (красный), [SPEED_COLOR_STEPS] − 1 — самая
 *   быстрая (зелёный).
 * @property points Точки куска; соседние куски одного отрезка делят точку стыка — линия непрерывна.
 */
data class SpeedChunk(
    val level: Int,
    val points: List<ViewerTrackPoint>
)

/**
 * Трек, раскрашенный по скорости относительно самого участника.
 *
 * @property chunks Куски одного цвета; разрывы трека (потеря связи) не соединяются.
 * @property slowSpeed Скорость красного конца шкалы, м/с.
 * @property fastSpeed Скорость зелёного конца шкалы, м/с.
 */
data class TrackSpeedProfile(
    val chunks: List<SpeedChunk>,
    val slowSpeed: Double,
    val fastSpeed: Double
)

/**
 * Раскраска трека по скорости: сглаженная скорость в каждой точке ([SPEED_WINDOW_MS]), шкала от 5-го
 * до 95-го перцентиля скоростей участника, разбиение на [steps] ступеней. `null` — точек для расчёта
 * скорости мало.
 */
fun ViewerTrack.speedProfile(windowMs: Long = SPEED_WINDOW_MS, steps: Int = SPEED_COLOR_STEPS): TrackSpeedProfile? {
    val segments = segments().filter { it.size > 1 }
    val speeds = segments.map { smoothedSpeeds(it, windowMs) }
    val sorted = speeds.flatMap { it.filterNotNull() }.sorted()
    if (sorted.isEmpty()) return null
    val slow = sorted.percentile(SLOW_PERCENTILE)
    val fast = sorted.percentile(FAST_PERCENTILE)

    fun level(speed: Double): Int {
        if (fast - slow < MIN_SPEED_RANGE) return (steps - 1) / 2
        return (((speed - slow) / (fast - slow)).coerceIn(0.0, 1.0) * (steps - 1)).roundToInt()
    }

    val chunks = segments.zip(speeds).flatMap { (segment, segmentSpeeds) ->
        val filled = segmentSpeeds.filledGaps() ?: return@flatMap emptyList()
        // Цвет отрезка между точками — по средней скорости его концов.
        val edgeLevels = (0 until segment.lastIndex).map { i -> level((filled[i] + filled[i + 1]) / 2) }
        val result = mutableListOf<SpeedChunk>()
        var start = 0
        for (i in 1..edgeLevels.size) {
            if (i == edgeLevels.size || edgeLevels[i] != edgeLevels[start]) {
                result += SpeedChunk(edgeLevels[start], segment.subList(start, i + 1))
                start = i
            }
        }
        result
    }
    return TrackSpeedProfile(chunks, slowSpeed = slow, fastSpeed = fast)
}

/**
 * Сглаженная скорость (м/с) в каждой точке отрезка: смещение между крайними точками окна
 * ±[windowMs]/2 вокруг неё. В окне всегда есть хотя бы один сосед — иначе редкие точки остались бы
 * без скорости. `null` — у точек окна одинаковое время (архив округляет время до секунды).
 */
internal fun smoothedSpeeds(points: List<ViewerTrackPoint>, windowMs: Long): List<Double?> {
    val half = windowMs / 2
    var lo = 0
    var hi = 0
    return points.indices.map { i ->
        val t = points[i].t
        while (points[lo].t < t - half) lo++
        if (hi < i) hi = i
        while (hi + 1 < points.size && points[hi + 1].t <= t + half) hi++
        val from = if (lo == i && i > 0) i - 1 else lo
        val to = if (hi == i && i < points.lastIndex) i + 1 else hi
        val dt = points[to].t - points[from].t
        if (dt <= 0) null else distanceMeters(points[from], points[to]) / (dt / 1000.0)
    }
}

/** Точки без скорости берут скорость ближайшей предыдущей (в начале — первой известной). */
private fun List<Double?>.filledGaps(): List<Double>? {
    val first = firstOrNull { it != null } ?: return null
    var last: Double = first
    return map { speed -> (speed ?: last).also { last = it } }
}

private fun List<Double>.percentile(p: Double): Double = this[(lastIndex * p).roundToInt()]

private fun distanceMeters(from: ViewerTrackPoint, to: ViewerTrackPoint): Double {
    val dLat = Math.toRadians(to.lat - from.lat)
    val dLon = Math.toRadians(to.lon - from.lon)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(from.lat)) * cos(Math.toRadians(to.lat)) * sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
}
