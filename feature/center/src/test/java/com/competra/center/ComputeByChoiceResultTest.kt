package com.competra.center

import com.competra.center.presentation.read_card.computeByChoiceResult
import com.competra.domain.models.Gender
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.ControlPoint
import com.competra.domain.models.orienteering.ControlPointRole
import com.competra.domain.models.orienteering.SplitTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Проверка расчёта результата для формата "по выбору" (score-О) — [computeByChoiceResult].
 */
class ComputeByChoiceResultTest {

    private val startTime = 0L

    private fun group(
        timeLimitMinutes: Int? = null,
        scorePenaltyPerMinute: Int? = null,
        maxLatenessMinutes: Int? = null,
    ) = ParticipantGroup(
        groupId = 1L,
        competitionId = "c1",
        title = "M21",
        gender = Gender.MALE,
        distanceId = 1L,
        timeLimitMinutes = timeLimitMinutes,
        scorePenaltyPerMinute = scorePenaltyPerMinute,
        maxLatenessMinutes = maxLatenessMinutes,
    )

    private fun cp(number: Int, score: Int = 0, role: ControlPointRole = ControlPointRole.ORDINARY) =
        ControlPoint(number = number, role = role, score = score)

    private fun split(cp: Int, timestampSeconds: Long) = SplitTime(cp, timestampSeconds * 1000L)

    @Test
    fun `любой порядок взятия КП валиден`() {
        val expected = listOf(cp(31, score = 10), cp(32, score = 20), cp(33, score = 30))
        // Порядок в чипе не совпадает с порядком в expected — это допустимо для BY_CHOICE.
        val actual = listOf(split(33, 60), split(31, 120), split(32, 180))

        val result = computeByChoiceResult(expected, actual, startTime, group())

        assertEquals(ResultStatus.FINISHED, result.status)
        assertEquals(60, result.totalScore)
    }

    @Test
    fun `отметка финиша до старта не вытесняет настоящий финиш`() {
        val start = 1000L
        val expected = listOf(cp(31, score = 10), cp(100, role = ControlPointRole.FINISH))
        // Участник отметил финишную станцию в стартовом городке до своего старта.
        val actual = listOf(split(100, 900), split(31, 1060), split(100, 1300))

        val result = computeByChoiceResult(expected, actual, start * 1000L, group())

        assertEquals(ResultStatus.FINISHED, result.status)
        assertEquals(listOf(split(31, 1060), split(100, 1300)), result.validSplits)
        assertEquals(10, result.totalScore)
    }

    @Test
    fun `КП отмеченный до старта не приносит очков`() {
        val start = 1000L
        val expected = listOf(cp(31, score = 10), cp(32, score = 20))
        val actual = listOf(split(31, 900), split(32, 1100))

        val result = computeByChoiceResult(expected, actual, start * 1000L, group())

        assertEquals(20, result.totalScore)
    }

    @Test
    fun `повторная отметка одного КП считается один раз`() {
        val expected = listOf(cp(31, score = 10), cp(32, score = 20))
        val actual = listOf(split(31, 60), split(31, 90), split(32, 120))

        val result = computeByChoiceResult(expected, actual, startTime, group())

        assertEquals(ResultStatus.FINISHED, result.status)
        assertEquals(30, result.totalScore)
    }

    @Test
    fun `лишние отметки не входящие в дистанцию игнорируются`() {
        val expected = listOf(cp(31, score = 10))
        val actual = listOf(split(31, 60), split(99, 90))

        val result = computeByChoiceResult(expected, actual, startTime, group())

        assertEquals(ResultStatus.FINISHED, result.status)
        assertEquals(10, result.totalScore)
    }

    @Test
    fun `непройденный обязательный КП дисквалифицирует`() {
        val expected = listOf(
            cp(31, score = 10, role = ControlPointRole.REQUIRED),
            cp(32, score = 20)
        )
        val actual = listOf(split(32, 60))

        val result = computeByChoiceResult(expected, actual, startTime, group())

        assertEquals(ResultStatus.DSQ, result.status)
    }

    @Test
    fun `без лимита времени штраф не применяется`() {
        val expected = listOf(cp(31, score = 10))
        val actual = listOf(split(31, 10_000))

        val result = computeByChoiceResult(expected, actual, startTime, group(timeLimitMinutes = null))

        assertEquals(ResultStatus.FINISHED, result.status)
        assertEquals(10, result.totalScore)
        assertEquals(0, result.scorePenalty)
    }

    @Test
    fun `опоздание в пределах порога уменьшает баллы на штраф`() {
        val expected = listOf(cp(31, score = 100))
        // Лимит 10 минут, финиш на 12-й минуте — опоздание 2 минуты.
        val actual = listOf(split(31, timestampSeconds = 12 * 60L))

        val result = computeByChoiceResult(
            expected, actual, startTime,
            group(timeLimitMinutes = 10, scorePenaltyPerMinute = 5, maxLatenessMinutes = 30)
        )

        assertEquals(ResultStatus.FINISHED, result.status)
        assertEquals(10, result.scorePenalty)
        assertEquals(90, result.totalScore)
    }

    @Test
    fun `опоздание сверх порога обнуляет баллы но статус остаётся FINISHED`() {
        val expected = listOf(cp(31, score = 100))
        // Лимит 10 минут, порог сильного опоздания 15 минут, финиш на 40-й минуте.
        val actual = listOf(split(31, timestampSeconds = 40 * 60L))

        val result = computeByChoiceResult(
            expected, actual, startTime,
            group(timeLimitMinutes = 10, scorePenaltyPerMinute = 5, maxLatenessMinutes = 15)
        )

        assertEquals(ResultStatus.FINISHED, result.status)
        assertEquals(0, result.totalScore)
    }

    @Test
    fun `штраф не уводит итоговый счёт ниже нуля`() {
        val expected = listOf(cp(31, score = 5))
        val actual = listOf(split(31, timestampSeconds = 60 * 60L))

        val result = computeByChoiceResult(
            expected, actual, startTime,
            group(timeLimitMinutes = 1, scorePenaltyPerMinute = 100, maxLatenessMinutes = null)
        )

        assertEquals(ResultStatus.FINISHED, result.status)
        assertEquals(0, result.totalScore)
    }
}
