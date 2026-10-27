package com.competra.domain.models.orienteering

/**
 * Одно участие человека в прошлом соревновании организатора — сырьё для [KnownParticipant].
 *
 * @property id Id записи участника (UUID, одинаковый на клиенте и сервере).
 * @property competitionId Соревнование, в котором участвовал.
 * @property userId Пользователь Competra, если участник регистрировался сам; пусто — внесён вручную.
 * @property firstName Имя.
 * @property lastName Фамилия.
 * @property groupName Группа, в которой участвовал.
 * @property commandName Подпись команды в протоколе; пусто — без команды.
 * @property competitionDate Дата начала соревнования (Unix ms).
 */
data class PastParticipation(
    val id: String,
    val competitionId: String,
    val userId: String,
    val firstName: String,
    val lastName: String,
    val groupName: String,
    val commandName: String,
    val competitionDate: Long
)

/**
 * Человек из истории соревнований организатора — подсказка при ручном добавлении участника.
 * Данные (команда, группа) берутся из самого свежего участия.
 *
 * @property userId Пользователь Competra; пусто — человек ни разу не регистрировался сам.
 * @property firstName Имя.
 * @property lastName Фамилия.
 * @property commandName Команда последнего участия; пусто — без команды.
 * @property lastGroupName Группа последнего участия.
 * @property lastCompetitionDate Дата последнего соревнования (Unix ms).
 * @property participationsCount Сколько раз участвовал.
 */
data class KnownParticipant(
    val userId: String,
    val firstName: String,
    val lastName: String,
    val commandName: String,
    val lastGroupName: String,
    val lastCompetitionDate: Long,
    val participationsCount: Int
)

/**
 * Склейка истории участников и поиск подсказок для диалога «Новый участник».
 */
object ParticipantHistory {

    private val WHITESPACE = Regex("\\s+")

    /** Имя для сравнения: без регистра, лишних пробелов и с «ё» → «е». */
    fun normalizeName(raw: String): String =
        raw.trim().lowercase().replace('ё', 'е').replace(WHITESPACE, " ")

    private fun nameKey(firstName: String, lastName: String): String =
        "${normalizeName(lastName)}|${normalizeName(firstName)}"

    /**
     * Объединяет локальную и серверную историю. Одна запись участника (по [PastParticipation.id])
     * берётся один раз, локальная версия важнее: в ней могут быть ещё не выгруженные правки.
     * Локальные участия покрывают соревнования, которых ещё нет на сервере, серверные — те,
     * что не загружены на это устройство.
     */
    fun merge(local: List<PastParticipation>, server: List<PastParticipation>): List<PastParticipation> {
        val localIds = local.mapTo(HashSet()) { it.id }
        return local + server.filterNot { it.id in localIds }
    }

    /**
     * Склеивает участия в людей и сортирует: сначала частые, затем недавние.
     *
     * Один человек — один `userId`; участия без `userId` склеиваются по имени и фамилии.
     * Если такое имя ровно у одного пользователя, участия без `userId` присоединяются к нему —
     * организатор вносил его вручную до того, как тот зарегистрировался сам. Однофамильцев
     * с разными `userId` не склеиваем.
     */
    fun aggregate(participations: List<PastParticipation>): List<KnownParticipant> {
        val valid = participations.filter { it.firstName.isNotBlank() && it.lastName.isNotBlank() }
        val (withUser, withoutUser) = valid.partition { it.userId.isNotBlank() }

        val byUser = withUser.groupBy { it.userId }.mapValues { it.value.toMutableList() }
        val userIdsByName = byUser.entries
            .groupBy({ (_, list) -> nameKey(list.first().firstName, list.first().lastName) }, { it.key })

        val anonymous = mutableMapOf<String, MutableList<PastParticipation>>()
        withoutUser.forEach { p ->
            val key = nameKey(p.firstName, p.lastName)
            val owners = userIdsByName[key]
            if (owners?.size == 1) {
                byUser.getValue(owners.single()).add(p)
            } else {
                anonymous.getOrPut(key) { mutableListOf() }.add(p)
            }
        }

        return (byUser.values + anonymous.values)
            .map { list ->
                val latest = list.maxBy { it.competitionDate }
                KnownParticipant(
                    userId = list.firstOrNull { it.userId.isNotBlank() }?.userId.orEmpty(),
                    firstName = latest.firstName.trim(),
                    lastName = latest.lastName.trim(),
                    commandName = latest.commandName.trim(),
                    lastGroupName = latest.groupName,
                    lastCompetitionDate = latest.competitionDate,
                    participationsCount = list.size
                )
            }
            .sortedWith(
                compareByDescending<KnownParticipant> { it.participationsCount }
                    .thenByDescending { it.lastCompetitionDate }
                    .thenBy { normalizeName(it.lastName) }
                    .thenBy { normalizeName(it.firstName) }
            )
    }

