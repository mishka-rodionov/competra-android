package com.competra.domain.models.cyclic_event

/** Максимальная длина подписи команды (ограничение колонки на сервере). */
const val COMMAND_NAME_MAX_LENGTH = 200

private val WHITESPACE = Regex("\\s+")

/**
 * Вариант подписи команды при регистрации: клубная команда пользователя либо клуб, в котором у
 * пользователя нет команды по виду спорта соревнования.
 *
 * @property teamId Идентификатор команды; null — вариант «только клуб».
 * @property clubId Идентификатор клуба.
 * @property clubName Название клуба.
 * @property teamName Название команды; null — вариант «только клуб».
 * @property label Подпись для протокола: «Клуб (Команда)» или название клуба.
 */
data class RegistrationTeamOption(
    val teamId: String?,
    val clubId: String,
    val clubName: String,
    val teamName: String?,
    val label: String
)

/**
 * Подсказки для поля «Команда» при регистрации на соревнование.
 *
 * @property options Клубные команды и клубы пользователя.
 * @property protocolNames Подписи команд, уже встречающиеся в протоколе соревнования.
 * @property suggestedCommandName Что подставить в поле сразу; null — оставить пустым.
 */
data class RegistrationTeamOptions(
    val options: List<RegistrationTeamOption> = emptyList(),
    val protocolNames: List<String> = emptyList(),
    val suggestedCommandName: String? = null
)

/**
 * Клуб, чьё название совпало с введённой подписью команды и в котором пользователь не состоит.
 *
 * @property id Идентификатор клуба.
 * @property name Название клуба.
 * @property allowJoinRequests Принимает ли клуб заявки на вступление.
 */
data class ClubMatch(
    val id: String,
    val name: String,
    val allowJoinRequests: Boolean
)

/** Откуда взялась подпись команды при регистрации — для аналитики. */
enum class TeamSource {
    /** Клубная команда пользователя (teamId отправлен на сервер). */
    CLUB_TEAM,

    /** Клуб пользователя без команды. */
    CLUB,

    /** Подпись, уже встречающаяся в протоколе соревнования. */
    PROTOCOL,

    /** Свободный текст. */
    CUSTOM,

    /** Поле пустое. */
    NONE
}

/** Элемент выпадающего списка подсказок поля «Команда». */
sealed interface TeamSuggestion {
    /** Подпись, которая подставится в поле. */
    val label: String

    /** Клубная команда или клуб пользователя. */
    data class Own(val option: RegistrationTeamOption) : TeamSuggestion {
        override val label: String get() = option.label
    }

    /** Подпись из протокола соревнования. */
    data class Protocol(override val label: String) : TeamSuggestion
}

/** Подпись без крайних и повторных пробелов; пустая строка → null. */
fun normalizeCommandName(raw: String?): String? =
    raw?.trim()?.replace(WHITESPACE, " ")?.takeIf { it.isNotEmpty() }

/** true, если подписи совпадают с точностью до регистра и пробелов. */
fun sameCommandName(a: String?, b: String?): Boolean =
    normalizeCommandName(a).equals(normalizeCommandName(b), ignoreCase = true)

/** Свой вариант (команда или клуб), чья подпись совпадает с [commandName], иначе null. */
fun RegistrationTeamOptions.ownOptionFor(commandName: String): RegistrationTeamOption? {
    if (normalizeCommandName(commandName) == null) return null
    return options.firstOrNull { sameCommandName(it.label, commandName) }
}

/**
 * Команда для отправки на сервер: только если подпись в поле совпадает с подписью своей клубной
 * команды. Отредактировал подпись — ссылка на команду пропадает, остаётся свободный текст.
 */
fun RegistrationTeamOptions.teamIdFor(commandName: String): String? = ownOptionFor(commandName)?.teamId

/** Источник подписи [commandName] для аналитики. */
fun RegistrationTeamOptions.teamSourceFor(commandName: String): TeamSource {
    if (normalizeCommandName(commandName) == null) return TeamSource.NONE
    val own = ownOptionFor(commandName)
    return when {
        own?.teamId != null -> TeamSource.CLUB_TEAM
        own != null -> TeamSource.CLUB
        protocolNames.any { sameCommandName(it, commandName) } -> TeamSource.PROTOCOL
        else -> TeamSource.CUSTOM
    }
}

/**
 * Подсказки под полем «Команда»: сначала свои команды/клубы, затем подписи из протокола (без
 * дублей своих). Непустой [query] фильтрует по вхождению без учёта регистра; если подпись в поле
 * уже совпадает с подсказкой целиком, она не показывается.
 */
fun RegistrationTeamOptions.suggestionsFor(query: String, limit: Int = 8): List<TeamSuggestion> {
    val own = options.map { TeamSuggestion.Own(it) }
    val protocol = protocolNames
        .filter { name -> options.none { sameCommandName(it.label, name) } }
        .map { TeamSuggestion.Protocol(it) }
    val needle = normalizeCommandName(query)?.lowercase()
    return (own + protocol)
        .filter { needle == null || it.label.lowercase().contains(needle) }
        .filterNot { sameCommandName(it.label, query) }
        .take(limit)
}
