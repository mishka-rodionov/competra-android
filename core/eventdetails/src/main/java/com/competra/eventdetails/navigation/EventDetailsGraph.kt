package com.competra.eventdetails.navigation

import com.competra.eventdetails.presentation.live_track_runner.LiveTrackRunnerScreen
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.competra.data.navigation.EventParticipantGroupNavType
import com.competra.data.navigation.EventsNavigation
import com.competra.domain.models.cyclic_event.EventParticipantGroup
import com.competra.eventdetails.presentation.details.EventDetailsScreen
import com.competra.eventdetails.presentation.group_splits.EventGroupSplitsTableScreen
import com.competra.eventdetails.presentation.live_results.LiveResultsScreen
import com.competra.eventdetails.presentation.participant_group.EventParticipantGroupScreen
import com.competra.eventdetails.presentation.race_graph.EventRaceGraphScreen
import com.competra.eventdetails.presentation.score_graph.EventScoreGraphScreen
import com.competra.eventdetails.presentation.results.EventResultsScreen
import kotlin.reflect.typeOf

/**
 * Подграф деталей события: сам экран деталей, регистрация в группу, результаты и онлайн‑результаты.
 * Подключается из любого NavHost-а через [NavGraphBuilder.eventDetailsGraph].
 */
fun NavGraphBuilder.eventDetailsGraph() {
    composable<EventsNavigation.EventDetailsRoute> {
        val route = it.toRoute<EventsNavigation.EventDetailsRoute>()
        EventDetailsScreen(idEvent = route.eventId)
    }
    composable<EventsNavigation.EventParticipantGroupRoute>(
        typeMap = mapOf(typeOf<EventParticipantGroup>() to EventParticipantGroupNavType)
    ) {
        val route = it.toRoute<EventsNavigation.EventParticipantGroupRoute>()
        EventParticipantGroupScreen(
            eventId = route.eventId,
            participantGroup = route.participantGroup
        )
    }
    composable<EventsNavigation.EventResultsRoute> {
        val route = it.toRoute<EventsNavigation.EventResultsRoute>()
        EventResultsScreen(eventId = route.eventId)
    }
    composable<EventsNavigation.LiveResultsRoute> {
        val route = it.toRoute<EventsNavigation.LiveResultsRoute>()
        LiveResultsScreen(eventId = route.eventId)
    }
    composable<EventsNavigation.LiveTrackRunnerRoute> {
        val route = it.toRoute<EventsNavigation.LiveTrackRunnerRoute>()
        LiveTrackRunnerScreen(eventId = route.eventId)
    }
    composable<EventsNavigation.EventSplitsTableRoute> {
        val route = it.toRoute<EventsNavigation.EventSplitsTableRoute>()
        EventGroupSplitsTableScreen(eventId = route.eventId, groupId = route.groupId)
    }
    composable<EventsNavigation.EventRaceGraphRoute> {
        val route = it.toRoute<EventsNavigation.EventRaceGraphRoute>()
        EventRaceGraphScreen(eventId = route.eventId, groupId = route.groupId)
    }
    composable<EventsNavigation.EventScoreGraphRoute> {
        val route = it.toRoute<EventsNavigation.EventScoreGraphRoute>()
        EventScoreGraphScreen(eventId = route.eventId, groupId = route.groupId)
    }
}
