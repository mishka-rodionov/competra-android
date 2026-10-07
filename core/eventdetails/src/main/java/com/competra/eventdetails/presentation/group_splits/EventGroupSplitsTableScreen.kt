package com.competra.eventdetails.presentation.group_splits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.competra.ui.components.GroupSplitsTableContent
import org.koin.androidx.compose.koinViewModel

@Composable
fun EventGroupSplitsTableScreen(
    eventId: String,
    groupId: Long,
    viewModel: EventGroupSplitsTableViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(eventId, groupId) {
        viewModel.load(eventId, groupId)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.table == null || state.table!!.rows.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Сплиты по группе отсутствуют",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            else -> GroupSplitsTableContent(
                groupTitle = state.groupTitle,
                table = state.table!!,
                direction = state.direction,
                byChoiceMode = state.byChoiceMode
            )
        }
    }
}
