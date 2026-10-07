package com.competra.eventdetails.presentation.registration

import com.competra.domain.models.cyclic_event.COMMAND_NAME_MAX_LENGTH
import com.competra.domain.models.cyclic_event.normalizeCommandName
import com.competra.domain.models.cyclic_event.ownOptionFor
import com.competra.domain.repository.events.CyclicEventDetailsRepository
import com.competra.eventdetails.data.registration.RegistrationTeamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Логика поля «Клуб/команда» при регистрации на соревнование: загрузка подсказок с
 * автоподстановкой и поиск клуба с таким же названием. Общая для шторок регистрации на деталях
 * события и на экране группы.
 *
 * @param repository Репозиторий деталей события (подсказки команд и поиск клубов).
 * @param scope Скоуп вьюмодели-владельца.
 */
class RegistrationTeamDelegate(
    private val repository: CyclicEventDetailsRepository,
    private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow(RegistrationTeamState())

    /** Текущее состояние поля команды. */
    val state: StateFlow<RegistrationTeamState> = _state.asStateFlow()

    private var teamOptionsJob: Job? = null
    private var clubMatchJob: Job? = null

    /** Подгружает подсказки для поля «Команда» и подставляет предложенную подпись. */
    fun load(eventId: String) {
        teamOptionsJob?.cancel()
        teamOptionsJob = scope.launch {
            // Ошибка не критична: поле остаётся обычным текстовым, без подсказок.
            repository.getRegistrationTeamOptions(eventId)
                .onSuccess { options ->
                    _state.update {
                        it.copy(
                            teamOptions = options,
                            // Не перетираем то, что пользователь успел ввести, пока шёл запрос.
                            commandName = if (it.isCommandNameEdited) it.commandName else options.suggestedCommandName.orEmpty()
                        )
                    }
                    scheduleClubMatch()
                }
        }
    }

    /** Пользователь ввёл подпись или выбрал подсказку. */
    fun changeCommandName(commandName: String) {
        _state.update {
            it.copy(
                commandName = commandName.take(COMMAND_NAME_MAX_LENGTH),
                isCommandNameEdited = true,
                clubMatches = emptyList()
            )
        }
        scheduleClubMatch()
    }

    /** Сбрасывает поле — при закрытии шторки или после регистрации. */
    fun reset() {
        teamOptionsJob?.cancel()
        clubMatchJob?.cancel()
        _state.update { RegistrationTeamState() }
    }

    /**
     * Ищет клуб с названием, совпадающим с подписью команды, чтобы предложить вступить в него.
     * Не ищет, если подпись — своя команда/клуб. Запрос откладывается, пока пользователь печатает.
     */
    private fun scheduleClubMatch() {
        clubMatchJob?.cancel()
        val current = _state.value
        val commandName = current.commandName
        val normalized = normalizeCommandName(commandName) ?: return
        if (normalized.length < CLUB_MATCH_MIN_LENGTH) return
        if (current.teamOptions.ownOptionFor(commandName) != null) return
        clubMatchJob = scope.launch {
            delay(CLUB_MATCH_DEBOUNCE_MS)
            repository.matchClubs(normalized)
                .onSuccess { matches ->
                    _state.update { if (it.commandName == commandName) it.copy(clubMatches = matches) else it }
                }
        }
    }

    private companion object {
        /** Пауза после последнего ввода перед поиском клуба по названию. */
        const val CLUB_MATCH_DEBOUNCE_MS = 500L

        /** Короче — не ищем клуб: слишком много случайных совпадений. */
        const val CLUB_MATCH_MIN_LENGTH = 2
    }
}
