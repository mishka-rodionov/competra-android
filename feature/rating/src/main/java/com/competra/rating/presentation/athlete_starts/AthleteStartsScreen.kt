package com.competra.rating.presentation.athlete_starts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.rating.RatingCompetition
import com.competra.domain.models.rating.RatingStandingBreakdownEntry
import com.competra.rating.data.athlete_starts.AthleteStartsAction
import com.competra.resources.R
import com.competra.utils.DateTimeFormat
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthleteStartsScreen(
    ratingId: String,
    groupId: Long,
    participantKey: String,
    viewModel: AthleteStartsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(ratingId, groupId, participantKey) {
        viewModel.initialize(ratingId, groupId, participantKey)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.standing?.displayName.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onAction(AthleteStartsAction.BackClick) }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back_24px),
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        }
    ) { padding ->
        val standing = state.standing
        if (state.isLoading && standing == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (standing == null) return@Scaffold

        val competitionsById = state.competitions.associateBy { it.competitionId }
        val starts = standing.breakdown.sortedByDescending { entry ->
            competitionsById[entry.competitionId]?.competitionStartDate ?: 0L
        }

        if (starts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.rating_athlete_starts_empty))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.SIZE_BASE.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
        ) {
            items(starts, key = { it.competitionId }) { entry ->
                StartRow(
                    entry = entry,
                    competition = competitionsById[entry.competitionId],
                    onClick = { viewModel.onAction(AthleteStartsAction.StartClick(entry.competitionId)) }
                )
            }
        }
    }
}

@Composable
private fun StartRow(
    entry: RatingStandingBreakdownEntry,
    competition: RatingCompetition?,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.SIZE_BASE.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = competition?.competitionTitle
                        ?: stringResource(R.string.rating_athlete_starts_unknown_competition, entry.competitionId),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (competition != null) {
                    Text(
                        text = DateTimeFormat.transformLongToDisplayDate(competition.competitionStartDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = stringResource(R.string.rating_athlete_starts_place_label, entry.place?.toString() ?: "—"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
