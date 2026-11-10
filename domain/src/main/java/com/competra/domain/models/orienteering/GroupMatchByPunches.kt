package com.competra.domain.models.orienteering

import com.competra.domain.models.ParticipantGroup

/**
 * Группы, дистанцию которых участник прошёл по отметкам чипа, — подсказка организатору в режиме
 * «Регистрация на финише» ([OrienteeringCompetition.isFinishRegistration]), к какой группе
 * относится незнакомый чип.
 *
 * Группа «подходит», если у неё есть дистанция с КП и все эти КП есть среди отметок чипа
 * (порядок не проверяется — это только подсказка, результат считается обычной проверкой).
 * Если подходят несколько групп, остаются те, чья дистанция длиннее всех по числу КП: короткая
 * дистанция часто целиком входит в длинную, и тогда отметки говорят именно о длинной.
 * Группы без дистанции не подходят никогда — по отметкам о них ничего не сказать.
 *
 * @param splits Отметки чипа.
 * @param groups Группы соревнования.
 * @param distances Дистанции соревнования.
 * @return id подходящих групп; пусто — подсказать нечего.
 */
fun matchingGroupIdsByPunches(
    splits: List<SplitTime>,
    groups: List<ParticipantGroup>,
    distances: List<Distance>,
): Set<Long> {
    if (splits.isEmpty()) return emptySet()
    val punched = splits.map { it.controlPoint }.toSet()
    val distanceById = distances.filterNot { it.isDeleted }.associateBy { it.id }
    val matches = groups
        .filterNot { it.isDeleted || it.distanceId == NO_DISTANCE_ID }
        .mapNotNull { group ->
            val cps = distanceById[group.distanceId]?.controlPoints?.map { it.number }?.toSet()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (punched.containsAll(cps)) group.groupId to cps.size else null
        }
    val longest = matches.maxOfOrNull { it.second } ?: return emptySet()
    return matches.filter { it.second == longest }.map { it.first }.toSet()
}
