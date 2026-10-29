package com.competra.events.data.main

import com.competra.domain.models.events.EventsFilter
import com.competra.domain.models.livetrack.MyStart
import com.competra.domain.models.livetrack.RunnerTrackSession
import com.competra.ui.BaseState

/**
 * Состояние экрана списка событий.
 *
 * @property isFilterDialogOpen Открыт ли диалог фильтра.
 * @property appliedFilter Применённый фильтр.
 * @property isRefreshing Идёт обновление, запущенное жестом pull-to-refresh: вместо глобального
 * лоадера показывается индикатор над списком.
 * @property todayStarts Соревнования пользователя, которые проходят сегодня (из локального кэша, без сети) —
 * карточки «Сегодня старт» с кнопкой онлайн-трека.
 * @property liveTrackConsentFor Соревнование, для которого показан диалог согласия на публикацию трека.
 */
data class EventsState(
    val isFilterDialogOpen: Boolean = false,
    val appliedFilter: EventsFilter = EventsFilter(),
    val isRefreshing: Boolean = false,
    val todayStarts: List<TodayStart> = emptyList(),
    val liveTrackConsentFor: String? = null
) : BaseState

/**
 * Карточка «Сегодня старт».
 *
 * @property start Соревнование из кэша «моих стартов».
 * @property session Последняя сессия онлайн-трека по нему на этом телефоне.
 */
data class TodayStart(
    val start: MyStart,
    val session: RunnerTrackSession?
) {
    /** Трек пишется или ещё досылается — кнопка открывает экран записи вместо нового старта. */
    val isTrackInProgress: Boolean
        get() = session != null && (session.isRecording || (session.stopRequested && !session.stopDelivered))
}
