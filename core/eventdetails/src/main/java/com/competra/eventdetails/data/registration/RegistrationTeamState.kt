package com.competra.eventdetails.data.registration

import com.competra.domain.models.cyclic_event.ClubMatch
import com.competra.domain.models.cyclic_event.RegistrationTeamOption
import com.competra.domain.models.cyclic_event.RegistrationTeamOptions
import com.competra.domain.models.cyclic_event.TeamSuggestion
import com.competra.domain.models.cyclic_event.ownOptionFor
import com.competra.domain.models.cyclic_event.suggestionsFor

/**
 * Состояние поля «Клуб/команда» в шторке регистрации на соревнование.
 *
 * @param commandName Название клуба/команды участника (свободный текст, опционально).
 * @param isCommandNameEdited Пользователь сам менял поле команды — автоподстановка его не перетирает.
 * @param teamOptions Подсказки для поля команды: свои команды/клубы и подписи из протокола.
 * @param clubMatches Клубы с названием, совпавшим с подписью, где пользователь не состоит.
 */
data class RegistrationTeamState(
    val commandName: String = "",
    val isCommandNameEdited: Boolean = false,
    val teamOptions: RegistrationTeamOptions = RegistrationTeamOptions(),
    val clubMatches: List<ClubMatch> = emptyList()
) {

    /** Подсказки под полем «Команда» для текущего ввода. */
    val teamSuggestions: List<TeamSuggestion>
        get() = teamOptions.suggestionsFor(commandName)

    /** Своя команда/клуб, чья подпись сейчас в поле (null — свободный текст). */
    val selectedTeamOption: RegistrationTeamOption?
        get() = teamOptions.ownOptionFor(commandName)
}
