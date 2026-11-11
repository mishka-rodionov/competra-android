package com.competra.center.presentation.splits

import androidx.compose.foundation.layout.*
import com.competra.domain.models.orienteering.withRaceStart
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.competra.designsystem.theme.Dimens
import com.competra.center.presentation.read_card.ParticipantInfoCard
import com.competra.center.presentation.read_card.RaceSummaryCard
import com.competra.center.presentation.read_card.SplitsCard
import com.competra.resources.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun ParticipantSplitsScreen(
    participantId: String,
    competitionId: String,
    viewModel: ParticipantSplitsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(participantId, competitionId) {
        viewModel.load(participantId, competitionId)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val participant = state.participant
        val result = state.result

        if (participant == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Dimens.SIZE_BASE.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_BASE.dp)
            ) {
                item {
                    Text(
                        text = "Сплиты участника",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                item { ParticipantInfoCard(participant) }

                if (result != null) {
                    // Старт и сплиты — от фактического старта из результата: при старте по станции
                    // у участника с сервера старт пустой.
                    val raceParticipant = participant.withRaceStart(result)
                    item { RaceSummaryCard(raceParticipant, result) }

                    result.splits?.let { splits ->
                        item {
                            Text(
                                text = "Сплиты по пунктам",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        item { SplitsCard(raceParticipant, splits) }
                    }

                    if (result.splits.isNullOrEmpty()) {
                        item {
                            Text(
                                text = "Сплиты отсутствуют",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.SIZE_DOUBLE.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_location_on_24px),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Результат пока не зафиксирован",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
