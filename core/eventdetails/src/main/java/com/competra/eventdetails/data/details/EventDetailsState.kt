package com.competra.eventdetails.data.details

import com.competra.domain.models.cyclic_event.ClubMatch
import com.competra.domain.models.cyclic_event.CyclicEventDetails
import com.competra.domain.models.cyclic_event.EventParticipantGroup
import com.competra.domain.models.cyclic_event.GroupEligibility
import com.competra.domain.models.cyclic_event.RegistrationTeamOption
import com.competra.domain.models.cyclic_event.RegistrationTeamOptions
import com.competra.domain.models.cyclic_event.TeamSuggestion
import com.competra.domain.models.cyclic_event.ownOptionFor
import com.competra.domain.models.cyclic_event.suggestionsFor
import com.competra.domain.models.events.EventStatus
import com.competra.domain.models.events.EventType
import com.competra.ui.BaseState

/**
 * Состояние экрана деталей события.
 *
 * @param eventDetails Детали события.
 * @param isRegistrationSheetVisible Видимость BottomSheet регистрации.
 * @param selectedGroup Выбранная группа для регистрации.
 * @param isRegistering Флаг процесса регистрации (загрузка).
 * @param isUserRegistered Флаг того, зарегистрирован ли пользователь на это событие.
 * @param commandName Название клуба/команды участника (свободный текст, опционально).
 * @param isCommandNameEdited Пользователь сам менял поле команды — автоподстановка его не перетирает.
 * @param teamOptions Подсказки для поля команды: свои команды/клубы и подписи из протокола.
 * @param clubMatches Клубы с названием, совпавшим с подписью, где пользователь не состоит.
 * @param organizerClubName Название клуба-организатора, резолвится отдельным запросом по `organizingClubId`.
 * @param liveTrackEntry Что показывать на кнопке онлайн-трека бегуна.
 * @param isLiveTrackConsentVisible Видимость диалога согласия на публикацию трека.
 * @param isStartingLiveTrack Идёт старт сессии трекинга на сервере.
 * @param hasLiveTracks По соревнованию есть онлайн-треки участников (для зрителя).
 * @param groupEligibility Подходит ли группа (по groupId) вошедшему пользователю по полу и возрасту;
 * пусто — пользователь не вошёл. Неподходящие группы в BottomSheet регистрации неактивны.
 */
data class EventDetailsState(
    val eventDetails: CyclicEventDetails? = null,
    val isRegistrationSheetVisible: Boolean = false,
    val selectedGroup: EventParticipantGroup? = null,
    val isRegistering: Boolean = false,
    val isUserRegistered: Boolean = false,
    val commandName: String = "",
    val isCommandNameEdited: Boolean = false,
    val teamOptions: RegistrationTeamOptions = RegistrationTeamOptions(),
    val clubMatches: List<ClubMatch> = emptyList(),
    val error: String? = null,
    val organizerClubName: String? = null,
    val liveTrackEntry: LiveTrackEntry = LiveTrackEntry.HIDDEN,
    val isLiveTrackConsentVisible: Boolean = false,
    val isStartingLiveTrack: Boolean = false,
    val hasLiveTracks: Boolean = false,
    val groupEligibility: Map<String, GroupEligibility> = emptyMap()
) : BaseState {

    /** Подсказки под полем «Команда» для текущего ввода. */
    val teamSuggestions: List<TeamSuggestion>
        get() = teamOptions.suggestionsFor(commandName)

    /** Своя команда/клуб, чья подпись сейчас в поле (null — свободный текст). */
    val selectedTeamOption: RegistrationTeamOption?
        get() = teamOptions.ownOptionFor(commandName)

    /** Показывать зрителю кнопку «Онлайн-треки»: соревнование идёт или треки уже есть. */
    val isLiveTracksButtonVisible: Boolean
        get() = eventDetails?.eventType == EventType.CyclicEvent.Orienteering &&
            (eventDetails.status == EventStatus.STARTED || hasLiveTracks)
}

/** Кнопка онлайн-трека бегуна на деталях события. */
enum class LiveTrackEntry {
    /** Не показывать: не зарегистрирован, не день соревнования, не ориентирование. */
    HIDDEN,

    /** «Включить онлайн-трек». */
    START,

    /** Трек уже пишется или досылается — кнопка открывает экран записи. */
    RECORDING
}
