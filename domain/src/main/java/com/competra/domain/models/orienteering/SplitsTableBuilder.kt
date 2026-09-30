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
 * Длина перегона (в метрах) для каждой позиции в [cpOrder] дистанции [distance]. Первый перегон —
 * от точки старта ([startPoint]), перегон на финишную станцию — до координат финиша (см.
 * [expectedSequence]). Null там, где у одного из концов перегона нет координат (дистанция создана
 * вручную или импортирована до появления координат старта/финиша).
 */
private fun legLengthsMeters(distance: Distance?, cpOrder: List<Int>): List<Double?> {
    val expected = distance?.expectedSequence() ?: return List(cpOrder.size) { null }
    val start = distance.startPoint()
    return cpOrder.indices.map { i ->
        controlPointDistanceMeters(if (i == 0) start else expected.getOrNull(i - 1), expected.getOrNull(i))
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
    /** Дистанция, пройденная участником (BY_CHOICE), в метрах — сумма расстояний от старта через
     * последовательно взятые КП (включая финишную станцию) по их координатам. Null для
     * FORWARD/MARKING, когда у дистанции нет координат КП, и когда сумма получилась нулевой (ни
     * одного перегона с известными координатами). Перегон от старта не входит, если его координаты
     * неизвестны. */
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

/** Участник с отметками только на дистанции — без сделанных до старта (см. [punchesAfterStart]). */
private fun ParticipantWithResult.withRaceSplits(): ParticipantWithResult {
    val splits = result?.splits ?: return this
    return copy(result = result.copy(splits = punchesAfterStart(splits, anchorStartTime(this))))
}

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
 * Дистанция, пройденная участником (BY_CHOICE), в метрах — сумма расстояний от старта
 * ([startPoint]) через последовательно взятые КП, включая финишную станцию ([expectedSequence]),
 * по их координатам. Перегоны с неизвестными координатами (нет хотя бы одной из точек, в т.ч.
 * старта) в сумму не входят — итог остаётся приблизительным, а не null, чтобы частичное
 * отсутствие координат не скрывало всю оценку целиком.
 */
private fun byChoiceDistanceMeters(splits: List<SplitTime>, distance: Distance?): Double? {
    if (distance == null || distance.controlPoints.isEmpty() || splits.isEmpty()) return null
    val controlPointByNumber = distance.expectedSequence().associateBy { it.number }
    val route = listOf(distance.startPoint()) + splits.map { controlPointByNumber[it.controlPoint] }
    var sum = 0.0
    for (i in 1 until route.size) {
        sum += controlPointDistanceMeters(route[i - 1], route[i]) ?: 0.0
    }
    return sum.takeIf { it > 0.0 }
}

/** Перегон короче этого не участвует в рейтинге — см. isRankableLeg в [buildSplitsTable]. */
private const val MIN_RANKABLE_LEG_MS = 1000L

/**
 * Последовательность КП колонок таблицы (FORWARD/MARKING).
 *
 * Берётся из дистанции (КП + финиш), если у неё задан финишный КП. Иначе — самая частая
 * последовательность отметок среди финишировавших (при равенстве — самая короткая): у отдельного
 * финишировавшего могут быть лишние отметки (отметил чужой КП, потом нашёл свой), и раньше, когда
 * колонки брались по самому длинному результату, такой участник сдвигал всю таблицу. Если
 * финишировавших нет — самый длинный результат группы.
 */
private fun courseSequence(participants: List<ParticipantWithResult>, distance: Distance?): List<Int> {
    if (distance?.finishControlPoint != null && distance.controlPoints.isNotEmpty()) {
        return distance.expectedSequence().map { it.number }
    }
    val finishedSequences = participants
        .filter { it.result?.status == ResultStatus.FINISHED }
        .mapNotNull { pw -> pw.result?.splits?.map { it.controlPoint }?.takeIf { it.isNotEmpty() } }
    if (finishedSequences.isNotEmpty()) {
        return finishedSequences
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<List<Int>, Int>> { it.value }.thenBy { it.key.size })
            .first()
            .key
    }
    return participants
        .mapNotNull { pw -> pw.result?.splits?.map { it.controlPoint } }
        .maxByOrNull { it.size }
        .orEmpty()
}

/**
 * Сопоставляет отметки участника с КП дистанции: для каждой позиции [cpOrder] — первая ещё не
 * использованная отметка этого КП после предыдущей сопоставленной (тот же последовательный поиск,
 * что и при проверке прохождения дистанции на экране считывания чипа). Лишние отметки в таблицу не
 * попадают, у пропущенного КП — `null`; повторяющиеся КП (петли) получают каждое своё вхождение.
 */
private fun matchToCourse(splits: List<SplitTime>, cpOrder: List<Int>): List<SplitTime?> {
    var searchIndex = 0
    return cpOrder.map { cp ->
        val found = (searchIndex until splits.size).firstOrNull { splits[it].controlPoint == cp }
        if (found != null) searchIndex = found + 1
        found?.let { splits[it] }
    }
}

/**
 * Строит таблицу сплитов группы. Для FORWARD/MARKING колонки — КП дистанции по порядку (см.
 * [courseSequence]), отметки участника раскладываются по ним последовательным поиском (см.
 * [matchToCourse]): лишние отметки не сдвигают строку, у пропущенного КП ячейка пустая, повторяющиеся
 * номера КП в дистанции (петли) обрабатываются корректно. В рейтинг перегона/общего времени попадают
 * только сравнимые значения — без пропусков перед КП и без перегонов короче секунды.
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
 * перегоне ([SplitsTableCell.paceMinPerKm]), на первом — если известны координаты старта; без
 * неё темп остаётся null. Также используется для
 * пересчёта [SplitsTableRow.rawScore] в BY_CHOICE.
 *
 * Отметки до старта в таблицу не входят ([punchesAfterStart]), в [SplitsTableRow.result] — тоже.
 */
fun buildSplitsTable(
    group: GroupWithParticipantsAndResults,
    distance: Distance? = null,
    direction: OrienteeringDirection = OrienteeringDirection.FORWARD,
): SplitsTable {
    val participants = group.participants.map { it.withRaceSplits() }

    if (direction == OrienteeringDirection.BY_CHOICE) {
        val scoreByNumber = distance?.controlPoints?.associate { it.number to it.score } ?: emptyMap()
        val maxSplitsCount = participants.maxOfOrNull { it.result?.splits?.size ?: 0 } ?: 0
        val columns = (1..maxSplitsCount).map { SplitsTableColumn(positionIndex = it, controlPoint = 0) }

        val rows = participants.map { pw ->
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
                totalDistanceMeters = byChoiceDistanceMeters(splits, distance),
            )
        }

        return SplitsTable(columns = columns, rows = rows)
    }

    val cpOrder = courseSequence(participants, distance)
    val columns = cpOrder.mapIndexed { i, cp -> SplitsTableColumn(positionIndex = i + 1, controlPoint = cp) }
    val legLengths = legLengthsMeters(distance, cpOrder)
    val matchedByParticipant = participants.associate { pw ->
        pw.participant.id to matchToCourse(pw.result?.splits.orEmpty(), cpOrder)
    }

    /** Время отметки предыдущего взятого КП дистанции до позиции [i], либо старт. */
    fun prevTimestamp(matched: List<SplitTime?>, i: Int, startTs: Long): Long =
        (i - 1 downTo 0).firstNotNullOfOrNull { matched[it] }?.timestamp ?: startTs

    /**
     * Перегон на позицию [i] сравним с другими: взяты и этот КП, и предыдущий по дистанции, а сам
     * перегон не короче секунды. Отметки быстрее секунды не бывает — это КП, засчитанный
     * организатором вручную с временем предыдущей отметки (см. insertCreditedPunch в feature:center).
     */
    fun isRankableLeg(matched: List<SplitTime?>, i: Int, startTs: Long): Boolean {
        val split = matched[i] ?: return false
        if (i > 0 && matched[i - 1] == null) return false
        return split.timestamp - prevTimestamp(matched, i, startTs) >= MIN_RANKABLE_LEG_MS
    }

    /** Ранги на позиции [i] среди участников, для которых [measure] вернула время (> 0). */
    fun ranksAt(i: Int, measure: (matched: List<SplitTime?>, startTs: Long) -> Long?): Map<String, Int> =
        participants
            .mapNotNull { pw ->
                val matched = matchedByParticipant.getValue(pw.participant.id)
                val value = measure(matched, anchorStartTime(pw))?.takeIf { it > 0 } ?: return@mapNotNull null
                pw.participant.id to value
            }
            .sortedBy { it.second }
            .mapIndexed { rank, (id, _) -> id to (rank + 1) }
            .toMap()

    // Общее время на КП сравнимо, только если все КП до него взяты: у снятого с пропуском
    // иначе оказывалось бы «лучшее» время на следующих КП.
    val cumulRanks: List<Map<String, Int>> = cpOrder.indices.map { i ->
        ranksAt(i) { matched, startTs ->
            val split = matched[i]
            if (split == null || (0 until i).any { matched[it] == null } || !isRankableLeg(matched, i, startTs)) {
                null
            } else {
                split.timestamp - startTs
            }
        }
    }

    val deltaRanks: List<Map<String, Int>> = cpOrder.indices.map { i ->
        ranksAt(i) { matched, startTs ->
            if (!isRankableLeg(matched, i, startTs)) return@ranksAt null
            matched[i]!!.timestamp - prevTimestamp(matched, i, startTs)
        }
    }

    val rows = participants.map { pw ->
        val matched = matchedByParticipant.getValue(pw.participant.id)
        val startTs = anchorStartTime(pw)

        val cells = cpOrder.indices.map { i ->
            val split = matched[i]
            if (split == null) {
                SplitsTableCell(
                    deltaSeconds = null,
                    cumulativeSeconds = null,
                    deltaRank = null,
                    cumulativeRank = null,
                    isBestLeg = false,
                )
            } else {
                val cumulSec = (split.timestamp - startTs) / 1000L
                val deltaSec = (split.timestamp - prevTimestamp(matched, i, startTs)) / 1000L
                val cumulRank = cumulRanks[i][pw.participant.id]
                val deltaRank = deltaRanks[i][pw.participant.id]
                // Темп — только для перегонов, попавших в рейтинг: у перегона через пропущенный КП он бессмыслен.
                val pace = if (deltaRank != null) paceMinPerKm(deltaSec, legLengths.getOrNull(i)) else null

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
        result.splits?.let { punchesAfterStart(it, startTs) }?.forEach { split ->
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
