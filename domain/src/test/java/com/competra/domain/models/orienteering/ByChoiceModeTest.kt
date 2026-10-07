package com.competra.domain.models.orienteering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ByChoiceModeTest {

    @Test
    fun `score-O ranks by score`() {
        assertTrue(ranksByScore(OrienteeringDirection.BY_CHOICE, ByChoiceMode.SCORE))
    }

    @Test
    fun `choice with minimum controls ranks by time`() {
        assertFalse(ranksByScore(OrienteeringDirection.BY_CHOICE, ByChoiceMode.MIN_CONTROLS))
    }

    @Test
    fun `forward and marking rank by time whatever the mode`() {
        assertFalse(ranksByScore(OrienteeringDirection.FORWARD, ByChoiceMode.SCORE))
        assertFalse(ranksByScore(OrienteeringDirection.MARKING, ByChoiceMode.SCORE))
    }

    @Test
    fun `unknown mode falls back to score`() {
        assertEquals(ByChoiceMode.SCORE, ByChoiceMode.fromString("SOMETHING_ELSE"))
        assertEquals(ByChoiceMode.SCORE, ByChoiceMode.fromString(null))
    }
}
