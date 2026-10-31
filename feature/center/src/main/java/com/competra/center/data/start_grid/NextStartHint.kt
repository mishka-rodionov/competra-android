package com.competra.center.data.start_grid

import com.competra.domain.models.orienteering.PunchingSystem

/**
 * Номер участника в подсказке «Следующими стартуют».
 *
 * @property value Номер чипа или стартовый номер.
 * @property isChip true — [value] это номер чипа, false — стартовый номер.
 */
data class NextStartNumber(
    val value: String,
    val isChip: Boolean
)

/**
 * Минута, которая стартует следом за текущей (по реальному времени, а не по открытой
 * странице решётки). Null — если текущая минута последняя или все уже стартовали.
 *
 * @param currentSlotIndex Индекс ближайшей нестартовавшей минуты ([StartGridState.currentSlotIndex]).
 */
fun List<StartSlot>.nextStartSlot(currentSlotIndex: Int): StartSlot? =
    if (currentSlotIndex in indices) getOrNull(currentSlotIndex + 1) else null

/**
 * Номера участников минуты для подсказки организатору на старте.
 *
 * При отметке Sportiduino — номера чипов (участник без чипа — стартовым номером),
 * при остальных системах — стартовые номера. Порядок — как в минуте (по стартовому номеру).
 */
fun StartSlot.hintNumbers(punchingSystem: PunchingSystem?): List<NextStartNumber> =
    participants.map { participant ->
        val chip = participant.chipNumber.trim()
        if (punchingSystem == PunchingSystem.SPORTIDUINO && chip.isNotEmpty()) {
            NextStartNumber(value = chip, isChip = true)
        } else {
            NextStartNumber(value = participant.startNumber, isChip = false)
        }
    }
