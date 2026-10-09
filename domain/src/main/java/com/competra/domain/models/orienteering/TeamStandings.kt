package com.competra.domain.models.orienteering

import com.competra.domain.models.Gender
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.cyclic_event.normalizeCommandName

/** Командный зачёт соревнования: в каждой группе и общие. */
data class TeamStandings(
    val groupMethod: TeamScoringMethod,
    val groupCountedResults: Int,
    /** В порядке групп соревнования; группы без команд не выводятся. */
    val groupStandings: List<GroupTeamStanding>,
    /** В порядке MEN, WOMEN, ALL; без команд не выводятся. */
    val overallStandings: List<OverallTeamStanding>
)

/** @property countedResults N этой группы — своё ([ParticipantGroup.teamCountedResults]) или соревнования. */
data class GroupTeamStanding(
    val group: ParticipantGroup,
    val countedResults: Int,
    val teams: List<GroupTeam>
)

/**
 * @property place место команды в группе; null — вне зачёта.
 * @property points сумма очков (POINTS).
 * @property timeSeconds сумма времени в секундах (TIME); null — вне зачёта.
 * @property members участники команды с результатом в группе — вошедшие в зачёт первыми.
 */
data class GroupTeam(
    val place: Int?,
    val teamName: String,
    val points: Int?,
    val timeSeconds: Long?,
    val members: List<TeamMemberResult>
)

/**
 * @property place место в группе (только у FINISHED).
 * @property timeSeconds время с учётом штрафа (только у FINISHED).
 * @property counted результат вошёл в зачёт команды.
 */
data class TeamMemberResult(
    val participant: OrienteeringParticipant,
    val status: ResultStatus,
    val place: Int?,
    val points: Int,
    val timeSeconds: Long?,
    val counted: Boolean
)

data class OverallTeamStanding(
    val scope: TeamOverallScope,
    val teams: List<OverallTeam>
)

/** @property groups группы, где команда заняла место, — по убыванию баллов. */
data class OverallTeam(
    val place: Int,
    val teamName: String,
    val points: Int,
    val groups: List<OverallTeamGroup>
)

data class OverallTeamGroup(
    val group: ParticipantGroup,
    val place: Int,
    val points: Int
)

/**
 * Командный зачёт (docs/specs/team-scoring.md) — та же логика, что computeTeamStandings в eSport
 * (data/services/TeamStandings.kt). Чистая функция над результатами: зачёт не хранится и
 * пересчитывается при каждом показе — в Центре офлайн из локальной БД, в деталях события — из
 * загруженных с сервера результатов.
 *
 * - Команда — подпись участника без учёта регистра и пробелов ([normalizeCommandName]); без подписи — не участвует.
 * - В группе: POINTS — сумма очков за места ([placePoints]) N лучших; TIME — сумма времени N лучших,
 *   финишировавших меньше N — вне зачёта. N — своё у группы ([ParticipantGroup.teamCountedResults]), иначе у соревнования.
 * - Общие зачёты: сумма баллов за командные места в группах по той же таблице.
 *
 * @param groups группы соревнования с участниками и результатами — в порядке вывода.
 */
fun computeTeamStandings(settings: TeamScoring, groups: List<GroupWithParticipantsAndResults>): TeamStandings {
    val counted = settings.groupCountedResults.coerceAtLeast(1)

    val groupStandings = groups.mapNotNull { group ->
        val entries = group.participants.filter { pw ->
            pw.result != null && normalizeCommandName(pw.participant.commandName) != null
        }
        if (entries.isEmpty()) return@mapNotNull null
        val groupCounted = group.group.teamCountedResults?.takeIf { it > 0 } ?: counted
        val teams = when (settings.groupMethod) {
            TeamScoringMethod.POINTS -> pointsStanding(entries, groupCounted)
            TeamScoringMethod.TIME -> timeStanding(entries, groupCounted)
        }
        GroupTeamStanding(group.group, groupCounted, teams)
    }

    val overallStandings = TeamOverallScope.entries
        .filter { it in settings.overallScopes }
        .mapNotNull { scope ->
            val teams = overallStanding(groupStandings.filter { it.group.belongsTo(scope) })
            teams.takeIf { it.isNotEmpty() }?.let { OverallTeamStanding(scope, it) }
        }

    return TeamStandings(settings.groupMethod, counted, groupStandings, overallStandings)
}

private fun ParticipantWithResult.place(): Int? {
    val result = result ?: return null
    return result.rank?.takeIf { result.status == ResultStatus.FINISHED && it > 0 }
}

private fun ParticipantWithResult.points(): Int = place()?.let(::placePoints) ?: 0

private fun ParticipantWithResult.raceSeconds(): Long? {
    val result = result ?: return null
    return result.totalTime?.takeIf { result.status == ResultStatus.FINISHED }?.let { it + result.penaltyTime }
}

private fun ParticipantWithResult.teamKey(): String = normalizeCommandName(participant.commandName)!!.lowercase()

private fun ParticipantWithResult.toMember(isCounted: Boolean) = TeamMemberResult(
    participant = participant,
    status = result!!.status,
    place = place(),
    points = points(),
    timeSeconds = raceSeconds(),
    counted = isCounted
)

