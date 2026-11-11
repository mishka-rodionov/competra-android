package com.competra.center

import com.competra.center.data.creator.DistanceFormatOption
import com.competra.domain.models.orienteering.ByChoiceMode
import com.competra.domain.models.orienteering.CourseFormat
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.OrienteeringDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceFormatOptionTest {

    private val scoreCompetition = CourseFormat(OrienteeringDirection.BY_CHOICE, ByChoiceMode.SCORE)

    private fun distance(direction: OrienteeringDirection?, mode: ByChoiceMode? = null) = Distance(
        competitionId = "c",
        lengthMeters = 0,
        climbMeters = 0,
        controlsCount = 0,
        controlPoints = emptyList(),
        direction = direction,
        byChoiceMode = mode
    )

    @Test
    fun `new distance gets competition format`() {
        assertEquals(DistanceFormatOption.BY_CHOICE_SCORE, DistanceFormatOption.of(null, scoreCompetition))
    }

    @Test
    fun `inherited distance shows competition format`() {
        assertEquals(DistanceFormatOption.BY_CHOICE_SCORE, DistanceFormatOption.of(distance(null), scoreCompetition))
    }

    @Test
    fun `option equal to competition format is saved as inherited`() {
        assertEquals(null to null, DistanceFormatOption.BY_CHOICE_SCORE.toDistanceFields(scoreCompetition))
    }

    @Test
    fun `other option is saved explicitly`() {
        assertEquals(
            OrienteeringDirection.FORWARD to null,
            DistanceFormatOption.FORWARD.toDistanceFields(scoreCompetition)
        )
        assertEquals(
            OrienteeringDirection.BY_CHOICE to ByChoiceMode.MIN_CONTROLS,
            DistanceFormatOption.BY_CHOICE_MIN_CONTROLS.toDistanceFields(scoreCompetition)
        )
    }

    @Test
    fun `marking is offered only when already selected`() {
        assertFalse(DistanceFormatOption.MARKING in DistanceFormatOption.selectable(DistanceFormatOption.FORWARD))
        assertTrue(DistanceFormatOption.MARKING in DistanceFormatOption.selectable(DistanceFormatOption.MARKING))
    }
}
