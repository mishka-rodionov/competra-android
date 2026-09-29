package com.competra.eventdetails.presentation.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.designsystem.components.clickRipple
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.ParticipantWithResult
import com.competra.domain.models.orienteering.displayPlace
import com.competra.eventdetails.presentation.SplitsBottomSheet
import com.competra.eventdetails.presentation.formatResultScore
import com.competra.eventdetails.presentation.formatResultTime
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Экран результатов события.
 * Отображает результаты участников, сгруппированные по категориям (табам).
 *
 * @param eventId Идентификатор события.
 * @param viewModel Вьюмодель экрана.
 */
@Composable
fun EventResultsScreen(
    eventId: String,
    viewModel: EventResultsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(eventId) {
        viewModel.loadResults(eventId)
    }

    when {
        state.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.groupsWithResults.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Результаты недоступны",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            val groups = state.groupsWithResults
            val pagerState = rememberPagerState(pageCount = { groups.size })
            val scope = rememberCoroutineScope()

            Column(modifier = Modifier.fillMaxSize()) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    groups.forEachIndexed { index, groupWithResults ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(text = groupWithResults.group.title) }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        // remoteId, а не groupId — в этом remote-only потоке локальный groupId
                        // не заполняется (см. ParticipantGroupResponse.toDomain()) и одинаков
                        // у всех групп, поэтому именно remoteId уникально идентифицирует группу.
                        val groupRemoteId = groups[page].group.remoteId
                        if (groupRemoteId != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        viewModel.onAction(
                                            EventResultsAction.OpenGroupSplitsTable(
                                                eventId = eventId,
                                                groupId = groupRemoteId
                                            )
                                        )
                                    }
                                ) {
                                    Text(text = "Сплиты")
                                }
                                // Для формата "по выбору" общего порядка КП нет — вместо графика
                                // отставания от лидера по времени показываем график набора очков
                                // во времени (победитель определяется по сумме баллов, а не по
                                // времени на перегонах).
                                TextButton(
                                    onClick = {
                                        val action = if (state.direction == OrienteeringDirection.BY_CHOICE) {
                                            EventResultsAction.OpenScoreGraph(eventId = eventId, groupId = groupRemoteId)
                                        } else {
                                            EventResultsAction.OpenRaceGraph(eventId = eventId, groupId = groupRemoteId)
                                        }
                                        viewModel.onAction(action)
                                    }
                                ) {
                                    Text(text = "График")
                                }
                            }
                        }
                        ResultsList(
                            participants = groups[page].participants,
                            direction = state.direction,
                            onParticipantClick = { viewModel.onAction(EventResultsAction.ShowSplits(it)) }
                        )
                    }
                }
            }
        }
    }

    state.selectedParticipant?.let { selected ->
        SplitsBottomSheet(
            participantWithResult = selected,
            onDismiss = { viewModel.onAction(EventResultsAction.HideSplits) }
        )
    }
}

/**
 * Список результатов для конкретной группы.
 *
 * @param participants Список участников с их результатами.
 */
@Composable
private fun ResultsList(
    participants: List<ParticipantWithResult>,
    direction: OrienteeringDirection = OrienteeringDirection.FORWARD,
    onParticipantClick: (ParticipantWithResult) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            ResultsHeader()
        }
        items(participants) { item ->
            ResultItem(item = item, direction = direction, onClick = { onParticipantClick(item) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * Заголовок таблицы результатов.
 */
@Composable
private fun ResultsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(text = "№", modifier = Modifier.weight(0.1f), fontWeight = FontWeight.Bold)
        Text(text = "Участник", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
        Text(text = "Результат", modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
        Text(text = "Место", modifier = Modifier.weight(0.1f), fontWeight = FontWeight.Bold)
    }
}

/**
 * Строка результата участника.
 *
 * Для формата "по выбору" (BY_CHOICE) клик по строке отключён — просмотр сплитов не имеет
 * смысла (порядок посещения КП не регламентирован), вместо времени показываются баллы и время
 * вместе (приоритет в определении победителя у баллов).
 *
 * @param item Данные участника и его результата.
 */
@Composable
private fun ResultItem(
    item: ParticipantWithResult,
    direction: OrienteeringDirection = OrienteeringDirection.FORWARD,
    onClick: () -> Unit
) {
    val isByChoice = direction == OrienteeringDirection.BY_CHOICE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isByChoice) it else it.clickRipple(onClick = onClick) }
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = item.participant.startNumber,
            modifier = Modifier.weight(0.1f)
        )
        Text(
            text = "${item.participant.lastName} ${item.participant.firstName}",
            modifier = Modifier.weight(0.5f)
        )
        if (isByChoice && item.result?.status == ResultStatus.FINISHED) {
            Column(modifier = Modifier.weight(0.3f)) {
                Text(text = formatResultScore(item.result), fontWeight = FontWeight.Bold)
                Text(
                    text = formatResultTime(item.result),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = formatResultTime(item.result),
                modifier = Modifier.weight(0.3f)
            )
        }
        Text(
            text = item.result?.displayPlace?.toString() ?: "-",
            modifier = Modifier.weight(0.1f)
        )
    }
}


@Preview(showBackground = true)
@Composable
private fun EventResultsPreview() {
    val mockParticipants = listOf(
        ParticipantWithResult(
            participant = OrienteeringParticipant(
                id = "id1",
                userId = "user_1",
                firstName = "Иван",
                lastName = "Иванов",
                groupId = 1,
                groupName = "М21",
                competitionId = "1",
                commandName = "Команда А",
                startNumber = "101",
                startTime = 0L,
                chipNumber = "12345",
                comment = "",
                isChipGiven = true
            ),
            result = OrienteeringResult(
                id = 1,
                competitionId = "1",
                groupId = 1,
                participantId = "id1",
                totalTime = 1800L,
                rank = 1,
                status = ResultStatus.REGISTERED
            )
        ),
        ParticipantWithResult(
            participant = OrienteeringParticipant(
                id = "id2",
                userId = "user_2",
                firstName = "Петр",
                lastName = "Петров",
                groupId = 1,
                groupName = "М21",
                competitionId = "1",
                commandName = "Команда Б",
                startNumber = "102",
                startTime = 0L,
                chipNumber = "54321",
                comment = "",
                isChipGiven = true
            ),
            result = OrienteeringResult(
                id = 2,
                competitionId = "1",
                groupId = 1,
                participantId = "id2",
                totalTime = 1950L,
                rank = 2,
                status = ResultStatus.FINISHED
            )
        )
    )
    MaterialTheme {
        ResultsList(participants = mockParticipants, onParticipantClick = {})
    }
}
