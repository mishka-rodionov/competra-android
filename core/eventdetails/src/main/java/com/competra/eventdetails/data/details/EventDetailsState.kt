package com.competra.eventdetails.data.details

import com.competra.domain.models.cyclic_event.CyclicEventDetails
import com.competra.domain.models.cyclic_event.EventParticipantGroup
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
 * @param organizerClubName Название клуба-организатора, резолвится отдельным запросом по `organizingClubId`.
 * @param liveTrackEntry Что показывать на кнопке онлайн-трека бегуна.
 * @param isLiveTrackConsentVisible Видимость диалога согласия на публикацию трека.
 * @param isStartingLiveTrack Идёт старт сессии трекинга на сервере.
 */
data class EventDetailsState(
    val eventDetails: CyclicEventDetails? = null,
    val isRegistrationSheetVisible: Boolean = false,
    val selectedGroup: EventParticipantGroup? = null,
    val isRegistering: Boolean = false,
    val isUserRegistered: Boolean = false,
    val commandName: String = "",
    val error: String? = null,
    val organizerClubName: String? = null,
    val liveTrackEntry: LiveTrackEntry = LiveTrackEntry.HIDDEN,
    val isLiveTrackConsentVisible: Boolean = false,
    val isStartingLiveTrack: Boolean = false
) : BaseState

/** Кнопка онлайн-трека бегуна на деталях события. */
enum class LiveTrackEntry {
    /** Не показывать: не зарегистрирован, не день соревнования, не ориентирование. */
    HIDDEN,

    /** «Включить онлайн-трек». */
    START,

    /** Трек уже пишется или досылается — кнопка открывает экран записи. */
    RECORDING
}
