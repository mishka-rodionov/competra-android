package com.competra.domain.models.orienteering

import com.competra.domain.models.Coordinates
import com.competra.domain.models.Gender
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.ResultStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверка [buildSplitsTable] и [sortedForResults] — общего билдера таблицы сплитов,
 * переиспользуемого HTML/CSV/PDF-экспортом и экранами сплитов группы.
 */
class SplitsTableBuilderTest {

    private fun participant(id: String, groupId: Long = 1L, startTime: Long = 0L): OrienteeringParticipant =
        OrienteeringParticipant(
            id = id,
            userId = "u$id",
            firstName = "First$id",
            lastName = "Last$id",
            groupId = groupId,
            groupName = "G$groupId",
            competitionId = "c1",
            commandName = "team",
            startNumber = id,
            startTime = startTime,
            chipNumber = "chip$id",
            comment = "",
            isChipGiven = true,
        )

    private fun result(
        participantId: String,
        status: ResultStatus,
        startTime: Long? = 0L,
        totalTime: Long? = null,
        splits: List<SplitTime>? = null,
    ): OrienteeringResult = OrienteeringResult(
        competitionId = "c1",
        groupId = 1L,
        participantId = participantId,
        startTime = startTime,
        totalTime = totalTime,
        status = status,
        splits = splits,
    )

    private fun group(participants: List<ParticipantWithResult>): GroupWithParticipantsAndResults =
        GroupWithParticipantsAndResults(
            group = ParticipantGroup(
                groupId = 1L,
                competitionId = "c1",
                title = "M21",
                gender = Gender.MALE,
                distanceId = 1L,
            ),
            participants = participants,
        )

    @Test
    fun `positional split matching computes correct delta and cumulative seconds`() {
        val a = ParticipantWithResult(
            participant = participant("A"),
            result = result(
                "A", ResultStatus.FINISHED, totalTime = 200,
                splits = listOf(SplitTime(31, 100_000), SplitTime(32, 200_000)),
            ),
        )
        val b = ParticipantWithResult(
            participant = participant("B"),
            result = result(
                "B", ResultStatus.FINISHED, totalTime = 260,
                splits = listOf(SplitTime(31, 150_000), SplitTime(32, 260_000)),
            ),
        )

        val table = buildSplitsTable(group(listOf(a, b)))

        assertEquals(listOf(31, 32), table.columns.map { it.controlPoint })

        val rowA = table.rows[0]
        assertEquals(100L, rowA.cells[0].deltaSeconds)
        assertEquals(100L, rowA.cells[0].cumulativeSeconds)
        assertEquals(100L, rowA.cells[1].deltaSeconds)
        assertEquals(200L, rowA.cells[1].cumulativeSeconds)

        val rowB = table.rows[1]
        assertEquals(150L, rowB.cells[0].deltaSeconds)
        assertEquals(110L, rowB.cells[1].deltaSeconds)
        assertEquals(260L, rowB.cells[1].cumulativeSeconds)
    }

    @Test
    fun `fastest leg is ranked first and marked as best`() {
        val a = ParticipantWithResult(
            participant = participant("A"),
            result = result(
                "A", ResultStatus.FINISHED, totalTime = 200,
                splits = listOf(SplitTime(31, 100_000), SplitTime(32, 200_000)),
            ),
        )
        val b = ParticipantWithResult(
            participant = participant("B"),
            result = result(
                "B", ResultStatus.FINISHED, totalTime = 260,
                splits = listOf(SplitTime(31, 150_000), SplitTime(32, 260_000)),
            ),
        )

        val table = buildSplitsTable(group(listOf(a, b)))

        val rowA = table.rows[0]
        val rowB = table.rows[1]

        assertTrue(rowA.cells[0].isBestLeg)
        assertTrue(rowA.cells[1].isBestLeg)
        assertFalse(rowB.cells[0].isBestLeg)
        assertFalse(rowB.cells[1].isBestLeg)

        assertEquals(1, rowA.cells[0].deltaRank)
        assertEquals(2, rowB.cells[0].deltaRank)
        assertEquals(1, rowA.cells[1].cumulativeRank)
        assertEquals(2, rowB.cells[1].cumulativeRank)
    }

