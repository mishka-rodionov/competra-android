package com.competra.center.data.main

import com.competra.center.data.refresh.RemoteRefreshState
import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.ui.BaseState

/**
 * Состояние экрана «Центр».
 *
 * @property controlledEvents Соревнования организатора из локальной БД.
 * @property isAuthed Пользователь авторизован.
 * @property deletingCompetition Соревнование, для которого открыт диалог удаления.
 * @property isLocalLoaded Список уже прочитан из локальной БД — до этого заглушку «нет событий» не показываем.
 * @property refresh Состояние фоновой загрузки списка с сервера.
 */
data class CenterState(
    val controlledEvents: List<OrienteeringCompetition> = emptyList(),
    val isAuthed: Boolean = false,
    val deletingCompetition: OrienteeringCompetition? = null,
    val isLocalLoaded: Boolean = false,
    val refresh: RemoteRefreshState = RemoteRefreshState(),
) : BaseState
