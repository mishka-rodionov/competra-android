package com.competra.center

import com.competra.center.data.start_grid.NextStartNumber
import com.competra.center.data.start_grid.StartSlot
import com.competra.center.data.start_grid.hintNumbers
import com.competra.center.data.start_grid.nextStartSlot
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.PunchingSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Подсказка «Следующими стартуют» на стартовой решётке: выбор минуты и номеров.
 */
class NextStartHintTest {

    private fun participant(startNumber: String, chipNumber: String = "") = OrienteeringParticipant(
        id = startNumber,
        userId = "",
        firstName = "",
        lastName = "",
        groupId = 1L,
        groupName = "",
        competitionId = "c",
        commandName = "",
        startNumber = startNumber,
        startTime = 0L,
        chipNumber = chipNumber,
        comment = "",
        isChipGiven = false
    )

    private val slots = listOf(
        StartSlot(startTime = 1_000L, participants = listOf(participant("1"))),
        StartSlot(startTime = 2_000L, participants = listOf(participant("2"))),
        StartSlot(startTime = 3_000L, participants = listOf(participant("3")))
    )

    @Test
    fun `next slot follows the current one`() {
        assertEquals(slots[1], slots.nextStartSlot(currentSlotIndex = 0))
        assertEquals(slots[2], slots.nextStartSlot(currentSlotIndex = 1))
    }

    @Test
    fun `no next slot after the last minute`() {
        assertNull(slots.nextStartSlot(currentSlotIndex = 2))
        assertNull(emptyList<StartSlot>().nextStartSlot(currentSlotIndex = 0))
    }

    @Test
    fun `sportiduino shows chip numbers and falls back to start number`() {
        val slot = StartSlot(
            startTime = 1_000L,
            participants = listOf(participant("12", chipNumber = "1042"), participant("15"))
        )
        assertEquals(
            listOf(NextStartNumber("1042", isChip = true), NextStartNumber("15", isChip = false)),
            slot.hintNumbers(PunchingSystem.SPORTIDUINO)
        )
    }

    @Test
    fun `other punching systems show start numbers`() {
        val slot = StartSlot(
            startTime = 1_000L,
            participants = listOf(participant("12", chipNumber = "1042"), participant("15"))
        )
        assertEquals(
            listOf(NextStartNumber("12", isChip = false), NextStartNumber("15", isChip = false)),
            slot.hintNumbers(PunchingSystem.SPORTIDENT)
        )
    }
}
