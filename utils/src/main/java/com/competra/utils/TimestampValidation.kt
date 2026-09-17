package com.competra.utils

/** Минимальный валидный timestamp (1 января 2000) — отсекает нулевые/неустановленные значения. */
const val MIN_VALID_TIMESTAMP_MS = 946_684_800_000L

/**
 * true, если время реально установлено (а не 0L/"не назначено").
 *
 * Используется для полей вроде [com.competra.domain.models.orienteering.OrienteeringParticipant.startTime],
 * где отсутствие значения кодируется как `0L`, а не `null`.
 */
fun Long.isValidStartTimestamp(): Boolean = this >= MIN_VALID_TIMESTAMP_MS
