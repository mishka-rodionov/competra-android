package com.competra.center.data.draw

import com.competra.domain.models.orienteering.DrawMode
import com.competra.domain.models.orienteering.DrawSettings
import kotlin.math.roundToLong

/** Запас на подготовку опоздавшего (запись чипа, путь до старта) — раньше этого он не стартует. */
const val LATE_ENTRY_PREPARATION_MS = 2 * 60 * 1000L

/**
 * Куда поставить участника, дозаявленного после жеребьёвки.
 */
enum class LateEntryPlacement {
    /** Ближайшая минута, свободная по правилам проведённой жеребьёвки. */
    FREE_SLOT,

    /** Сразу после последнего старта протокола. */
    END,

    /** Время вводит организатор. */
    MANUAL
}

/**
 * Старт в протоколе, занимающий стартовую минуту.
 *
 * @property startTime Стартовое время участника (timestamp, мс).
 * @property groupId Группа участника.
 */
data class ProtocolStart(val startTime: Long, val groupId: Long)

/**
 * Подбор стартового времени для дозаявки после жеребьёвки.
 *
 * Протокол рассматривается как сетка минут с шагом [intervalMs], привязанная к самому раннему
 * старту (при старте соревнования все времена сдвигаются целиком, сетка сохраняется). Время
 * участника, введённое вручную не по сетке, относится к ближайшей минуте.
 *
 * Та же логика есть в веб-клиенте (competra-web-ts, `src/lib/lateEntry.ts`).
 *
 * @param starts Старты протокола (только участники с валидным временем).
 * @param groupDistanceMap Соответствие groupId → distanceId; без записи ключом дистанции служит groupId.
 * @param drawSettings Режим проведённой жеребьёвки; null — режим неизвестен (жеребьёвка старой версии).
 * @param intervalMs Стартовый интервал.
 * @param fallbackAnchor Начало сетки, если в протоколе ещё нет ни одного старта.
 * @param notBefore Раньше этого момента ставить нельзя (обычно «сейчас + [LATE_ENTRY_PREPARATION_MS]»).
 */
class LateEntrySlotFinder(
    starts: List<ProtocolStart>,
    private val groupDistanceMap: Map<Long, Long>,
    private val drawSettings: DrawSettings?,
    intervalMs: Long,
    fallbackAnchor: Long,
    notBefore: Long
) {
    private val intervalMs: Long = intervalMs.coerceAtLeast(1L)
    private val anchor: Long = starts.minOfOrNull { it.startTime } ?: fallbackAnchor
    private val slotStarts: Map<Long, List<ProtocolStart>> = starts.groupBy { slotOf(it.startTime) }
    private val firstAllowedSlot: Long =
        if (notBefore <= anchor) 0L else (notBefore - anchor + this.intervalMs - 1) / this.intervalMs

    /** Время ближайшей минуты, свободной для участника группы [groupId]. */
    fun freeSlot(groupId: Long): Long {
        var slot = firstAllowedSlot
        while (!isFree(slot, groupId)) slot++
        return timeOf(slot)
    }

    /** Время сразу после последнего старта протокола (но не раньше допустимого). */
    fun endOfProtocol(): Long {
        val lastSlot = slotStarts.keys.maxOrNull() ?: -1L
        return timeOf(maxOf(lastSlot + 1, firstAllowedSlot))
    }

    private fun isFree(slot: Long, groupId: Long): Boolean {
        val inSlot = slotStarts[slot].orEmpty()
        return when (drawSettings?.mode) {
            DrawMode.GENERAL -> inSlot.isEmpty()
            DrawMode.GROUP -> inSlot.none { it.groupId == groupId }
            DrawMode.DISTANCE -> isFreeByDistance(
                slot = slot,
                groupId = groupId,
                corridors = (drawSettings.corridors ?: 1).coerceAtLeast(1),
                gap = (drawSettings.gap ?: 1).coerceAtLeast(1)
            )
            // Режим неизвестен — осторожное правило: коридоров не больше, чем уже есть в протоколе,
            // и в одной минуте не больше одного участника дистанции.
            null -> isFreeByDistance(
                slot = slot,
                groupId = groupId,
                corridors = (slotStarts.values.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1),
                gap = 1
            )
        }
    }

    private fun isFreeByDistance(slot: Long, groupId: Long, corridors: Int, gap: Int): Boolean {
        if (slotStarts[slot].orEmpty().size >= corridors) return false
        val distance = distanceOf(groupId)
        // Участники той же дистанции не ближе gap минут — как в жеребьёвке по дистанциям.
        return (slot - gap + 1..slot + gap - 1).none { near ->
            slotStarts[near].orEmpty().any { distanceOf(it.groupId) == distance }
        }
    }

    private fun distanceOf(groupId: Long): Long = groupDistanceMap[groupId] ?: groupId

    private fun slotOf(time: Long): Long = ((time - anchor).toDouble() / intervalMs).roundToLong()

    private fun timeOf(slot: Long): Long = anchor + slot * intervalMs
}
