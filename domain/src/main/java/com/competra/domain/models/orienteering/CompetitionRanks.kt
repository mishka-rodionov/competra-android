package com.competra.domain.models.orienteering

/**
 * Места по уже отсортированным ключам результатов (лучший — первым) по правилам соревнований:
 * равные ключи делят место, следующий за ними получает место по числу участников выше него
 * (1, 1, 3, а не 1, 1, 2).
 *
 * Порт серверного `competitionRanks` (eSport, `domain/orienteering/CompetitionRanks.kt`) —
 * меняешь здесь, поменяй и там.
 *
 * @param sortedKeys ключи сравнения результатов в порядке от лучшего к худшему.
 * @return место для каждого ключа в том же порядке.
 */
fun <K> competitionRanks(sortedKeys: List<K>): List<Int> {
    var rank = 0
    return sortedKeys.mapIndexed { index, key ->
        if (index == 0 || key != sortedKeys[index - 1]) rank = index + 1
        rank
    }
}
