package com.competra.domain.orienteering

import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.ParticipantHistory
import com.competra.domain.models.orienteering.PastParticipation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticipantHistoryTest {

    private fun past(
        first: String,
        last: String,
        date: Long,
        userId: String = "",
        command: String = "",
        group: String = "М21",
        id: String = "$last-$first-$date"
    ) = PastParticipation(id, "comp-$date", userId, first, last, group, command, date)

    private fun protocol(first: String, last: String, userId: String = "") = OrienteeringParticipant(
        id = "$first$last", userId = userId, firstName = first, lastName = last, groupId = 1,
        groupName = "М21", competitionId = "c", commandName = "", startNumber = "1", startTime = 0,
        chipNumber = "", comment = "", isChipGiven = false
    )

    @Test
    fun `manual entries are merged by name ignoring case and yo`() {
        val result = ParticipantHistory.aggregate(
            listOf(
                past("Пётр", "Иванов", date = 1, command = "Старая"),
                past("петр ", "ИВАНОВ", date = 2, command = "Азимут")
            )
        )
        assertEquals(1, result.size)
        assertEquals(2, result.single().participationsCount)
        assertEquals("Азимут", result.single().commandName)
        assertEquals(2L, result.single().lastCompetitionDate)
    }

    @Test
    fun `manual entry joins the only registered user with the same name`() {
        val result = ParticipantHistory.aggregate(
            listOf(past("Пётр", "Иванов", date = 1), past("Пётр", "Иванов", date = 2, userId = "u1"))
        )
        assertEquals(1, result.size)
        assertEquals("u1", result.single().userId)
        assertEquals(2, result.single().participationsCount)
    }

    @Test
    fun `namesakes with different users stay separate`() {
        val result = ParticipantHistory.aggregate(
            listOf(
                past("Пётр", "Иванов", date = 1, userId = "u1"),
                past("Пётр", "Иванов", date = 2, userId = "u2"),
                past("Пётр", "Иванов", date = 3)
            )
        )
        assertEquals(3, result.size)
    }

    @Test
    fun `frequent participants come first, then recent`() {
        val result = ParticipantHistory.aggregate(
            listOf(
                past("Анна", "Петрова", date = 10),
                past("Иван", "Сидоров", date = 1),
                past("Иван", "Сидоров", date = 2),
                past("Олег", "Котов", date = 20)
            )
        )
        assertEquals(listOf("Сидоров", "Котов", "Петрова"), result.map { it.lastName })
    }

    @Test
    fun `search matches prefixes of first and last name`() {
        val history = ParticipantHistory.aggregate(
            listOf(past("Пётр", "Иванов", date = 1), past("Иван", "Петров", date = 2))
        )
        assertEquals(listOf("Иванов", "Петров"), ParticipantHistory.search(history, "ив").map { it.lastName }.sorted())
        // «ив» — начало и фамилии «Иванов», и имени «Иван»: подходят оба.
        assertEquals(listOf("Иванов", "Петров"), ParticipantHistory.search(history, "Ив петр").map { it.lastName }.sorted())
        assertEquals(listOf("Иванов"), ParticipantHistory.search(history, "Иванов петр").map { it.lastName })
        assertEquals(listOf("Иванов"), ParticipantHistory.search(history, "иванов ").map { it.lastName })
        assertTrue(ParticipantHistory.search(history, "и").isEmpty())
        assertTrue(ParticipantHistory.search(history, "  ").isEmpty())
    }

    @Test
    fun `filter returns whole history for empty query and has no limit`() {
        val history = ParticipantHistory.aggregate((1..8).map { past("Имя$it", "Иванов$it", date = it.toLong()) })
        assertEquals(8, ParticipantHistory.filter(history, " ").size)
        assertEquals(8, ParticipantHistory.filter(history, "ив").size)
        assertEquals(listOf("Иванов3"), ParticipantHistory.filter(history, "иванов3").map { it.lastName })
    }

    @Test
    fun `current group goes first, order inside parts is kept`() {
        val history = ParticipantHistory.aggregate(
            listOf(
                past("Анна", "Петрова", date = 1, group = "Ж21"),
                past("Анна", "Петрова", date = 2, group = "Ж21"),
                past("Иван", "Сидоров", date = 3, group = "M21"), // латинская M
                past("Олег", "Котов", date = 4, group = "М35"),
                past("Пётр", "Иванов", date = 5, group = "м 21")
            )
        )
        assertEquals(
            listOf("Иванов", "Сидоров", "Петрова", "Котов"),
            ParticipantHistory.prioritizeGroup(history, "М21").map { it.lastName }
        )
        assertEquals(history, ParticipantHistory.prioritizeGroup(history, " "))
    }

    @Test
    fun `key survives adding server history`() {
        val localOnly = ParticipantHistory.aggregate(listOf(past("Пётр", "Иванов", date = 1, id = "p1")))
        val merged = ParticipantHistory.aggregate(
            ParticipantHistory.merge(
                listOf(past("Пётр", "Иванов", date = 1, id = "p1")),
                listOf(past("Петр", "Иванов", date = 5, id = "p2"))
            )
        )
        assertEquals(ParticipantHistory.keyOf(localOnly.single()), ParticipantHistory.keyOf(merged.single()))
        assertEquals("u1", ParticipantHistory.keyOf(localOnly.single().copy(userId = "u1")))
    }

    @Test
    fun `merge prefers local record and adds server-only participations`() {
        val local = listOf(past("Пётр", "Иванов", date = 1, id = "p1", command = "Новая"))
        val server = listOf(
            past("Пётр", "Иванов", date = 1, id = "p1", command = "Старая"),
            past("Анна", "Петрова", date = 2, id = "p2")
        )
        val merged = ParticipantHistory.merge(local, server)
        assertEquals(listOf("p1", "p2"), merged.map { it.id })
        assertEquals("Новая", merged.first().commandName)
    }

    @Test
    fun `participants already in protocol are excluded`() {
        val history = ParticipantHistory.aggregate(
            listOf(
                past("Пётр", "Иванов", date = 1),
                past("Анна", "Петрова", date = 2, userId = "u2"),
                past("Олег", "Котов", date = 3)
            )
        )
        val left = ParticipantHistory.excludeProtocol(
            history,
            listOf(protocol("петр", "Иванов"), protocol("Аня", "Петрова", userId = "u2"))
        )
        assertEquals(listOf("Котов"), left.map { it.lastName })
    }
}
