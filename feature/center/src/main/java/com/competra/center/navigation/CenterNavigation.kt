package com.competra.center.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowSizeClass
import com.competra.center.presentation.draw.DrawParticipantsScreen
import com.competra.center.presentation.event_control.orienteering.OrienteeringEventControlScreen
import com.competra.center.presentation.get_chip.GetOrienteeringChipScreen
import com.competra.center.presentation.group_splits.GroupSplitsTableScreen
import com.competra.center.presentation.race_graph.RaceGraphScreen
import com.competra.center.presentation.score_graph.ScoreGraphScreen
import com.competra.center.presentation.write_chip.WriteChipScreen
import com.competra.center.presentation.main.CenterScreen
import com.competra.center.presentation.kind_of_sport.KindOfSportScreen
import com.competra.center.presentation.map.MapPickerScreen
import com.competra.center.presentation.orientiring_competition_create.*
import com.competra.center.presentation.participant_list.ParticipantListScreen
import com.competra.center.presentation.read_card.OrientReadCardScreen
import com.competra.center.presentation.results.OrienteeringCompetitionResultsScreen
import com.competra.center.presentation.splits.ParticipantSplitsScreen
import com.competra.center.presentation.start_grid.StartGridScreen
import com.competra.center.presentation.orientiring_competition_create.MAP_RESULT_LAT
import com.competra.center.presentation.orientiring_competition_create.MAP_RESULT_LON
import com.competra.data.navigation.CenterNavigation
import com.competra.eventdetails.navigation.competitionLinkRequestsDestination
import org.koin.androidx.compose.koinViewModel

/**
 * Граф навигации для раздела управления соревнованиями.
 */
fun NavGraphBuilder.centerGraph(windowSizeClass: WindowSizeClass, navController: NavController) {
//    navigation<CenterNavigationGraph.CenterBaseRoute>(startDestination = CenterNavigationGraph.CenterRoute) {
    composable<CenterNavigation.CenterRoute> { CenterScreen() }
    composable<CenterNavigation.KindOfSportRoute> { KindOfSportScreen() }
    competitionLinkRequestsDestination()
    
    // Пошаговое создание соревнования
    composable<CenterNavigation.CommonCompetitionFieldRoute> { backStackEntry ->
        val route: CenterNavigation.CommonCompetitionFieldRoute = backStackEntry.toRoute()
        val viewModel: OrienteeringCreatorViewModel = koinViewModel()

        // Получение результата выбора координат на карте через savedStateHandle
        val currentEntry by navController.currentBackStackEntryAsState()
        LaunchedEffect(currentEntry?.id) {
            val handle = backStackEntry.savedStateHandle
            val lat: Double = handle.get<Double>(MAP_RESULT_LAT) ?: return@LaunchedEffect
            val lon: Double = handle.get<Double>(MAP_RESULT_LON) ?: return@LaunchedEffect
            viewModel.updateCoordinates(lat, lon)
            handle.remove<Double>(MAP_RESULT_LAT)
            handle.remove<Double>(MAP_RESULT_LON)
        }

        CommonCompetitionFieldScreen(competitionId = route.competitionId, viewModel = viewModel)
    }
    composable<CenterNavigation.RegistrationCompetitionFieldRoute> { backStackEntry ->
        val route: CenterNavigation.RegistrationCompetitionFieldRoute = backStackEntry.toRoute()
        RegistrationCompetitionFieldScreen(competitionId = route.competitionId)
    }
    composable<CenterNavigation.OrganizatorCompetitionFieldRoute> { backStackEntry ->
        val route: CenterNavigation.OrganizatorCompetitionFieldRoute = backStackEntry.toRoute()
        OrganizatorCompetitionFieldScreen(competitionId = route.competitionId)
    }
    composable<CenterNavigation.CreateDistanceRoute> { backStackEntry ->
        val route: CenterNavigation.CreateDistanceRoute = backStackEntry.toRoute()
        CreateDistanceScreen(competitionId = route.competitionId)
    }
    composable<CenterNavigation.CreateParticipantGroupRoute> { backStackEntry ->
        val route: CenterNavigation.CreateParticipantGroupRoute = backStackEntry.toRoute()
        CreateParticipantGroupScreen(competitionId = route.competitionId)
    }

    composable<CenterNavigation.OrienteeringEventControlRoute> {
        OrienteeringEventControlScreen(
            windowSizeClass = windowSizeClass
        )
    }
    composable<CenterNavigation.OrientReadCardRoute> { OrientReadCardScreen() }
    composable<CenterNavigation.ParticipantList> { ParticipantListScreen(onSave = { navController.popBackStack() }) }
    composable<CenterNavigation.DrawParticipants> { DrawParticipantsScreen() }
    composable<CenterNavigation.StartGridRoute> { StartGridScreen() }
    composable<CenterNavigation.ParticipantResults> { OrienteeringCompetitionResultsScreen() }
    composable<CenterNavigation.GetOrienteeringChipRoute> { backStackEntry ->
        val route: CenterNavigation.GetOrienteeringChipRoute = backStackEntry.toRoute()
        GetOrienteeringChipScreen(competitionId = route.competitionId)
    }

    composable<CenterNavigation.WriteChipRoute> { WriteChipScreen() }

    composable<CenterNavigation.ParticipantSplitsRoute> { backStackEntry ->
        val route: CenterNavigation.ParticipantSplitsRoute = backStackEntry.toRoute()
        ParticipantSplitsScreen(
            participantId = route.participantId,
            competitionId = route.competitionId
        )
    }

    composable<CenterNavigation.GroupSplitsTableRoute> { backStackEntry ->
        val route: CenterNavigation.GroupSplitsTableRoute = backStackEntry.toRoute()
        GroupSplitsTableScreen(
            groupId = route.groupId,
            competitionId = route.competitionId
        )
    }

    composable<CenterNavigation.GroupRaceGraphRoute> { backStackEntry ->
        val route: CenterNavigation.GroupRaceGraphRoute = backStackEntry.toRoute()
        RaceGraphScreen(
            groupId = route.groupId,
            competitionId = route.competitionId
        )
    }

    composable<CenterNavigation.GroupScoreGraphRoute> { backStackEntry ->
        val route: CenterNavigation.GroupScoreGraphRoute = backStackEntry.toRoute()
        ScoreGraphScreen(
            groupId = route.groupId,
            competitionId = route.competitionId
        )
    }

    composable<CenterNavigation.MapPickerRoute> { backStackEntry ->
        val route: CenterNavigation.MapPickerRoute = backStackEntry.toRoute()
        MapPickerScreen(
            initLat = route.initLatE6 / 1_000_000.0,
            initLon = route.initLonE6 / 1_000_000.0,
            onConfirm = { lat, lon ->
                navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                    handle[MAP_RESULT_LAT] = lat
                    handle[MAP_RESULT_LON] = lon
                }
                navController.popBackStack()
            }
        )
    }
//    }
}
