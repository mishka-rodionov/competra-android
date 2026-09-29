package com.competra.domain.models.orienteering

/**
 * Представляет отметку участника на контрольном пункте дистанции.
 *
 * @property controlPoint идентификатор или название контрольного пункта.
 * @property timestamp время отметки в миллисекундах с начала эпохи Unix (UTC).
 */
data class SplitTime(
    val controlPoint: Int,
    val timestamp: Long
)

/**
 * Отметки чипа, сделанные не раньше старта. Отметки до старта бывают, когда участник в стартовом
 * городке отмечает финишную (или любую другую) станцию — в результат они не входят, как и в
 * стандартных программах обработки отметок: иначе такая отметка засчитывалась бы за КП дистанции,
 * первый перегон получался отрицательным, а остальные сплиты сдвигались относительно колонок КП.
 */
fun punchesAfterStart(splits: List<SplitTime>, startTime: Long): List<SplitTime> =
    splits.filter { it.timestamp >= startTime }
