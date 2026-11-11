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

/** Допуск на расхождение часов станции и телефона при поиске отметок «из будущего», мс. */
const val STATION_CLOCK_TOLERANCE_MS = 60_000L

/**
 * Номера КП, у отметок которых время позже момента чтения чипа больше чем на [toleranceMs]:
 * часы станции не синхронизированы. Sportiduino хранит на чипе только младшие 3 байта времени
 * отметки, а старший байт берётся из времени очистки чипа. Поэтому сброшенные или отстающие часы
 * станции дают отметку на дни или месяцы вперёд, и её время, сплиты и результат бессмысленны.
 * Отстающие часы, давшие время между очисткой и чтением, так не распознать.
 *
 * @param readAt момент чтения чипа (мс).
 * @return номера КП в порядке первых отметок на чипе, без повторов; пусто — подозрительных нет.
 */
fun unsyncedStationControlPoints(
    splits: List<SplitTime>,
    readAt: Long,
    toleranceMs: Long = STATION_CLOCK_TOLERANCE_MS,
): List<Int> =
    splits.filter { it.timestamp > readAt + toleranceMs }.map { it.controlPoint }.distinct()

/**
 * Время старта, от которого считаются сплиты участника: фактическое из результата
 * ([OrienteeringResult.startTime]), иначе — плановое из карточки участника. При старте по стартовой
 * станции фактический старт известен только из результата: в карточке участника его правят лишь на
 * судейском телефоне, а с сервера приходит пустое (0) — сплиты от него получились бы огромными.
 * Тот же анкер у таблицы сплитов группы (SplitsTableBuilder).
 */
fun raceStartTime(participant: OrienteeringParticipant, result: OrienteeringResult?): Long =
    result?.startTime?.takeIf { it > 0L } ?: participant.startTime

/** Участник с фактическим временем старта из [result] — для экранов сплитов (см. [raceStartTime]). */
fun OrienteeringParticipant.withRaceStart(result: OrienteeringResult?): OrienteeringParticipant =
    copy(startTime = raceStartTime(this, result))
