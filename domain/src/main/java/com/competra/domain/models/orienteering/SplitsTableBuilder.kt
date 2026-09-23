package com.competra.domain.models.orienteering

import com.competra.domain.models.ResultStatus
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Колонка таблицы сплитов — один контрольный пункт по позиции в дистанции. */
data class SplitsTableColumn(
    val positionIndex: Int,
    val controlPoint: Int,
)

/** Ячейка таблицы сплитов для одного участника на одном КП. */
data class SplitsTableCell(
    val deltaSeconds: Long?,
    val cumulativeSeconds: Long?,
    val deltaRank: Int?,
    val cumulativeRank: Int?,
    val isBestLeg: Boolean,
    val paceMinPerKm: Double? = null,
    /** Номер КП, реально взятого участником на этой позиции (BY_CHOICE — у каждого свой порядок).
     * Null для FORWARD/MARKING, где КП колонки общий для всех (см. [SplitsTableColumn.controlPoint]). */
    val controlPoint: Int? = null,
)

private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Расстояние между двумя контрольными пунктами по их WGS84-координатам, в метрах.
 * Null, если у одного из КП нет координат (дистанция не импортирована из геопривязанной карты
 * или создана вручную). Переиспользуется как таблицей сплитов, так и экраном сканирования чипа
 * (feature:center) — единая формула для обоих мест отображения темпа.
 */
