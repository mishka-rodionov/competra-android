package com.competra.domain.repository.events

import com.competra.domain.models.cyclic_event.ClubMatch
import com.competra.domain.models.cyclic_event.CyclicEventDetails
import com.competra.domain.models.cyclic_event.RegistrationTeamOptions
import com.competra.domain.models.orienteering.OrienteeringParticipant

/**
 * Репозиторий для получения деталей циклического события.
 */
interface CyclicEventDetailsRepository {

    /**
     * Получить детали события.
     * @param eventId Идентификатор события.
     * @param userId ID текущего пользователя для проверки регистрации (null = не проверять).
     */
    suspend fun getEventDetails(eventId: String, userId: String? = null): Result<CyclicEventDetails?>

    /**
     * Получить список участников группы события.
     * @param eventId Идентификатор события.
     * @param groupId Идентификатор группы.
     */
    suspend fun getParticipants(eventId: String, groupId: String): Result<List<OrienteeringParticipant>>

    /**
     * Зарегистрировать текущего пользователя в группу события.
     * @param eventId Идентификатор события (competitionId на сервере).
     * @param groupId Идентификатор группы.
     * @param firstName Имя пользователя.
     * @param lastName Фамилия пользователя.
     * @param commandName Название клуба/команды участника (опционально, свободный текст или выбор из своих клубов/команд).
     * @param teamId Клубная команда пользователя, если подпись выбрана из его команд.
     */
    suspend fun registerToEvent(
        eventId: String,
        groupId: String,
        firstName: String,
        lastName: String,
        commandName: String? = null,
        teamId: String? = null
    ): Result<Unit>

    /**
     * Подсказки для поля «Команда» при регистрации: свои клубные команды по виду спорта события,
     * подписи из протокола и подпись для автоподстановки. Требует авторизации.
     * @param eventId Идентификатор события.
     */
    suspend fun getRegistrationTeamOptions(eventId: String): Result<RegistrationTeamOptions>

    /**
     * Клубы, чьё название совпадает с подписью команды и в которых пользователь не состоит.
     * @param commandName Подпись команды из поля ввода.
     */
    suspend fun matchClubs(commandName: String): Result<List<ClubMatch>>

    /**
     * Отменить регистрацию текущего пользователя на событие.
     * @param eventId Идентификатор события.
     */
    suspend fun cancelRegistration(eventId: String): Result<Unit>

}
