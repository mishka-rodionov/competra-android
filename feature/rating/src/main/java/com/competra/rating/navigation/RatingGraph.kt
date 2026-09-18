package com.competra.rating.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.competra.data.navigation.RatingNavigation
import com.competra.eventdetails.navigation.eventDetailsGraph
import com.competra.rating.presentation.add_competition.AddCompetitionScreen
import com.competra.rating.presentation.athlete_starts.AthleteStartsScreen
import com.competra.rating.presentation.detail.RatingDetailScreen
import com.competra.rating.presentation.form.RatingFormScreen
import com.competra.rating.presentation.group_mapping.GroupMappingScreen
import com.competra.rating.presentation.list.RatingListScreen
import com.competra.rating.presentation.search.RatingsSearchScreen

/**
 * Граф навигации для раздела рейтингов соревнований. Точка входа — из экрана деталей клуба
 * (клубный список) либо из экрана списка клубов (глобальный поиск).
 */
fun NavGraphBuilder.ratingGraph() {
    composable<RatingNavigation.RatingsSearchRoute> {
        RatingsSearchScreen()
    }

    composable<RatingNavigation.RatingListRoute> { backStackEntry ->
        val route: RatingNavigation.RatingListRoute = backStackEntry.toRoute()
        RatingListScreen(clubId = route.clubId)
    }

    composable<RatingNavigation.CreateRatingRoute> { backStackEntry ->
        val route: RatingNavigation.CreateRatingRoute = backStackEntry.toRoute()
        RatingFormScreen(clubId = route.clubId)
    }

    composable<RatingNavigation.RatingDetailRoute> { backStackEntry ->
        val route: RatingNavigation.RatingDetailRoute = backStackEntry.toRoute()
        RatingDetailScreen(ratingId = route.ratingId)
    }

    composable<RatingNavigation.AddCompetitionRoute> { backStackEntry ->
        val route: RatingNavigation.AddCompetitionRoute = backStackEntry.toRoute()
        AddCompetitionScreen(ratingId = route.ratingId)
    }

    composable<RatingNavigation.GroupMappingRoute> { backStackEntry ->
        val route: RatingNavigation.GroupMappingRoute = backStackEntry.toRoute()
        GroupMappingScreen(ratingId = route.ratingId, competitionId = route.competitionId)
    }

    composable<RatingNavigation.AthleteStartsRoute> { backStackEntry ->
        val route: RatingNavigation.AthleteStartsRoute = backStackEntry.toRoute()
        AthleteStartsScreen(ratingId = route.ratingId, groupId = route.groupId, participantKey = route.participantKey)
    }

    // Переход со старта спортсмена на результаты соревнования (EventResultsRoute) ведёт сюда же —
    // граф деталей события подключается прямо здесь, как и в profileNavigation()/eventsGraph().
    eventDetailsGraph()
}
