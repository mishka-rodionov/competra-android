package com.competra.domain.models.orienteering

import com.competra.domain.models.ParticipantGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseFormatTest {

    private val forward = CourseFormat(OrienteeringDirection.FORWARD)

    private fun distance(id: Long, direction: OrienteeringDirection? = null, mode: ByChoiceMode? = null) = Distance(
        id = id,
        remoteId = id * 10,
        competitionId = "c",
        lengthMeters = 0,
        climbMeters = 0,
        controlsCount = 0,
        controlPoints = emptyList(),
        direction = direction,
        byChoiceMode = mode
    )

    private fun group(id: Long, distanceId: Long) = ParticipantGroup(
        groupId = id,
        competitionId = "c",
        title = "G$id",
        gender = null,
        distanceId = distanceId,
        remoteId = id * 100
    )

    @Test
    fun `distance without own format inherits competition`() {
        assertEquals(forward, distance(1).effectiveFormat(forward))
    }

    @Test
    fun `score distance in forward competition ranks by score`() {
        val format = distance(1, OrienteeringDirection.BY_CHOICE, ByChoiceMode.SCORE).effectiveFormat(forward)
        assertTrue(format.ranksByScore)
    }

    @Test
    fun `by choice distance without mode takes competition mode`() {
        val competition = CourseFormat(OrienteeringDirection.FORWARD, ByChoiceMode.MIN_CONTROLS)
        assertFalse(distance(1, OrienteeringDirection.BY_CHOICE).effectiveFormat(competition).ranksByScore)
    }

    @Test
    fun `group without distance gets competition format`() {
        val formats = GroupFormats.of(null, listOf(group(1, NO_DISTANCE_ID)), listOf(distance(1, OrienteeringDirection.MARKING)))
        assertEquals(CourseFormat.DEFAULT, formats.of(1))
    }

    @Test
    fun `remote formats are keyed by server ids`() {
        val formats = remoteGroupFormats(
            competition = null,
            groups = listOf(group(1, distanceId = 20)),
            distances = listOf(distance(2, OrienteeringDirection.BY_CHOICE, ByChoiceMode.SCORE))
        )
        assertTrue(formats.of(100).ranksByScore)
    }

    @Test
    fun `mixed format ignores mode of non choice formats`() {
        assertFalse(listOf(distance(1, OrienteeringDirection.FORWARD, ByChoiceMode.MIN_CONTROLS)).isMixedFormat(forward))
        assertTrue(listOf(distance(1, OrienteeringDirection.MARKING)).isMixedFormat(forward))
    }

    @Test
    fun `legacy score competition keeps penalty for score courses`() {
        val (time, score) = resolveOvertimePolicies(OvertimePolicy.SCORE_PENALTY, null, null, competitionRanksByScore = true)
        assertEquals(OvertimePolicy.IGNORE, time)
        assertEquals(OvertimePolicy.SCORE_PENALTY, score)
    }

    @Test
    fun `legacy time competition keeps its policy for time courses`() {
        val (time, score) = resolveOvertimePolicies(OvertimePolicy.DISQUALIFY, null, null, competitionRanksByScore = false)
        assertEquals(OvertimePolicy.DISQUALIFY, time)
        assertEquals(OvertimePolicy.SCORE_PENALTY, score)
    }

    @Test
    fun `score penalty is not applied to time courses`() {
        val (time, _) = resolveOvertimePolicies(null, OvertimePolicy.SCORE_PENALTY, null, competitionRanksByScore = false)
        assertEquals(OvertimePolicy.IGNORE, time)
    }

    @Test
    fun `score group without own control time needs it when competition has none`() {
        val groups = listOf(group(1, distanceId = 1), group(2, distanceId = 2).copy(timeLimitMinutes = 60))
        val distances = listOf(
            distance(1, OrienteeringDirection.BY_CHOICE, ByChoiceMode.SCORE),
            distance(2, OrienteeringDirection.BY_CHOICE, ByChoiceMode.SCORE)
        )
        val missing = groups.withoutRequiredControlTime(distances, forward, OvertimePolicy.SCORE_PENALTY, null)
        assertEquals(listOf(1L), missing.map { it.groupId })
    }

    @Test
    fun `control time is not required with competition control time or without penalty`() {
        val groups = listOf(group(1, distanceId = 1))
        val distances = listOf(distance(1, OrienteeringDirection.BY_CHOICE, ByChoiceMode.SCORE))
        assertTrue(groups.withoutRequiredControlTime(distances, forward, OvertimePolicy.SCORE_PENALTY, 90).isEmpty())
        assertTrue(groups.withoutRequiredControlTime(distances, forward, OvertimePolicy.IGNORE, null).isEmpty())
    }

    @Test
    fun `time course group does not need control time`() {
        val groups = listOf(group(1, distanceId = 1))
        val distances = listOf(distance(1, OrienteeringDirection.FORWARD))
        assertTrue(groups.withoutRequiredControlTime(distances, forward, OvertimePolicy.SCORE_PENALTY, null).isEmpty())
    }
}
