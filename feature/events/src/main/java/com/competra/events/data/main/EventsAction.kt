package com.competra.events.data.main

import com.competra.domain.models.events.EventsFilter

sealed class EventsAction {
    data class EventClick(val eventId: String?) : EventsAction()
    data object OpenFilterDialog : EventsAction()
    data object CloseFilterDialog : EventsAction()
    data class ApplyFilter(val filter: EventsFilter) : EventsAction()
    data object ResetFilter : EventsAction()

    /** Пользователь потянул список вниз — перезагрузить его с первой страницы. */
    data object Refresh : EventsAction()

    /** «Начать трек» на карточке «Сегодня старт». */
    data class StartLiveTrack(val competitionId: String) : EventsAction()

    /** «Открыть» на карточке с идущей записью — экран записи трека. */
    data class OpenLiveTrack(val competitionId: String) : EventsAction()

    /** Согласие на публикацию трека получено. */
    data object LiveTrackConsentAccepted : EventsAction()

    /** Диалог согласия закрыт без согласия. */
    data object LiveTrackConsentDismissed : EventsAction()

    /** Итог проверки/запроса разрешений перед стартом трека. */
    data class LiveTrackPermissionsResult(val locationGranted: Boolean) : EventsAction()
}
