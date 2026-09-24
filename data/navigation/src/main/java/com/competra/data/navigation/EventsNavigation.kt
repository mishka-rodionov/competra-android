package com.competra.data.navigation

import androidx.navigation.NavOptionsBuilder
import com.competra.domain.models.cyclic_event.EventParticipantGroup
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Навигация для модуля событий.
 */
@Serializable
sealed class EventsNavigation: BaseNavigation {

    @Transient
    @Contextual
    override var navOptionsBuilder: (NavOptionsBuilder.() -> Unit)? = null

    /**
     * Базовый роут событий.
     */
    @Serializable
    data object EventsBaseRoute : EventsNavigation()

    /**
     * Роут списка событий.
     */
    @Serializable
    data object EventsRoute : EventsNavigation()

    /**
     * Роут деталей события.
     * @param eventId Идентификатор события.
     */
    @Serializable
    data class EventDetailsRoute(val eventId: String) : EventsNavigation()

    /**
     * Роут группы участников события.
     * @param eventId Идентификатор события.
     * @param participantGroup Группа участников.
     */
    @Serializable
    data class EventParticipantGroupRoute(
        val eventId: String,
        val participantGroup: EventParticipantGroup
    ) : EventsNavigation()

    /**
     * Роут результатов события.
     * @param eventId Идентификатор события.
     */
    @Serializable
    data class EventResultsRoute(val eventId: String) : EventsNavigation()

    /**
     * Роут онлайн-результатов соревнования (live режим, только для IN_PROGRESS).
     * @param eventId Идентификатор события.
     */
    @Serializable
    data class LiveResultsRoute(val eventId: String) : EventsNavigation()

    /**
     * Роут экрана онлайн-трека бегуна: статус записи и передачи трека на соревновании (без карты —
     * бегуну позиция не показывается).
     * @param eventId Идентификатор соревнования.
     */
    @Serializable
    data class LiveTrackRunnerRoute(val eventId: String) : EventsNavigation()

    /**
     * Роут онлайн-треков для зрителя: выбор дистанции соревнования.
     * @param eventId Идентификатор соревнования.
     */
    @Serializable
    data class LiveTracksRoute(val eventId: String) : EventsNavigation()

    /**
     * Роут карты онлайн-треков одной дистанции (живые и архивные треки её участников).
     * @param eventId Идентификатор соревнования.
     * @param distanceId Серверный идентификатор дистанции.
     */
    @Serializable
    data class LiveTrackMapRoute(val eventId: String, val distanceId: Long) : EventsNavigation()

    /**
     * Роут таблицы сплитов группы события.
     * @param eventId Идентификатор события.
     * @param groupId Идентификатор группы участников.
     */
    @Serializable
    data class EventSplitsTableRoute(val eventId: String, val groupId: Long) : EventsNavigation()

    /**
     * Роут графика отставания от лидера (race graph) по группе события.
     * @param eventId Идентификатор события.
     * @param groupId Идентификатор группы участников.
     */
    @Serializable
    data class EventRaceGraphRoute(val eventId: String, val groupId: Long) : EventsNavigation()

    /**
     * Роут графика набора очков во времени (score graph, BY_CHOICE) по группе события.
     * @param eventId Идентификатор события.
     * @param groupId Идентификатор группы участников.
     */
    @Serializable
    data class EventScoreGraphRoute(val eventId: String, val groupId: Long) : EventsNavigation()
}