    /**
     * Убирает из истории тех, кто уже есть в протоколе текущего соревнования: совпал `userId`
     * или имя с фамилией.
     */
    fun excludeProtocol(
        history: List<KnownParticipant>,
        protocol: List<OrienteeringParticipant>
    ): List<KnownParticipant> {
        val userIds = protocol.mapNotNull { it.userId.takeIf(String::isNotBlank) }.toSet()
        val names = protocol.map { nameKey(it.firstName, it.lastName) }.toSet()
        return history.filterNot {
            (it.userId.isNotBlank() && it.userId in userIds) || nameKey(it.firstName, it.lastName) in names
        }
    }

    /**
     * Подсказки по введённому тексту: каждое слово запроса — начало имени или фамилии.
     * Запрос короче [MIN_QUERY_LENGTH] символов подсказок не даёт. Порядок — как в [history].
     */
    fun search(history: List<KnownParticipant>, query: String, limit: Int = DEFAULT_LIMIT): List<KnownParticipant> {
        if (normalizeName(query).replace(" ", "").length < MIN_QUERY_LENGTH) return emptyList()
        return filter(history, query).take(limit)
    }

    /**
     * Список для массового выбора: пустой запрос — вся история, иначе каждое слово запроса —
     * начало имени или фамилии. Порядок — как в [history].
     */
    fun filter(history: List<KnownParticipant>, query: String): List<KnownParticipant> {
        val normalized = normalizeName(query)
        if (normalized.isEmpty()) return history
        val tokens = normalized.split(' ')
        return history.filter { known ->
            val words = (normalizeName(known.lastName) + " " + normalizeName(known.firstName)).split(' ')
            tokens.all { token -> words.any { it.startsWith(token) } }
        }
    }

    /**
     * Поднимает наверх тех, чья последняя группа совпадает с [groupName] (например, при добавлении
     * в М21 — сначала бегавшие в М21). Внутри обеих частей порядок [history] сохраняется.
     */
    fun prioritizeGroup(history: List<KnownParticipant>, groupName: String): List<KnownParticipant> {
        val target = normalizeGroupName(groupName)
        if (target.isEmpty()) return history
        return history.sortedByDescending { normalizeGroupName(it.lastGroupName) == target }
    }

    /**
     * Название группы для сравнения: без регистра и пробелов, латинские буквы-двойники заменены
     * кириллическими — «M21» (латиница) и «М21» (кириллица) считаются одной группой.
     */
    fun normalizeGroupName(raw: String): String =
        raw.lowercase().replace(WHITESPACE, "").map { LATIN_TO_CYRILLIC[it] ?: it }.joinToString("")

    private val LATIN_TO_CYRILLIC = mapOf(
        'a' to 'а', 'b' to 'в', 'c' to 'с', 'e' to 'е', 'h' to 'н', 'k' to 'к', 'm' to 'м',
        'o' to 'о', 'p' to 'р', 't' to 'т', 'x' to 'х', 'y' to 'у'
    )

    /**
     * Устойчивый ключ человека: `userId` или фамилия с именем. Не меняется, когда к локальной
     * истории добавляется серверная, — по нему держится отметка в массовом выборе.
     */
    fun keyOf(known: KnownParticipant): String =
        known.userId.ifBlank { nameKey(known.firstName, known.lastName) }

    /** Минимальная длина запроса, с которой показываются подсказки. */
    const val MIN_QUERY_LENGTH = 2

    /** Сколько подсказок показывать по умолчанию. */
    const val DEFAULT_LIMIT = 5
}