    @Test
    fun `participant with missing punch gets empty cell instead of crashing`() {
        val a = ParticipantWithResult(
            participant = participant("A"),
            result = result(
                "A", ResultStatus.FINISHED, totalTime = 200,
                splits = listOf(SplitTime(31, 100_000), SplitTime(32, 200_000)),
            ),
        )
        val c = ParticipantWithResult(
            participant = participant("C"),
            result = result(
                "C", ResultStatus.DNF,
                splits = listOf(SplitTime(31, 120_000)), // не дошёл до второго КП
            ),
        )

        val table = buildSplitsTable(group(listOf(a, c)))

        val rowC = table.rows[1]
        assertEquals(120L, rowC.cells[0].cumulativeSeconds)
        assertNull(rowC.cells[1].cumulativeSeconds)
        assertNull(rowC.cells[1].deltaSeconds)
        assertFalse(rowC.cells[1].isBestLeg)
    }

    @Test
    fun `falls back to participant scheduled start time when result start time is lost`() {
        // Сценарий SEN-27: реальный OrienteeringResult.startTime утерян (результаты потеряны и
        // восстановлены HTML-импортом), сплиты в этом случае реконструируются от планового
        // startTime участника (см. buildResultsDiff) — отображение должно использовать тот же анкер,
        // иначе кумулятив считается от эпохи Unix (1970 года) вместо реального времени старта.
        val scheduledStart = 1_700_000_000_000L // плановое время старта участника
        val a = ParticipantWithResult(
            participant = participant("A", startTime = scheduledStart),
            result = result(
                "A", ResultStatus.FINISHED, startTime = null, totalTime = 200,
                splits = listOf(
                    SplitTime(31, scheduledStart + 100_000),
                    SplitTime(32, scheduledStart + 200_000),
                ),
            ),
        )

        val table = buildSplitsTable(group(listOf(a)))
        val row = table.rows[0]

        assertEquals(100L, row.cells[0].cumulativeSeconds)
        assertEquals(100L, row.cells[0].deltaSeconds)
        assertEquals(200L, row.cells[1].cumulativeSeconds)
        assertEquals(100L, row.cells[1].deltaSeconds)
        assertEquals(1, row.cells[0].cumulativeRank)
    }

    @Test
    fun `punch before start is ignored and does not shift splits`() {
        val start = 1_000_000L
        val a = ParticipantWithResult(
            participant = participant("A", startTime = start),
            result = result(
                "A", ResultStatus.FINISHED, startTime = start, totalTime = 300,
                splits = listOf(SplitTime(31, start + 100_000), SplitTime(32, start + 200_000), SplitTime(100, start + 300_000)),
            ),
        )
        // Отметила финишную станцию в стартовом городке до своего старта.
        val b = ParticipantWithResult(
            participant = participant("B", startTime = start),
            result = result(
                "B", ResultStatus.DSQ, startTime = start,
                splits = listOf(SplitTime(100, start - 60_000), SplitTime(31, start + 90_000), SplitTime(100, start + 400_000)),
            ),
        )

        val table = buildSplitsTable(group(listOf(a, b)))
        val rowB = table.rows[1]

        assertEquals(listOf(31, 32, 100), table.columns.map { it.controlPoint })
        assertEquals(90L, rowB.cells[0].deltaSeconds)
        assertEquals(1, rowB.cells[0].deltaRank)
        assertEquals(listOf(SplitTime(31, start + 90_000), SplitTime(100, start + 400_000)), rowB.result?.splits)
    }

    @Test
    fun `missed control point leaves empty cell and following splits are not ranked`() {
        val a = ParticipantWithResult(
            participant = participant("A"),
            result = result(
                "A", ResultStatus.FINISHED, totalTime = 300,
                splits = listOf(SplitTime(31, 100_000), SplitTime(32, 200_000), SplitTime(33, 300_000)),
            ),
        )
        // Пропустил КП 32 — отметка КП 33 должна попасть в свою колонку, а не в колонку КП 32.
        val b = ParticipantWithResult(
            participant = participant("B"),
            result = result(
                "B", ResultStatus.DSQ,
                splits = listOf(SplitTime(31, 120_000), SplitTime(33, 150_000)),
            ),
        )

        val table = buildSplitsTable(group(listOf(a, b)))
        val rowA = table.rows[0]
        val rowB = table.rows[1]

        assertNull(rowB.cells[1].cumulativeSeconds)
        assertEquals(30L, rowB.cells[2].deltaSeconds) // время от предыдущего взятого КП показываем как есть
        assertEquals(150L, rowB.cells[2].cumulativeSeconds)
        assertNull(rowB.cells[2].deltaRank)
        assertNull(rowB.cells[2].cumulativeRank)
        assertFalse(rowB.cells[2].isBestLeg)
        assertTrue(rowA.cells[2].isBestLeg)
        assertEquals(1, rowA.cells[2].cumulativeRank)
        assertEquals(2, rowB.cells[0].deltaRank) // корректный первый перегон по-прежнему в рейтинге
    }

