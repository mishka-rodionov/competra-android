package com.competra.domain.models.orienteering

import com.competra.domain.models.Gender
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.ResultStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Командный зачёт — [computeTeamStandings]; те же сценарии, что TeamStandingsTest в eSport. */
class TeamStandingsTest {

    private fun group(id: Long, title: String, gender: Gender?) =
        ParticipantGroup(groupId = id, competitionId = "c1", title = title, gender = gender, distanceId = 1L)

    private val m16 = group(1, "М16", Gender.MALE)
    private val m21 = group(2, "М21", Gender.MALE)
    private val w21 = group(3, "Ж21", Gender.FEMALE)

    private var nextId = 0

    private fun participant(group: ParticipantGroup, team: String?) = OrienteeringParticipant(
        id = "p${nextId++}", userId = "", firstName = "Имя", lastName = "Фамилия", groupId = group.groupId,
        groupName = group.title, competitionId = "c1", commandName = team.orEmpty(), startNumber = "1",
        startTime = 0L, chipNumber = "", comment = "", isChipGiven = false
    )

    private fun finished(group: ParticipantGroup, team: String?, rank: Int, minutes: Long): ParticipantWithResult {
        val participant = participant(group, team)
        return ParticipantWithResult(
            participant,
            OrienteeringResult(
                competitionId = "c1", groupId = group.groupId, participantId = participant.id,
                totalTime = minutes * 60, rank = rank, status = ResultStatus.FINISHED
            )
        )
    }

    private fun dsq(group: ParticipantGroup, team: String): ParticipantWithResult {
        val participant = participant(group, team)
        return ParticipantWithResult(
            participant,
            OrienteeringResult(competitionId = "c1", groupId = group.groupId, participantId = participant.id, status = ResultStatus.DSQ)
        )
    }

    private fun groups(vararg entries: ParticipantWithResult): List<GroupWithParticipantsAndResults> =
        listOf(m16, m21, w21).map { g -> GroupWithParticipantsAndResults(g, entries.filter { it.participant.groupId == g.groupId }) }

    private fun points(n: Int = 2, scopes: Set<TeamOverallScope> = emptySet()) = TeamScoring(TeamScoringMethod.POINTS, n, scopes)

    @Test
    fun `points - sum of N best place points, extra results not counted`() {
        val standings = computeTeamStandings(
            points(n = 2),
            groups(finished(m16, "Азимут", 1, 30), finished(m16, "Азимут", 3, 35), finished(m16, "Азимут", 5, 40), finished(m16, "Компас", 2, 32))
        )

        val teams = standings.groupStandings.single().teams
        assertEquals(listOf("Азимут" to 160, "Компас" to 80), teams.map { it.teamName to it.points })
        assertEquals(listOf(true, true, false), teams.first().members.map { it.counted })
    }

    @Test
    fun `team is the normalized caption, participants without caption do not count`() {
        val standings = computeTeamStandings(
            points(),
            groups(finished(m16, " азимут ", 1, 30), finished(m16, "АЗИМУТ", 2, 31), finished(m16, null, 3, 32))
        )

        assertEquals(listOf(180), standings.groupStandings.single().teams.map { it.points })
    }

    @Test
    fun `points - equal sums are split by the best result, fully equal share the place`() {
        val standings = computeTeamStandings(
            points(),
            groups(
                finished(m16, "А", 1, 30), finished(m16, "А", 40, 90),
                finished(m16, "Б", 2, 31), finished(m16, "Б", 20, 60),
                finished(m16, "В", 2, 31), finished(m16, "В", 20, 60),
            )
        )

        assertEquals(listOf("А" to 1, "Б" to 2, "В" to 2), standings.groupStandings.single().teams.map { it.teamName to it.place })
    }

    @Test
    fun `points - team without any place is out of the standing`() {
        val teams = computeTeamStandings(points(), groups(finished(m16, "А", 1, 30), dsq(m16, "Б"))).groupStandings.single().teams

        assertEquals("Б", teams.last().teamName)
        assertNull(teams.last().place)
    }

    @Test
    fun `time - sum of N best times, fewer finishers is out of the standing`() {
        val standings = computeTeamStandings(
            TeamScoring(TeamScoringMethod.TIME, 2),
            groups(
                finished(m21, "Азимут", 3, 50), finished(m21, "Азимут", 4, 55), finished(m21, "Азимут", 9, 70),
                finished(m21, "Компас", 1, 40), finished(m21, "Компас", 2, 45),
                finished(m21, "Ориент", 5, 60), dsq(m21, "Ориент"),
            )
        )

        val teams = standings.groupStandings.single().teams
        assertEquals(
            listOf(Triple("Компас", 1, 85L * 60), Triple("Азимут", 2, 105L * 60)),
            teams.take(2).map { Triple(it.teamName, it.place, it.timeSeconds) }
        )
        assertNull(teams.last().place)
        assertNull(teams.last().timeSeconds)
    }

    @Test
    fun `overall - sums points for team places in groups of the scope`() {
        val standings = computeTeamStandings(
            points(n = 1, scopes = TeamOverallScope.entries.toSet()),
            groups(
                finished(m16, "Азимут", 1, 30), finished(m16, "Компас", 2, 31),
                finished(m21, "Компас", 1, 40), finished(m21, "Азимут", 2, 41),
                finished(w21, "Азимут", 1, 50),
            )
        )

        val overall = standings.overallStandings.associateBy { it.scope }
        assertEquals(listOf(1, 1), overall.getValue(TeamOverallScope.MEN).teams.map { it.place })
        assertEquals(listOf("Азимут" to 100), overall.getValue(TeamOverallScope.WOMEN).teams.map { it.teamName to it.points })
        assertEquals(listOf("Азимут" to 280, "Компас" to 180), overall.getValue(TeamOverallScope.ALL).teams.map { it.teamName to it.points })
    }

    @Test
    fun `overall - team out of a time group earns nothing there`() {
        val standings = computeTeamStandings(
            TeamScoring(TeamScoringMethod.TIME, 2, setOf(TeamOverallScope.ALL)),
            groups(
                finished(m16, "Азимут", 1, 30), finished(m16, "Азимут", 2, 31),
                finished(m21, "Азимут", 1, 40),
                finished(m21, "Компас", 2, 41), finished(m21, "Компас", 3, 42),
            )
        )

        val all = standings.overallStandings.single()
        assertEquals(listOf("Азимут" to 100, "Компас" to 100), all.teams.map { it.teamName to it.points })
        assertEquals("М16", all.teams.first().groups.single().group.title)
    }

    @Test
    fun `group override of N takes precedence over the competition N`() {
        val standings = computeTeamStandings(
            points(n = 2),
            listOf(
                GroupWithParticipantsAndResults(
                    m16.copy(teamCountedResults = 1),
                    listOf(finished(m16, "Азимут", 1, 30), finished(m16, "Азимут", 2, 31))
                ),
                GroupWithParticipantsAndResults(m21, listOf(finished(m21, "Азимут", 1, 40), finished(m21, "Азимут", 2, 41))),
            )
        )

        assertEquals(listOf(1 to 100, 2 to 180), standings.groupStandings.map { it.countedResults to it.teams.single().points })
    }

    @Test
    fun `place points follow the rating table`() {
        assertEquals(listOf(100, 80, 60, 33, 31, 1, 0, 0), listOf(1, 2, 3, 9, 10, 40, 41, 0).map(::placePoints))
    }
}