private fun ParticipantGroup.belongsTo(scope: TeamOverallScope): Boolean = when (scope) {
    TeamOverallScope.ALL -> true
    TeamOverallScope.MEN -> gender == Gender.MALE
    TeamOverallScope.WOMEN -> gender == Gender.FEMALE
}

/** Подпись команды для вывода — как у лучшего участника, без лишних пробелов. */
private fun List<ParticipantWithResult>.teamName(): String = normalizeCommandName(first().participant.commandName)!!

/** Очки за места: N лучших по очкам, сумма. Команда без единого места — вне зачёта. */
private fun pointsStanding(entries: List<ParticipantWithResult>, counted: Int): List<GroupTeam> {
    data class Scored(val name: String, val takenPoints: List<Int>, val members: List<TeamMemberResult>)

    val scored = entries.groupBy { it.teamKey() }.values.map { teamEntries ->
        val ordered = teamEntries.sortedWith(
            compareByDescending<ParticipantWithResult> { it.points() }.thenBy { it.place() ?: Int.MAX_VALUE }
        )
        val taken = ordered.filter { it.place() != null }.take(counted)
        val members = taken.map { it.toMember(true) } + (ordered - taken.toSet()).map { it.toMember(false) }
        Scored(ordered.teamName(), taken.map { it.points() }, members)
    }
    val (ranked, outside) = scored.partition { it.takenPoints.isNotEmpty() }
    return rankDescending(ranked) { it.takenPoints }.map { (place, team) ->
        GroupTeam(place, team.name, team.takenPoints.sum(), null, team.members)
    } + outside.sortedBy { it.name.lowercase() }.map { GroupTeam(null, it.name, 0, null, it.members) }
}

/** Сумма времени: N лучших финишировавших; меньше N — вне зачёта. */
private fun timeStanding(entries: List<ParticipantWithResult>, counted: Int): List<GroupTeam> {
    data class Timed(val name: String, val totalSeconds: Long?, val members: List<TeamMemberResult>)

    val timed = entries.groupBy { it.teamKey() }.values.map { teamEntries ->
        val ordered = teamEntries.sortedBy { it.raceSeconds() ?: Long.MAX_VALUE }
        val finished = ordered.filter { it.raceSeconds() != null }
        val taken = if (finished.size >= counted) finished.take(counted) else emptyList()
        val members = taken.map { it.toMember(true) } + (ordered - taken.toSet()).map { it.toMember(false) }
        Timed(ordered.teamName(), taken.takeIf { it.isNotEmpty() }?.sumOf { it.raceSeconds()!! }, members)
    }
    val (ranked, outside) = timed.partition { it.totalSeconds != null }
    var place = 0
    var prevTotal: Long? = null
    val placed = ranked.sortedBy { it.totalSeconds }.mapIndexed { index, team ->
        if (team.totalSeconds != prevTotal) {
            place = index + 1
            prevTotal = team.totalSeconds
        }
        GroupTeam(place, team.name, null, team.totalSeconds, team.members)
    }
    return placed + outside.sortedBy { it.name.lowercase() }.map { GroupTeam(null, it.name, null, null, it.members) }
}

/** Общий зачёт: сумма баллов за командные места в группах (поделённое место — одинаковые баллы). */
private fun overallStanding(groupStandings: List<GroupTeamStanding>): List<OverallTeam> {
    data class Earned(val name: String, val groups: List<OverallTeamGroup>)

    val byTeam = linkedMapOf<String, Earned>()
    groupStandings.forEach { standing ->
        standing.teams.forEach { team ->
            val place = team.place ?: return@forEach
            val key = team.teamName.lowercase()
            val earned = OverallTeamGroup(standing.group, place, placePoints(place))
            val current = byTeam[key]
            byTeam[key] = Earned(current?.name ?: team.teamName, current?.groups.orEmpty() + earned)
        }
    }
    val teams = byTeam.values.map { it.copy(groups = it.groups.sortedByDescending { g -> g.points }) }
    return rankDescending(teams) { team -> team.groups.map { it.points } }.map { (place, team) ->
        OverallTeam(place, team.name, team.groups.sumOf { it.points }, team.groups)
    }
}

/**
 * Места по сумме убыв.; при равенстве — по слагаемым, сравниваемым по убыванию (у кого лучшее
 * слагаемое больше, затем второе…); полностью равные — делят место.
 */
private fun <T> rankDescending(items: List<T>, parts: (T) -> List<Int>): List<Pair<Int, T>> {
    val comparator = Comparator<List<Int>> { a, b ->
        val bySum = b.sum().compareTo(a.sum())
        if (bySum != 0) return@Comparator bySum
        val sortedA = a.sortedDescending()
        val sortedB = b.sortedDescending()
        for (i in 0 until maxOf(sortedA.size, sortedB.size)) {
            val cmp = (sortedB.getOrNull(i) ?: 0).compareTo(sortedA.getOrNull(i) ?: 0)
            if (cmp != 0) return@Comparator cmp
        }
        0
    }
    val sorted = items.sortedWith { x, y -> comparator.compare(parts(x), parts(y)) }
    var place = 0
    return sorted.mapIndexed { index, item ->
        if (index == 0 || comparator.compare(parts(sorted[index - 1]), parts(item)) != 0) place = index + 1
        place to item
    }
}