    @Test
    fun `extra punches of finished participant do not shift columns of others`() {
        // Сценарий из М21: у финишировавшего лишние отметки (чужой КП 38 вместо 34, потом свой)
        // — раньше его сплиты как самые длинные задавали колонки, и у остальных всё съезжало.
        val a = ParticipantWithResult(
            participant("A"),
            result(
                "A", ResultStatus.FINISHED, totalTime = 400,
                splits = listOf(SplitTime(31, 100_000), SplitTime(32, 200_000), SplitTime(33, 300_000), SplitTime(100, 400_000)),
            ),
        )
        val b = ParticipantWithResult(
            participant("B"),
            result(
                "B", ResultStatus.FINISHED, totalTime = 420,
                splits = listOf(SplitTime(31, 110_000), SplitTime(32, 220_000), SplitTime(33, 330_000), SplitTime(100, 420_000)),
            ),
        )
        val withExtra = ParticipantWithResult(
            participant("C"),
            result(
                "C", ResultStatus.FINISHED, totalTime = 500,
                splits = listOf(
                    SplitTime(31, 90_000), SplitTime(38, 150_000), SplitTime(32, 250_000),
                    SplitTime(33, 350_000), SplitTime(100, 500_000),
                ),
            ),
        )

        val table = buildSplitsTable(group(listOf(a, b, withExtra)))

        assertEquals(listOf(31, 32, 33, 100), table.columns.map { it.controlPoint })
        val rowA = table.rows[0]
        assertEquals(400L, rowA.cells[3].cumulativeSeconds) // финиш A — в колонке финиша
        assertEquals(1, rowA.cells[3].cumulativeRank)
        val rowC = table.rows[2]
        assertEquals(160L, rowC.cells[1].deltaSeconds) // перегон 31→32 включает крюк через 38
        assertEquals(1, rowC.cells[0].deltaRank)
        assertEquals(3, rowC.cells[1].deltaRank)
    }

    @Test
    fun `columns follow distance when it has finish control point`() {
        val distance = Distance(
            competitionId = "c1",
            lengthMeters = 1000,
            climbMeters = 0,
            controlsCount = 2,
            controlPoints = listOf(ControlPoint(number = 31), ControlPoint(number = 32)),
            finishControlPoint = 100,
        )
        // Единственный финишировавший с лишней отметкой — колонки всё равно по дистанции.
        val a = ParticipantWithResult(
            participant("A"),
            result(
                "A", ResultStatus.FINISHED, totalTime = 300,
                splits = listOf(SplitTime(31, 100_000), SplitTime(45, 150_000), SplitTime(32, 200_000), SplitTime(100, 300_000)),
            ),
        )

        val table = buildSplitsTable(group(listOf(a)), distance)

        assertEquals(listOf(31, 32, 100), table.columns.map { it.controlPoint })
        assertEquals(100L, table.rows[0].cells[1].deltaSeconds)
    }

