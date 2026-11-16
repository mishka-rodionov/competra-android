package com.competra.center

import com.competra.center.data.draw.LATE_ENTRY_PREPARATION_MS
import com.competra.center.data.draw.LateEntrySlotFinder
import com.competra.center.data.draw.ProtocolStart
import com.competra.center.data.draw.lateEntryNotBefore
import com.competra.domain.models.orienteering.DrawMode
import com.competra.domain.models.orienteering.DrawSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Подбор стартовой минуты для дозаявки после жеребьёвки [LateEntrySlotFinder].
 */
class LateEntrySlotFinderTest {

    private val anchor = 1_700_000_000_000L
    private val intervalMs = 60_000L

    private fun at(slot: Int) = anchor + slot * intervalMs

    /** Старты протокола: пары (минута, группа). */
    private fun starts(vararg slots: Pair<Int, Long>) = slots.map { (slot, group) -> ProtocolStart(at(slot), group) }

    private fun finder(
        starts: List<ProtocolStart>,
        settings: DrawSettings?,
        notBefore: Long = anchor,
        groupDistanceMap: Map<Long, Long> = emptyMap(),
        fallbackAnchor: Long = anchor
    ) = LateEntrySlotFinder(
        starts = starts,
        groupDistanceMap = groupDistanceMap,
        drawSettings = settings,
        intervalMs = intervalMs,
        fallbackAnchor = fallbackAnchor,
        notBefore = notBefore
    )

    @Test
    fun `general draw takes the first empty minute`() {
        val f = finder(starts(0 to 1L, 1 to 2L, 3 to 1L, 4 to 2L), DrawSettings(DrawMode.GENERAL))
        assertEquals(at(2), f.freeSlot(groupId = 1L))
    }

    @Test
    fun `general draw without gaps goes after the last start`() {
        val f = finder(starts(0 to 1L, 1 to 2L, 2 to 1L), DrawSettings(DrawMode.GENERAL))
        assertEquals(at(3), f.freeSlot(groupId = 1L))
    }

    @Test
    fun `minutes before notBefore are skipped and time is rounded up to the grid`() {
        val f = finder(
            starts(0 to 1L, 1 to 1L, 3 to 1L, 4 to 1L, 6 to 1L),
            DrawSettings(DrawMode.GENERAL),
            notBefore = anchor + 3 * intervalMs + 30_000L
        )
        // Минута 2 свободна, но уже прошла; 4 занята — первая свободная после 3:30 — минута 5.
        assertEquals(at(5), f.freeSlot(groupId = 1L))
    }

    @Test
    fun `group draw allows other groups in the same minute`() {
        val f = finder(starts(0 to 1L, 0 to 2L, 1 to 1L, 2 to 1L), DrawSettings(DrawMode.GROUP))
        assertEquals(at(1), f.freeSlot(groupId = 2L))
        assertEquals(at(3), f.freeSlot(groupId = 1L))
    }

    @Test
    fun `distance draw respects corridors and gap`() {
        val distances = mapOf(1L to 10L, 2L to 20L, 3L to 30L, 4L to 40L)
        val f = finder(
            starts(0 to 1L, 0 to 2L, 1 to 3L),
            DrawSettings(DrawMode.DISTANCE, corridors = 2, gap = 2),
            groupDistanceMap = distances
        )
        // Минута 0 заполнена (2 коридора), в минуте 1 дистанция 10 ближе зазора к минуте 0.
        assertEquals(at(2), f.freeSlot(groupId = 1L))
        // Новая дистанция встаёт во второй коридор минуты 1.
        assertEquals(at(1), f.freeSlot(groupId = 4L))
    }

    @Test
    fun `groups of one distance share the gap rule`() {
        val f = finder(
            starts(0 to 1L, 1 to 3L),
            DrawSettings(DrawMode.DISTANCE, corridors = 3, gap = 1),
            groupDistanceMap = mapOf(1L to 10L, 2L to 10L, 3L to 30L)
        )
        // Группа 2 бежит ту же дистанцию, что и группа 1, — в минуту 0 нельзя.
        assertEquals(at(1), f.freeSlot(groupId = 2L))
    }

    @Test
    fun `unknown draw mode limits corridors to the protocol maximum`() {
        val f = finder(starts(0 to 1L, 0 to 2L, 1 to 1L), settings = null)
        assertEquals(at(1), f.freeSlot(groupId = 2L))
        assertEquals(at(2), f.freeSlot(groupId = 1L))
        // Третьего коридора в протоколе не было — в минуту 0 новую группу не ставим.
        assertEquals(at(1), f.freeSlot(groupId = 3L))
    }

    @Test
    fun `end of protocol is after the last start but not before notBefore`() {
        val protocol = starts(0 to 1L, 1 to 2L, 2 to 1L)
        assertEquals(at(3), finder(protocol, DrawSettings(DrawMode.GENERAL)).endOfProtocol())
        assertEquals(
            at(10),
            finder(protocol, DrawSettings(DrawMode.GENERAL), notBefore = at(9) + 1).endOfProtocol()
        )
    }

    @Test
    fun `empty protocol starts from the fallback anchor`() {
        val f = finder(emptyList(), DrawSettings(DrawMode.GENERAL), fallbackAnchor = at(5))
        assertEquals(at(5), f.freeSlot(groupId = 1L))
        assertEquals(at(5), f.endOfProtocol())
    }

    @Test
    fun `manual off-grid start occupies the nearest minute`() {
        val protocol = listOf(ProtocolStart(at(0), 1L), ProtocolStart(at(1) + 10_000L, 2L))
        val f = finder(protocol, DrawSettings(DrawMode.GENERAL))
        assertEquals(at(2), f.freeSlot(groupId = 1L))
    }

    @Test
    fun `notBefore applies only after the competition has started`() {
        val now = at(40)
        assertEquals(now + LATE_ENTRY_PREPARATION_MS, lateEntryNotBefore(competitionStartTime = at(30), now = now))
        assertEquals(Long.MIN_VALUE, lateEntryNotBefore(competitionStartTime = null, now = now))
        assertEquals(Long.MIN_VALUE, lateEntryNotBefore(competitionStartTime = 0L, now = now))
    }

    @Test
    fun `late entry before the start goes right after the distance protocol even if planned time has passed`() {
        // Протокол привязан к плановому времени, которое уже прошло: дистанция 10 занимает минуты 0..24.
        val protocol = (0..24).map { ProtocolStart(at(it), 1L) }
        val f = finder(
            protocol,
            DrawSettings(DrawMode.DISTANCE, corridors = 4, gap = 1),
            notBefore = lateEntryNotBefore(competitionStartTime = null, now = at(44)),
            groupDistanceMap = mapOf(1L to 10L)
        )
        // Раньше ставили на минуту 46 (сейчас + 2 мин) — после старта оставалась дыра в 21 минуту.
        assertEquals(at(25), f.freeSlot(groupId = 1L))
        assertEquals(at(25), f.endOfProtocol())
    }
}
