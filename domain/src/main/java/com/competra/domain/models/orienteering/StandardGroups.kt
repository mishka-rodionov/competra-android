package com.competra.domain.models.orienteering

import com.competra.domain.models.Gender

/**
 * Пол и возраст, выведенные из принятого обозначения группы («М21», «Ж14», «МЖ35»).
 *
 * Возраст — как в [com.competra.domain.models.cyclic_event.checkGroupEligibility]: год
 * соревнования − год рождения.
 *
 * @property gender пол группы; null — без ограничения («МЖ», Open).
 * @property minAge минимальный возраст (ветеранские группы: «М35» — 35 и старше).
 * @property maxAge максимальный возраст (детские и юношеские: «Ж14» — 14 и моложе).
 */
data class GroupTitleInfo(
    val gender: Gender?,
    val minAge: Int?,
    val maxAge: Int?
)

/**
 * Шаблоны и разбор названий групп для мастера создания соревнования.
 * Тот же набор и те же правила — в веб-клиенте (`competra-web-ts/src/lib/standardGroups.ts`).
 */
object StandardGroups {

    /** Стандартные группы в порядке показа: дети и юноши, основные, ветераны, Open. */
    val TITLES: List<String> = listOf(
        "М10", "Ж10", "М12", "Ж12", "М14", "Ж14", "М16", "Ж16", "М18", "Ж18",
        "М21", "Ж21",
        "М35", "Ж35", "М45", "Ж45", "М55", "Ж55", "М65", "Ж65",
        "Open"
    )

    /** Возраст основной группы: «М21»/«Ж21» — без ограничения по возрасту. */
    private const val ELITE_AGE = 21

    private val TITLE_REGEX = Regex("""^\s*(МЖ|MW|ЖМ|WM|М|M|Ж|W|F|Д)\s*(\d{1,2})""", RegexOption.IGNORE_CASE)

    /**
     * Разбирает название группы. Детские и юношеские (меньше 21) — «до N лет», ветеранские
     * (больше 21) — «N лет и старше», 21 — без возраста. Латинские M/W/F понимаются так же,
     * как кириллические М/Ж.
     *
     * @return null, если название не похоже на обозначение группы (например, «Open», «Новички»).
     */
    fun parseTitle(title: String): GroupTitleInfo? {
        val match = TITLE_REGEX.find(title) ?: return null
        val gender = when (match.groupValues[1].uppercase()) {
            "М", "M" -> Gender.MALE
            "Ж", "W", "F", "Д" -> Gender.FEMALE
            else -> null
        }
        val age = match.groupValues[2].toInt()
        return when {
            age < ELITE_AGE -> GroupTitleInfo(gender, minAge = null, maxAge = age)
            age > ELITE_AGE -> GroupTitleInfo(gender, minAge = age, maxAge = null)
            else -> GroupTitleInfo(gender, minAge = null, maxAge = null)
        }
    }

    /**
     * Дистанция для группы по совпадению названий: Mapper часто называет дистанции так же,
     * как группы («М21», «Ж14»). Сравнение без учёта регистра, пробелов и раскладки М/M, Ж/W.
     *
     * @return подходящая дистанция или null.
     */
    fun matchDistance(groupTitle: String, distances: List<Distance>): Distance? {
        val key = normalize(groupTitle)
        return distances.firstOrNull { d -> d.name?.let(::normalize) == key }
    }

    private fun normalize(value: String): String = value.uppercase()
        .replace(" ", "")
        .replace('M', 'М')
        .replace('W', 'Ж')
}