    @Test
    fun `credited control point with sub-second leg is not ranked as best`() {
        val a = ParticipantWithResult(
            participant("A"),
            result(
                "A", ResultStatus.FINISHED, totalTime = 300,
                splits = listOf(SplitTime(31, 100_000), SplitTime(32, 200_000), SplitTime(100, 300_000)),
            ),
        )
        // КП 32 засчитан организатором: время отметки 31 + 1 мс.
        val credited = ParticipantWithResult(
            participant("B"),
            result(
                "B", ResultStatus.FINISHED, totalTime = 350,
                splits = listOf(SplitTime(31, 120_000), SplitTime(32, 120_001), SplitTime(100, 350_000)),
            ),
        )

        val table = buildSplitsTable(group(listOf(a, credited)))
        val rowB = table.rows[1]

        assertEquals(0L, rowB.cells[1].deltaSeconds)
        assertNull(rowB.cells[1].deltaRank)
        assertNull(rowB.cells[1].cumulativeRank)
        assertFalse(rowB.cells[1].isBestLeg)
        assertTrue(table.rows[0].cells[1].isBestLeg)
        assertEquals(2, rowB.cells[2].cumulativeRank) // дальше — обычный рейтинг
    }

    @Test
    fun `by choice columns stay positional with participant own control points`() {
        val a = ParticipantWithResult(
            participant("A"),
            result("A", ResultStatus.FINISHED, totalTime = 300, splits = listOf(SplitTime(33, 100_000), SplitTime(31, 200_000), SplitTime(100, 300_000))),
        )
        val b = ParticipantWithResult(
            participant("B"),
            result("B", ResultStatus.FINISHED, totalTime = 200, splits = listOf(SplitTime(32, 150_000), SplitTime(100, 200_000))),
        )

        val table = buildSplitsTable(group(listOf(a, b)), direction = OrienteeringDirection.BY_CHOICE)

        assertEquals(listOf(1, 2, 3), table.columns.map { it.positionIndex })
        assertEquals(listOf(33, 31, 100), table.rows[0].cells.map { it.controlPoint })
        assertEquals(listOf(32, 100, null), table.rows[1].cells.map { it.controlPoint })
        assertEquals(50L, table.rows[1].cells[1].deltaSeconds)
        assertTrue(table.rows.flatMap { it.cells }.none { it.isBestLeg || it.deltaRank != null })
    }

    @Test
    fun `participant without result produces columns with no splits`() {
        val onlyNoResult = ParticipantWithResult(participant = participant("A"), result = null)

        val table = buildSplitsTable(group(listOf(onlyNoResult)))

        assertTrue(table.columns.isEmpty())
        assertTrue(table.rows[0].cells.isEmpty())
    }

    @Test
    fun `pace is computed from control point coordinates when distance is provided`() {
        // Второй КП ровно в 1000м строго на север от первого (долгота не меняется) —
        // на меридиане гаверсинус вырождается в R * dLatRad, поэтому расстояние точное,
        // без допуска на сферическую аппроксимацию.
        val dLatRad = 1000.0 / 6_371_000.0
        val distance = Distance(
            competitionId = "c1",
            lengthMeters = 1000,
            climbMeters = 0,
            controlsCount = 2,
            controlPoints = listOf(
                ControlPoint(number = 31, latitude = 0.0, longitude = 0.0),
                ControlPoint(number = 32, latitude = Math.toDegrees(dLatRad), longitude = 0.0),
            ),
        )
        val a = ParticipantWithResult(
            participant = participant("A"),
            result = result(
                "A", ResultStatus.FINISHED, totalTime = 300,
                // leg 31->32: 300 секунд на 1000м = 5 мин/км
                splits = listOf(SplitTime(31, 100_000), SplitTime(32, 400_000)),
            ),
        )

        val table = buildSplitsTable(group(listOf(a)), distance)
        val row = table.rows[0]

        assertNull(row.cells[0].paceMinPerKm) // нет координаты до первого КП
        assertEquals(5.0, row.cells[1].paceMinPerKm!!, 0.001)
    }

