package com.competra.center.presentation.station_tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.center.data.station_tools.StationToolsAction
import com.competra.center.data.station_tools.StationToolsState
import com.competra.center.data.station_tools.StationToolsTab
import com.competra.designsystem.theme.Dimens
import com.competra.resources.R
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран «Станции и чипы»: работа с оборудованием Sportiduino без привязки к соревнованию.
 * Все инструменты — чтение/проверка, очистка и запись чипов, настройка станций мастер-картами —
 * на одном экране, переключаются вкладками под заголовком.
 */
@Composable
fun StationToolsScreen(viewModel: StationToolsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    // Режим записи живёт, только пока экран на виду: при уходе с него (назад, другая вкладка
    // нижней навигации) NFC возвращается в режим чтения и не мешает экрану «Сканировать».
    DisposableEffect(viewModel) {
        viewModel.onAction(StationToolsAction.ScreenShown)
        onDispose { viewModel.onAction(StationToolsAction.ScreenHidden) }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        StationToolsContent(state = state, onAction = viewModel::onAction)
    }
}

@Composable
private fun StationToolsContent(
    state: StationToolsState,
    onAction: (StationToolsAction) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(Dimens.SIZE_BASE.dp)) {
            Text(
                text = "Станции и чипы",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Инструменты для работы с оборудованием Sportiduino. Не требуют соревнования " +
                    "и не меняют его результаты.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            StationToolsTab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { onAction(StationToolsAction.SelectTab(tab)) },
                    icon = {
                        Icon(
                            imageVector = ImageVector.vectorResource(tab.icon),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        if (state.selectedTab != StationToolsTab.INSPECT) {
            NfcStatusCard(
                state = state,
                modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp, vertical = 8.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (state.selectedTab) {
                StationToolsTab.INSPECT -> InspectTab(state = state)
                StationToolsTab.CLEAR -> ClearChipTab(state = state, onAction = onAction)
                StationToolsTab.WRITE_NUMBER -> WriteNumberTab(state = state, onAction = onAction)
                StationToolsTab.STATION -> StationTab(state = state, onAction = onAction)
            }
        }
    }
}

private val StationToolsTab.title: String
    get() = when (this) {
        StationToolsTab.INSPECT -> "Считать"
        StationToolsTab.CLEAR -> "Очистить"
        StationToolsTab.WRITE_NUMBER -> "Номер"
        StationToolsTab.STATION -> "Станция"
    }

private val StationToolsTab.icon: Int
    get() = when (this) {
        StationToolsTab.INSPECT -> R.drawable.ic_check_24px
        StationToolsTab.CLEAR -> R.drawable.delete
        StationToolsTab.WRITE_NUMBER -> R.drawable.edit
        StationToolsTab.STATION -> R.drawable.ic_build_24px
    }

@Preview(name = "Станции и чипы", showBackground = true)
@Composable
private fun StationToolsPreview() {
    MaterialTheme {
        StationToolsContent(state = StationToolsState(), onAction = {})
    }
}

@Preview(name = "Очистка подряд", showBackground = true)
@Composable
private fun StationToolsBatchPreview() {
    MaterialTheme {
        StationToolsContent(
            state = StationToolsState(
                selectedTab = StationToolsTab.CLEAR,
                isBatchMode = true,
                isWaitingForChip = true,
                batchProcessedCount = 12
            ),
            onAction = {}
        )
    }
}