fun controlPointDistanceMeters(from: ControlPoint?, to: ControlPoint?): Double? {
    val lat1 = from?.latitude ?: return null
    val lon1 = from.longitude ?: return null
    val lat2 = to?.latitude ?: return null
    val lon2 = to?.longitude ?: return null
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** Темп участника на перегоне в минутах на километр, либо null, если длина перегона неизвестна/нулевая. */
fun paceMinPerKm(deltaSeconds: Long, legLengthMeters: Double?): Double? {
    if (legLengthMeters == null || legLengthMeters <= 0) return null
    return (deltaSeconds / 60.0) / (legLengthMeters / 1000.0)
}

/**
 * Длина перегона (в метрах) для каждой позиции в [cpOrder] дистанции [distance]: null для первой
 * позиции (нет координаты старта до первого КП) и там, где у одного из соседних КП нет координат.
 */
private fun legLengthsMeters(distance: Distance?, cpOrder: List<Int>): List<Double?> {
    val expected = distance?.expectedSequence() ?: return List(cpOrder.size) { null }
    return cpOrder.indices.map { i ->
        if (i == 0) return@map null
        controlPointDistanceMeters(expected.getOrNull(i - 1), expected.getOrNull(i))
    }
}

/** Строка таблицы сплитов — один участник группы. */
data class SplitsTableRow(
    val participant: OrienteeringParticipant,
    val result: OrienteeringResult?,
    val cells: List<SplitsTableCell>,
    /** Сырые очки за фактически взятые КП (BY_CHOICE) — сумма по дистанции, ДО вычета штрафа.
     * Null для FORWARD/MARKING. Считается один раз здесь, чтобы UI не знал про Distance/ControlPoint. */
    val rawScore: Int? = null,
    /** Дистанция, пройденная участником (BY_CHOICE), в метрах — сумма расстояний между
     * последовательно взятыми КП по их координатам. Null для FORWARD/MARKING, когда у дистанции
     * нет координат КП, и когда сумма получилась нулевой (ни одного перегона с известными
     * координатами). Первый взятый КП в сумму не входит — координата точки старта неизвестна. */
    val totalDistanceMeters: Double? = null,
)

data class SplitsTable(
    val columns: List<SplitsTableColumn>,
    val rows: List<SplitsTableRow>,
)

/**
 * Анкер отсчёта сплитов: фактическое время старта из результата, а если оно потеряно —
 * плановое время старта участника. Тот же анкер использует реконструкция сплитов при
 * HTML-импорте (см. `buildResultsDiff`), поэтому здесь нельзя молча падать на 0 —
 * это рассинхронит отображаемые времена с тем, что реально записано в SplitTime.timestamp.
 */
private fun anchorStartTime(pw: ParticipantWithResult): Long =
    pw.result?.startTime ?: pw.participant.startTime

/**
 * Сырые очки участника за фактически взятые КП (BY_CHOICE), ДО вычета штрафа — сумма
 * [ControlPoint.score] по номерам КП из [OrienteeringResult.splits]. [OrienteeringResult.totalScore]
 * хранится уже за вычетом штрафа, а при обнулении результата (сильное опоздание) totalScore+scorePenalty
 * не равен фактически заработанным очкам — поэтому считаем от дистанции, как и HTML-экспорт результатов.
 * Фолбэк на totalScore+scorePenalty, если карта очков КП дистанции недоступна.
 */
private fun rawByChoiceScore(result: OrienteeringResult?, scoreByNumber: Map<Int, Int>): Int? {
    val netScore = result?.totalScore ?: return null
    return if (scoreByNumber.isNotEmpty()) {
        result.splits?.sumOf { scoreByNumber[it.controlPoint] ?: 0 } ?: (netScore + result.scorePenalty)
    } else {
        netScore + result.scorePenalty
    }
}

/**
 * Дистанция, пройденная участником (BY_CHOICE), в метрах — сумма расстояний между
 * последовательно взятыми КП по их координатам ([controlPointByNumber]). Первый взятый КП не
 * учитывается — координата точки старта неизвестна. Перегоны с неизвестными координатами (нет
 * хотя бы одной из точек) в сумму не входят — итог остаётся приблизительным, а не null, чтобы
 * частичное отсутствие координат не скрывало всю оценку целиком.
 */
private fun byChoiceDistanceMeters(splits: List<SplitTime>, controlPointByNumber: Map<Int, ControlPoint>): Double? {
    if (controlPointByNumber.isEmpty() || splits.size < 2) return null
    var sum = 0.0
    for (i in 1 until splits.size) {
        val from = controlPointByNumber[splits[i - 1].controlPoint]
        val to = controlPointByNumber[splits[i].controlPoint]
        sum += controlPointDistanceMeters(from, to) ?: 0.0
    }
    return sum.takeIf { it > 0.0 }
}

/**
 * Строит таблицу сплитов группы: сопоставление позиционное (splits[i] <-> cpOrder[i]),
 * что корректно обрабатывает дублирующиеся номера КП в дистанции.
 * Порядок строк — как в [group.participants], сортировку применяет вызывающий код ([sortedForResults]).
 *
 * Для BY_CHOICE у каждого участника свой набор и порядок КП — общий cpOrder не имеет смысла:
 * колонки строятся по позиции (1..максимум сплитов в группе), а какой именно КП стоит за каждой
 * позицией у конкретного участника — заполняется в [SplitsTableCell.controlPoint]. Ранги/лучший
 * перегон/темп не считаются (сравнивать разные реальные перегоны бессмысленно) — тот же подход,
 * что и в HTML-публикации результатов.
 *
 * @param distance дистанция группы — если передана и у КП есть координаты (импорт из
 * геопривязанной карты через IOF XML), в ячейках FORWARD/MARKING заполняется темп участника на
 * перегоне ([SplitsTableCell.paceMinPerKm]); без неё темп остаётся null. Также используется для
 * пересчёта [SplitsTableRow.rawScore] в BY_CHOICE.
 */
fun buildSplitsTable(
    group: GroupWithParticipantsAndResults,
    distance: Distance? = null,
    direction: OrienteeringDirection = OrienteeringDirection.FORWARD,
): SplitsTable {
    if (direction == OrienteeringDirection.BY_CHOICE) {
        val scoreByNumber = distance?.controlPoints?.associate { it.number to it.score } ?: emptyMap()
        val controlPointByNumber = distance?.controlPoints?.associateBy { it.number } ?: emptyMap()
        val maxSplitsCount = group.participants.maxOfOrNull { it.result?.splits?.size ?: 0 } ?: 0
        val columns = (1..maxSplitsCount).map { SplitsTableColumn(positionIndex = it, controlPoint = 0) }

        val rows = group.participants.map { pw ->
            val splits = pw.result?.splits ?: emptyList()
            val startTs = anchorStartTime(pw)

            val cells = (0 until maxSplitsCount).map { i ->
                if (i >= splits.size) {
                    SplitsTableCell(
                        deltaSeconds = null,
                        cumulativeSeconds = null,
                        deltaRank = null,
                        cumulativeRank = null,
                        isBestLeg = false,
                    )
                } else {
                    val splitTs = splits[i].timestamp
                    val prevTs = if (i == 0) startTs else splits[i - 1].timestamp
                    SplitsTableCell(
                        deltaSeconds = (splitTs - prevTs) / 1000L,
                        cumulativeSeconds = (splitTs - startTs) / 1000L,
                        deltaRank = null,
                        cumulativeRank = null,
                        isBestLeg = false,
                        controlPoint = splits[i].controlPoint,
                    )
                }
            }

            SplitsTableRow(
                participant = pw.participant,
                result = pw.result,
                cells = cells,
                rawScore = rawByChoiceScore(pw.result, scoreByNumber),
                totalDistanceMeters = byChoiceDistanceMeters(splits, controlPointByNumber),
            )
        }

        return SplitsTable(columns = columns, rows = rows)
    }

    val cpOrder = group.participants
        .mapNotNull { it.result?.splits }
        .maxByOrNull { it.size }
        ?.map { it.controlPoint }
        ?: emptyList()

    val columns = cpOrder.mapIndexed { i, cp -> SplitsTableColumn(positionIndex = i + 1, controlPoint = cp) }
    val legLengths = legLengthsMeters(distance, cpOrder)

    val cumulRanks: List<Map<String, Int>> = cpOrder.indices.map { i ->
        group.participants
            .mapNotNull { pw ->
                val splits = pw.result?.splits ?: return@mapNotNull null
                val startTs = anchorStartTime(pw)
                if (i < splits.size) pw.participant.id to (splits[i].timestamp - startTs) else null
            }
            .sortedBy { it.second }
            .mapIndexed { rank, (id, _) -> id to (rank + 1) }
            .toMap()
    }

    val deltaRanks: List<Map<String, Int>> = cpOrder.indices.map { i ->
        group.participants
            .mapNotNull { pw ->
                val splits = pw.result?.splits ?: return@mapNotNull null
                val startTs = anchorStartTime(pw)
                if (i < splits.size) {
                    val prevTs = if (i == 0) startTs else splits[i - 1].timestamp
                    pw.participant.id to (splits[i].timestamp - prevTs)
                } else null
            }
            .sortedBy { it.second }
            .mapIndexed { rank, (id, _) -> id to (rank + 1) }
            .toMap()
    }

    val rows = group.participants.map { pw ->
        val splits = pw.result?.splits ?: emptyList()
        val startTs = anchorStartTime(pw)

        val cells = cpOrder.indices.map { i ->
            if (i >= splits.size) {
                SplitsTableCell(
                    deltaSeconds = null,
                    cumulativeSeconds = null,
                    deltaRank = null,
                    cumulativeRank = null,
                    isBestLeg = false,
                )
            } else {
                val splitTs = splits[i].timestamp
                val prevTs = if (i == 0) startTs else splits[i - 1].timestamp
                val cumulSec = (splitTs - startTs) / 1000L
                val deltaSec = (splitTs - prevTs) / 1000L
                val cumulRank = cumulRanks[i][pw.participant.id]
                val deltaRank = deltaRanks[i][pw.participant.id]
                val pace = paceMinPerKm(deltaSec, legLengths.getOrNull(i))

                SplitsTableCell(
                    deltaSeconds = deltaSec,
                    cumulativeSeconds = cumulSec,
                    deltaRank = deltaRank,
                    paceMinPerKm = pace,
                    cumulativeRank = cumulRank,
                    isBestLeg = deltaRank == 1,
                )
            }
        }

        SplitsTableRow(participant = pw.participant, result = pw.result, cells = cells)
    }

    return SplitsTable(columns = columns, rows = rows)
}

/**
 * Сортировка участников для отображения результатов: по статусу, затем —
 * для BY_CHOICE (score-О) по сумме баллов убыв. с тай-брейком по времени прохождения дистанции,
 * для остальных направлений — по итоговому времени возрастанию (как раньше).
 *
 * Тай-брейк использует именно [OrienteeringResult.totalTime] (время прохождения, finish-start),
 * а не [OrienteeringResult.finishTime] (абсолютное время по часам) — при интервальном/разном
 * старте участников более раннее абсолютное время финиша не означает более быстрый забег.
 */
fun List<ParticipantWithResult>.sortedForResults(
    direction: OrienteeringDirection = OrienteeringDirection.FORWARD
): List<ParticipantWithResult> =
    if (direction == OrienteeringDirection.BY_CHOICE) {
        sortedWith(
            compareBy<ParticipantWithResult> { statusSortOrder(it.result?.status) }
                .thenByDescending { it.result?.totalScore ?: 0 }
                .thenBy { it.result?.totalTime ?: Long.MAX_VALUE }
        )
    } else {
        sortedWith(
            compareBy(
                { p -> statusSortOrder(p.result?.status) },
                { p -> p.result?.totalTime ?: Long.MAX_VALUE },
            )
        )
    }

private fun statusSortOrder(status: ResultStatus?): Int = when (status) {
    ResultStatus.FINISHED -> 0
    // Превысившие КВ идут сразу за финишировавшими: результат показан, но места нет.
    ResultStatus.OVERTIME -> 1
    ResultStatus.DSQ -> 2
    ResultStatus.DNF -> 3
    ResultStatus.DNS -> 4
    ResultStatus.STARTED -> 5
    ResultStatus.REGISTERED -> 6
    null -> 9
}

/** Точка графика сплитов для одного КП: отставание участника от лидера в секундах. */
data class RaceGraphPoint(
    val positionIndex: Int,
    val controlPoint: Int,
    val deltaSeconds: Long?,
)

/** Кривая отставания от лидера для одного участника по всем КП дистанции. */
data class RaceGraphSeries(
    val participant: OrienteeringParticipant,
    val result: OrienteeringResult?,
    val points: List<RaceGraphPoint>,
)

data class RaceGraphData(
    val columns: List<SplitsTableColumn>,
    val series: List<RaceGraphSeries>,
)

/**
 * Строит данные для графика отставания от лидера (race graph, аналог WinSplits) на основе
 * уже посчитанной [SplitsTable]: для каждого КП лидер — участник с минимальным
 * [SplitsTableCell.cumulativeSeconds], отставание остальных считается относительно него.
 * Не финишировавшие (DNS/DNF/DSQ/REGISTERED/STARTED) исключаются — их кривая не имеет смысла.
 */
fun buildRaceGraphData(table: SplitsTable): RaceGraphData {
    val finishedRows = table.rows.filter { it.result?.status == ResultStatus.FINISHED }

    val leaderCumulativeByColumn = table.columns.indices.map { i ->
        finishedRows.mapNotNull { it.cells.getOrNull(i)?.cumulativeSeconds }.minOrNull()
    }

    val series = finishedRows.map { row ->
        val points = table.columns.mapIndexed { i, column ->
            val cumulative = row.cells.getOrNull(i)?.cumulativeSeconds
            val leader = leaderCumulativeByColumn[i]
            RaceGraphPoint(
                positionIndex = column.positionIndex,
                controlPoint = column.controlPoint,
                deltaSeconds = if (cumulative != null && leader != null) cumulative - leader else null,
            )
        }
        RaceGraphSeries(participant = row.participant, result = row.result, points = points)
    }

    return RaceGraphData(columns = table.columns, series = series)
}

/** Точка графика набора очков (BY_CHOICE): момент времени от старта и накопленные очки на этот момент. */
data class ScoreGraphPoint(
    val elapsedSeconds: Long,
    val cumulativeScore: Int,
)

/** Кривая набора очков во времени для одного участника (BY_CHOICE). */
data class ScoreGraphSeries(
    val participant: OrienteeringParticipant,
    val result: OrienteeringResult?,
    val points: List<ScoreGraphPoint>,
)

data class ScoreGraphData(
    val series: List<ScoreGraphSeries>,
    /** Контрольное время группы в секундах от старта — момент, с которого начинает начисляться
     * штраф за опоздание. Null, если у группы нет ограничения по времени. */
    val timeLimitSeconds: Long? = null,
)

/**
 * Строит данные графика набора очков во времени для BY_CHOICE (score-О): по оси времени —
 * секунды от старта участника, по оси очков — сумма очков за фактически взятые КП (сырая, ДО
 * вычета штрафа за опоздание — штраф не непрерывная функция времени, а разовое списание по
 * итогу, поэтому в кривую его включать не нужно; итоговое место/очки видны в легенде).
 * Каждая отметка добавляет точку — наклон отрезка между соседними точками показывает темп
 * набора очков на этом отрезке. Если участник финишировал позже последней отметки — добавляется
 * финальная плоская точка на totalTime, чтобы линия доходила до конца гонки.
 * Не финишировавшие исключаются — их кривая до конца не имеет смысла сравнивать.
 */
fun buildScoreGraphData(group: GroupWithParticipantsAndResults, distance: Distance? = null): ScoreGraphData {
    val scoreByNumber = distance?.controlPoints?.associate { it.number to it.score } ?: emptyMap()
    val timeLimitSeconds = group.group.timeLimitMinutes?.takeIf { it > 0 }?.let { it * 60L }

    val series = group.participants.mapNotNull { pw ->
        val result = pw.result ?: return@mapNotNull null
        if (result.status != ResultStatus.FINISHED) return@mapNotNull null
        val startTs = anchorStartTime(pw)

        val rawPoints = mutableListOf(ScoreGraphPoint(0L, 0))
        var cumulative = 0
        result.splits?.forEach { split ->
            cumulative += scoreByNumber[split.controlPoint] ?: 0
            rawPoints += ScoreGraphPoint((split.timestamp - startTs) / 1000L, cumulative)
        }
        val finishSeconds = result.totalTime
        if (finishSeconds != null && finishSeconds > rawPoints.last().elapsedSeconds) {
            rawPoints += ScoreGraphPoint(finishSeconds, cumulative)
        }

        val deduction = (cumulative - (result.totalScore ?: cumulative)).coerceAtLeast(0)
        val points = applyLatePenalty(rawPoints, timeLimitSeconds, deduction)

        ScoreGraphSeries(participant = pw.participant, result = result, points = points)
    }

    return ScoreGraphData(series = series, timeLimitSeconds = timeLimitSeconds)
}

/**
 * Применяет линейно нарастающий штраф за опоздание к "сырой" кривой очков: до [timeLimitSeconds]
 * кривая не меняется, после — вычитается штраф, линейно растущий от 0 в момент истечения лимита
 * до [totalDeduction] в момент финиша (последняя точка кривой). За величину штрафа берётся не
 * [OrienteeringResult.scorePenalty] напрямую (оно ненадёжно при полном обнулении результата за
 * сильное опоздание), а разница между суммой очков за реально взятые КП и итоговым зачётным
 * результатом — так конечная точка графика гарантированно совпадает с официальным местом
 * участника в любом случае.
 */
private fun applyLatePenalty(rawPoints: List<ScoreGraphPoint>, timeLimitSeconds: Long?, totalDeduction: Int): List<ScoreGraphPoint> {
    val finishSeconds = rawPoints.last().elapsedSeconds
    if (timeLimitSeconds == null || totalDeduction <= 0 || finishSeconds <= timeLimitSeconds) return rawPoints

    val rampSpan = (finishSeconds - timeLimitSeconds).toDouble()
    fun deductionAt(t: Long): Int =
        if (t <= timeLimitSeconds) 0 else (totalDeduction * (t - timeLimitSeconds) / rampSpan).toInt()

    val result = mutableListOf<ScoreGraphPoint>()
    var boundaryInserted = false
    for (i in rawPoints.indices) {
        val p = rawPoints[i]
        if (p.elapsedSeconds <= timeLimitSeconds) {
            result += p
        } else {
            if (!boundaryInserted) {
                result += ScoreGraphPoint(timeLimitSeconds, rawPoints[i - 1].cumulativeScore)
                boundaryInserted = true
            }
            result += ScoreGraphPoint(p.elapsedSeconds, (p.cumulativeScore - deductionAt(p.elapsedSeconds)).coerceAtLeast(0))
        }
    }
    return result
}
