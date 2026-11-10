package com.competra.domain.orienteering

import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.ControlPoint
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.NO_DISTANCE_ID
import com.competra.domain.models.orienteering.SplitTime
import com.competra.domain.models.orienteering.matchingGroupIdsByPunches
import org.junit.Assert.assertEquals
import org.junit.Test

/** Подсказка группы по отметкам чипа — [matchingGroupIdsByPunches]. */
class GroupMatchByPunchesTest {

    private fun distance(id: Long, vararg cps: Int) = Distance(
        id = id,
        competitionId = "c",
        lengthMeters = 0,
        climbMeters = 0,
        controlsCount = cps.size,
        controlPoints = cps.map { ControlPoint(number = it) },
        finishControlPoint = 245,
        startControlPoint = 240,
    )

    private fun group(id: Long, distanceId: Long) =
        ParticipantGroup(groupId = id, competitionId = "c", title = "G$id", gender = null, distanceId = distanceId)

    private fun punches(vararg cps: Int) = cps.mapIndexed { i, cp -> SplitTime(cp, 1_000L * (i + 1)) }

    private val short = distance(1, 31, 32)
    private val long = distance(2, 31, 32, 33, 34)
    private val groups = listOf(group(10, 1), group(20, 2), group(30, NO_DISTANCE_ID))

    @Test
    fun `all controls of the longest matching course win`() {
        val result = matchingGroupIdsByPunches(punches(240, 31, 32, 33, 34, 245), groups, listOf(short, long))
        assertEquals(setOf(20L), result)
    }

    @Test
    fun `order does not matter`() {
        val result = matchingGroupIdsByPunches(punches(240, 32, 31, 245), groups, listOf(short, long))
        assertEquals(setOf(10L), result)
    }

    @Test
    fun `missing control means no match`() {
        val result = matchingGroupIdsByPunches(punches(240, 31, 245), groups, listOf(short, long))
        assertEquals(emptySet<Long>(), result)
    }

    @Test
    fun `groups sharing a course both match`() {
        val result = matchingGroupIdsByPunches(
            punches(240, 31, 32, 245),
            groups + group(40, 1),
            listOf(short, long)
        )
        assertEquals(setOf(10L, 40L), result)
    }

    @Test
    fun `empty chip matches nothing`() {
        assertEquals(emptySet<Long>(), matchingGroupIdsByPunches(emptyList(), groups, listOf(short, long)))
    }
}