    @Test
    fun `pace on first and finish legs uses start and finish coordinates`() {
        // Точки через каждые 1000м строго на север: старт -> 31 -> 32 -> финиш 100.
        val kmLat = Math.toDegrees(1000.0 / 6_371_000.0)
        val distance = Distance(
            competitionId = "c1",
            lengthMeters = 3000,
            climbMeters = 0,
            controlsCount = 2,
            controlPoints = listOf(
                ControlPoint(number = 31, latitude = kmLat, longitude = 0.0),
                ControlPoint(number = 32, latitude = 2 * kmLat, longitude = 0.0),
            ),
            finishControlPoint = 100,
            startPosition = Coordinates(0.0, 0.0),
            finishPosition = Coordinates(3 * kmLat, 0.0),
        )
        val a = ParticipantWithResult(
            participant = participant("A"),
            result = result(
                "A", ResultStatus.FINISHED, totalTime = 900,
                // старт->31: 240с, 31->32: 300с, 32->финиш: 360с
                splits = listOf(SplitTime(31, 240_000), SplitTime(32, 540_000), SplitTime(100, 900_000)),
            ),
        )

        val row = buildSplitsTable(group(listOf(a)), distance).rows[0]

        assertEquals(4.0, row.cells[0].paceMinPerKm!!, 0.001)
        assertEquals(5.0, row.cells[1].paceMinPerKm!!, 0.001)
        assertEquals(6.0, row.cells[2].paceMinPerKm!!, 0.001)
    }

    @Test
    fun `by choice distance includes legs from start and to finish when coordinates are known`() {
        val kmLat = Math.toDegrees(1000.0 / 6_371_000.0)
        val distance = Distance(
            competitionId = "c1",
            lengthMeters = 3000,
            climbMeters = 0,
            controlsCount = 2,
            controlPoints = listOf(
                ControlPoint(number = 31, latitude = kmLat, longitude = 0.0),
                ControlPoint(number = 32, latitude = 2 * kmLat, longitude = 0.0),
            ),
            finishControlPoint = 100,
            startPosition = Coordinates(0.0, 0.0),
            finishPosition = Coordinates(3 * kmLat, 0.0),
        )
        val splits = listOf(SplitTime(31, 240_000), SplitTime(32, 540_000), SplitTime(100, 900_000))
        val a = ParticipantWithResult(participant("A"), result("A", ResultStatus.FINISHED, totalTime = 900, splits = splits))

        val withGeo = buildSplitsTable(group(listOf(a)), distance, OrienteeringDirection.BY_CHOICE)
        assertEquals(3000.0, withGeo.rows[0].totalDistanceMeters!!, 0.01)

        val noStartFinish = distance.copy(startPosition = null, finishPosition = null)
        val withoutGeo = buildSplitsTable(group(listOf(a)), noStartFinish, OrienteeringDirection.BY_CHOICE)
        assertEquals(1000.0, withoutGeo.rows[0].totalDistanceMeters!!, 0.01)
    }

    @Test
    fun `pace stays null when distance has no coordinates`() {
        val distance = Distance(
            competitionId = "c1",
            lengthMeters = 1000,
            climbMeters = 0,
            controlsCount = 2,
            controlPoints = listOf(ControlPoint(number = 31), ControlPoint(number = 32)),
        )
        val a = ParticipantWithResult(
            participant = participant("A"),
            result = result(
                "A", ResultStatus.FINISHED, totalTime = 200,
                splits = listOf(SplitTime(31, 100_000), SplitTime(32, 200_000)),
            ),
        )

        val table = buildSplitsTable(group(listOf(a)), distance)

        assertNull(table.rows[0].cells[0].paceMinPerKm)
        assertNull(table.rows[0].cells[1].paceMinPerKm)
    }

    @Test
    fun `sortedForResults orders by status then by total time`() {
        val finishedSlow = ParticipantWithResult(participant("slow"), result("slow", ResultStatus.FINISHED, totalTime = 500))
        val finishedFast = ParticipantWithResult(participant("fast"), result("fast", ResultStatus.FINISHED, totalTime = 300))
        val dsq = ParticipantWithResult(participant("dsq"), result("dsq", ResultStatus.DSQ))
        val dnf = ParticipantWithResult(participant("dnf"), result("dnf", ResultStatus.DNF))
        val dns = ParticipantWithResult(participant("dns"), result("dns", ResultStatus.DNS))
        val started = ParticipantWithResult(participant("started"), result("started", ResultStatus.STARTED))
        val registered = ParticipantWithResult(participant("registered"), result("registered", ResultStatus.REGISTERED))
        val noResult = ParticipantWithResult(participant("none"), result = null)

        val sorted = listOf(noResult, registered, started, dns, dnf, dsq, finishedSlow, finishedFast)
            .sortedForResults()

        assertEquals(
            listOf("fast", "slow", "dsq", "dnf", "dns", "started", "registered", "none"),
            sorted.map { it.participant.id },
        )
    }
}
