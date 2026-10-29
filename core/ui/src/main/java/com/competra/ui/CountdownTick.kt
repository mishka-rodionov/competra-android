package com.competra.ui

/** Запас после границы секунды, чтобы округление вверх уже дало новое значение. */
private const val BOUNDARY_MARGIN_MS = 2L

/**
 * Через сколько миллисекунд обновлять экран, чтобы цифра обратного отсчёта менялась
 * ровно на границах секунд до старта [nextStartTimeMs] — в те же моменты, что и
 * предстартовые сигналы сервиса соревнования.
 *
 * Границы отсчитываются от времени старта, а не от часов телефона: время старта
 * соревнования не обязательно кратно целой секунде.
 *
 * @param nowMs Текущее время (timestamp, мс).
 * @param nextStartTimeMs Ближайший предстоящий старт или `null`, если стартов впереди нет —
 *   тогда шаг выравнивается по секундам часов.
 */
fun millisUntilNextCountdownTick(nowMs: Long, nextStartTimeMs: Long?): Long {
    val anchor = nextStartTimeMs ?: 0L
    val untilBoundary = Math.floorMod(anchor - nowMs, 1000L).takeIf { it > 0L } ?: 1000L
    return untilBoundary + BOUNDARY_MARGIN_MS
}
