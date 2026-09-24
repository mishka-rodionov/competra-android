package com.competra.eventdetails.presentation.live_tracks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import org.koin.androidx.compose.koinViewModel

/**
 * Онлайн-треки соревнования: выбор дистанции (у каждой своя карта). Доступен без авторизации.
 *
 * @param eventId Идентификатор соревнования.
 */
@Composable
fun LiveTracksScreen(
    eventId: String,
    viewModel: LiveTracksViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LifecycleStartEffect(eventId) {
        viewModel.start(eventId)
        onStopOrDispose { viewModel.stop() }
    }

    LiveTracksContent(state = state, onAction = viewModel::onAction)
}

@Composable
private fun LiveTracksContent(state: LiveTracksState, onAction: (LiveTracksAction) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Онлайн-треки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.distances.isEmpty() -> Text(
                    if (state.isError) {
                        "Сервер онлайн-треков недоступен. Попробуем ещё раз через несколько секунд."
                    } else {
                        "Пока никто не включил онлайн-трек. Треки появятся, когда участники включат их в приложении перед стартом."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.distances, key = { it.distanceId }) { item ->
                        DistanceCard(item) { onAction(LiveTracksAction.OpenDistance(item.distanceId)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistanceCard(item: LiveTrackDistanceItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val active = if (item.activeCount > 0) "На дистанции: ${item.activeCount} • " else ""
            Text("${active}Треков: ${item.totalCount}", style = MaterialTheme.typography.bodyMedium)
            if (!item.hasMap) {
                Text(
                    "Карта дистанции не загружена — треки на обычной карте",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LiveTracksPreview() {
    MaterialTheme {
        LiveTracksContent(
            state = LiveTracksState(
                isLoading = false,
                distances = listOf(
                    LiveTrackDistanceItem(1, "Длинная", activeCount = 7, totalCount = 12, hasMap = true),
                    LiveTrackDistanceItem(2, "Короткая", activeCount = 0, totalCount = 5, hasMap = false)
                )
            ),
            onAction = {}
        )
    }
}
