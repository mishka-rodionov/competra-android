package com.competra.domain.models.cyclic_event

import com.competra.domain.models.Gender
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Результат проверки, может ли пользователь зарегистрироваться в группу.
 */
sealed interface GroupEligibility {
    data object Eligible : GroupEligibility

    /**
     * @property reason Текст причины для пользователя (совпадает с ответом сервера).
     * @property fixInProfile Причину можно устранить, заполнив/исправив профиль.
     */
    data class NotEligible(val reason: String, val fixInProfile: Boolean) : GroupEligibility
}

/**
 * Ограничение группы по полу. Поле gender у групп клиенты пишут в разных форматах:
 * веб — "M"/"F", Android — "MALE"/"FEMALE"/"MIXED". Null, "MIXED" и прочее — без ограничения.
 * Та же логика — groupGenderRestriction в eSport (GroupEligibility.kt) и в веб-клиенте.
 */
fun groupGenderRestriction(gender: String?): Gender? = when (gender?.trim()?.uppercase()) {
    "M", "MALE" -> Gender.MALE
    "F", "FEMALE" -> Gender.FEMALE
    else -> null
}

/**
 * Год рождения из birthDate (полночь в мс). Сдвиг на +12 ч перед взятием года в UTC корректен
 * и для UTC-полуночи DatePicker'а, и для локальной полуночи старых клиентов.
 */
private fun birthYear(birthDate: Long): Int =
    Instant.ofEpochMilli(birthDate).plusSeconds(12 * 3600).atZone(ZoneOffset.UTC).year

/** Год соревнования в его часовом поясе; неизвестный пояс — UTC. */
fun competitionYear(startDate: Long, timeZoneId: String?): Int {
    val zone = try {
        ZoneId.of(timeZoneId ?: "UTC")
    } catch (e: DateTimeException) {
        ZoneOffset.UTC
    }
    return Instant.ofEpochMilli(startDate).atZone(zone).year
}

/**
 * Проверяет, может ли пользователь зарегистрироваться в группу по полу и возрасту.
 * Возраст — по году рождения, как принято в ориентировании: год соревнования − год рождения.
 * minAge/maxAge ≤ 0 — без ограничения. Сервер (eSport, register) проверяет то же самое и с теми
 * же текстами — здесь проверка нужна, чтобы не показывать кнопку, которая закончится ошибкой.
 *
 * @param userGender Пол из профиля. Сервер отдаёт MALE пользователям, не указавшим пол, поэтому
 * при несовпадении тоже предлагаем исправить профиль.
 * @param userBirthDate Дата рождения в мс; null или 0 — не указана.
 */
fun checkGroupEligibility(
    groupTitle: String,
    groupGender: String?,
    minAge: Int?,
    maxAge: Int?,
    userGender: Gender?,
    userBirthDate: Long?,
    competitionYear: Int,
): GroupEligibility {
    val requiredGender = groupGenderRestriction(groupGender)
    if (requiredGender != null) {
        val gender = userGender?.takeIf { it != Gender.MIXED }
            ?: return GroupEligibility.NotEligible(
                "Укажите пол в профиле, чтобы зарегистрироваться в группу $groupTitle",
                fixInProfile = true
            )
        if (gender != requiredGender) {
            val who = if (requiredGender == Gender.MALE) "мужчин" else "женщин"
            return GroupEligibility.NotEligible("Группа $groupTitle — только для $who", fixInProfile = true)
        }
    }

    val min = minAge?.takeIf { it > 0 }
    val max = maxAge?.takeIf { it > 0 }
    if (min == null && max == null) return GroupEligibility.Eligible

    val born = userBirthDate?.takeIf { it != 0L }?.let(::birthYear)
        ?: return GroupEligibility.NotEligible(
            "Укажите дату рождения в профиле, чтобы зарегистрироваться в группу $groupTitle",
            fixInProfile = true
        )
    val age = competitionYear - born
    if ((min != null && age < min) || (max != null && age > max)) {
        return GroupEligibility.NotEligible(
            "Группа $groupTitle — для участников ${birthYearsRange(min, max, competitionYear)}, ваш год рождения — $born",
            fixInProfile = false
        )
    }
    return GroupEligibility.Eligible
}

/** Возрастной диапазон группы в годах рождения: «2011–2012 г.р.», «2012 г.р. и моложе»; null — без ограничения. */
fun birthYearsRange(minAge: Int?, maxAge: Int?, competitionYear: Int): String? {
    val oldest = maxAge?.takeIf { it > 0 }?.let { competitionYear - it }
    val youngest = minAge?.takeIf { it > 0 }?.let { competitionYear - it }
    return when {
        oldest != null && youngest != null ->
            if (oldest == youngest) "$oldest г.р." else "$oldest–$youngest г.р."
        youngest != null -> "$youngest г.р. и старше"
        oldest != null -> "$oldest г.р. и моложе"
        else -> null
    }
}
