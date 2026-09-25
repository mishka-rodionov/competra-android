package com.competra.domain.models.cyclic_event

import com.competra.domain.models.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class GroupEligibilityTest {

    private fun utcMidnight(date: String) =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun check(
        groupGender: String? = null,
        minAge: Int? = null,
        maxAge: Int? = null,
        userGender: Gender? = Gender.MALE,
        birthDate: Long? = utcMidnight("2012-06-15"),
    ) = checkGroupEligibility("М14", groupGender, minAge, maxAge, userGender, birthDate, 2026)

    private fun reason(result: GroupEligibility) = (result as GroupEligibility.NotEligible).reason

    @Test
    fun `group gender accepts both web and android formats`() {
        assertEquals(Gender.MALE, groupGenderRestriction("M"))
        assertEquals(Gender.MALE, groupGenderRestriction("MALE"))
        assertEquals(Gender.FEMALE, groupGenderRestriction("F"))
        assertEquals(Gender.FEMALE, groupGenderRestriction("female"))
        assertNull(groupGenderRestriction("MIXED"))
        assertNull(groupGenderRestriction(null))
    }

    @Test
    fun `competition year uses competition time zone`() {
        val millis = utcMidnight("2025-12-31") + 22 * 3600 * 1000L
        assertEquals(2026, competitionYear(millis, "Europe/Moscow"))
        assertEquals(2025, competitionYear(millis, null))
        assertEquals(2025, competitionYear(millis, "not/a-zone"))
    }

    @Test
    fun `group without restrictions accepts anyone`() {
        assertEquals(GroupEligibility.Eligible, check(userGender = null, birthDate = 0L))
        assertEquals(GroupEligibility.Eligible, check(groupGender = "MIXED", minAge = 0, maxAge = 0, birthDate = null))
    }

    @Test
    fun `gender is checked`() {
        assertEquals(GroupEligibility.Eligible, check(groupGender = "M"))
        assertEquals(
            GroupEligibility.NotEligible("Группа М14 — только для женщин", fixInProfile = true),
            check(groupGender = "FEMALE")
        )
        assertEquals(
            "Укажите пол в профиле, чтобы зарегистрироваться в группу М14",
            reason(check(groupGender = "F", userGender = null))
        )
    }

    @Test
    fun `age is counted by birth year`() {
        assertEquals(GroupEligibility.Eligible, check(minAge = 14, maxAge = 14, birthDate = utcMidnight("2012-12-31")))
        val moscowMidnight = LocalDate.parse("2012-01-01").atStartOfDay(ZoneId.of("Europe/Moscow")).toInstant().toEpochMilli()
        assertEquals(GroupEligibility.Eligible, check(minAge = 14, maxAge = 14, birthDate = moscowMidnight))
        assertEquals(
            GroupEligibility.NotEligible(
                "Группа М14 — для участников 2011–2012 г.р., ваш год рождения — 2013",
                fixInProfile = false
            ),
            check(minAge = 14, maxAge = 15, birthDate = utcMidnight("2013-01-01"))
        )
    }

    @Test
    fun `birth dates before 1970 work`() {
        assertEquals(GroupEligibility.Eligible, check(minAge = 60, birthDate = utcMidnight("1960-03-10")))
    }

    @Test
    fun `open-ended ranges and missing birth date`() {
        assertEquals(
            "Группа М14 — для участников 2012 г.р. и моложе, ваш год рождения — 2011",
            reason(check(maxAge = 14, birthDate = utcMidnight("2011-05-05")))
        )
        assertEquals(
            "Группа М14 — для участников 1991 г.р. и старше, ваш год рождения — 2012",
            reason(check(minAge = 35))
        )
        assertEquals(
            "Укажите дату рождения в профиле, чтобы зарегистрироваться в группу М14",
            reason(check(maxAge = 14, birthDate = 0L))
        )
        assertNull(birthYearsRange(null, 0, 2026))
    }
}
