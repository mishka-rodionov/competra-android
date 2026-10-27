package com.competra.domain.models.orienteering

import com.competra.domain.models.ParticipantGroup

/**
 * [ParticipantGroup.distanceId] группы, которой дистанция ещё не назначена. Сервер хранит такую
 * группу с `distanceId = null`; на границе с сетью 0 ↔ null (см. мапперы `:data:remote`).
 */
const val NO_DISTANCE_ID = 0L

/**
 * Назначена ли группе существующая дистанция. Группа без дистанции — нормальное состояние, пока
 * идёт регистрация (дистанции ещё не спланированы), но к старту дистанция нужна: без неё нельзя
 * провести жеребьёвку по дистанциям, прочитать чип и опубликовать результаты. Удалённую дистанцию
 * тоже считаем неназначенной — у группы остаётся висячий id.
 */
fun ParticipantGroup.hasAssignedDistance(distances: List<Distance>): Boolean =
    distanceId != NO_DISTANCE_ID && distances.any { it.id == distanceId }

/** Группы без назначенной дистанции — см. [hasAssignedDistance]. */
fun List<ParticipantGroup>.withoutDistance(distances: List<Distance>): List<ParticipantGroup> =
    filterNot { it.hasAssignedDistance(distances) }
