package com.competra.eventdetails.presentation.live_results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.designsystem.components.clickRipple
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.Gender
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.GroupWithParticipantsAndResults
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.ParticipantWithResult
import com.competra.domain.models.orienteering.SplitTime
import com.competra.domain.models.orienteering.displayPlace
import com.competra.eventdetails.presentation.SplitsBottomSheet
import com.competra.eventdetails.presentation.formatResultScore
import com.competra.eventdetails.presentation.formatResultTime
import com.competra.utils.orienteering.toSplitTime
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран онлайн-результатов соревнования.
 * Автоматически обновляется каждые 30 секунд.
 * Доступен без авторизации.
 *
 * @param eventId Идентификатор соревнования.
 */
@Composable
fun LiveResultsScreen(
    eventId: String,
    viewModel: LiveResultsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(eventId) {
        viewModel.startPolling(eventId)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LiveResultsHeader(lastUpdated = state.lastUpdated)

            when {
                state.isLoading && state.groupsWithResults.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.groupsWithResults.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Результаты пока не поступали",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val groups = state.groupsWithResults
                    val pagerState = rememberPagerState(pageCount = { groups.size })
                    val scope = rememberCoroutineScope()

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
                        LiveResultsList(
                            participants = groups[page].participants,
                            direction = state.direction,
                            onParticipantClick = { viewModel.onAction(LiveResultsAction.ShowSplits(it)) }
                        )
                    }
                }
            }
        }
    }

    state.selectedParticipant?.let { selected ->
        SplitsBottomSheet(
            participantWithResult = selected,
            onDismiss = { viewModel.onAction(LiveResultsAction.HideSplits) }
        )
    }
}

@Composable
private fun LiveResultsHeader(lastUpdated: Long?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SIZE_BASE.dp, vertical = Dimens.SIZE_HALF.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Онлайн результаты",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFFE53935), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        if (lastUpdated != null) {
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdated))
            Text(
                text = timeStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LiveResultsList(
    participants: List<ParticipantWithResult>,
    direction: OrienteeringDirection = OrienteeringDirection.FORWARD,
    onParticipantClick: (ParticipantWithResult) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.SIZE_BASE.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Text(text = "№", modifier = Modifier.weight(0.1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text(text = "Участник", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text(text = "Результат", modifier = Modifier.weight(0.25f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text(text = "Место", modifier = Modifier.weight(0.15f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            }
            HorizontalDivider()
        }
        itemsIndexed(participants) { _, item ->
            LiveResultRow(
                item = item,
                direction = direction,
                onClick = { onParticipantClick(item) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * Для формата "по выбору" (BY_CHOICE) клик по строке отключён — просмотр сплитов не имеет
 * смысла (порядок посещения КП не регламентирован), вместо времени показываются баллы и время
 * вместе (приоритет в определении победителя у баллов).
 */
@Composable
private fun LiveResultRow(
    item: ParticipantWithResult,
    direction: OrienteeringDirection = OrienteeringDirection.FORWARD,
    onClick: () -> Unit
) {
    val isByChoice = direction == OrienteeringDirection.BY_CHOICE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isByChoice) it else it.clickRipple(onClick = onClick) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.participant.startNumber,
            modifier = Modifier.weight(0.1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "${item.participant.lastName} ${item.participant.firstName}",
            modifier = Modifier.weight(0.5f),
            style = MaterialTheme.typography.bodyMedium
        )
        if (isByChoice && item.result?.status == ResultStatus.FINISHED) {
            Column(modifier = Modifier.weight(0.25f)) {
                Text(
                    text = formatResultScore(item.result),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatResultTime(item.result),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = formatResultTime(item.result),
                modifier = Modifier.weight(0.25f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.result?.status == ResultStatus.FINISHED)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = item.result?.displayPlace?.toString() ?: "—",
            modifier = Modifier.weight(0.15f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (item.result?.displayPlace == 1) FontWeight.Bold else FontWeight.Normal,
            color = if (item.result?.displayPlace == 1) Color(0xFFB8860B) else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}

private fun previewParticipant(
    id: String,
    firstName: String,
    lastName: String,
    startNumber: String,
    startTime: Long = 1_700_000_000_000L
) = OrienteeringParticipant(
    id = id,
    userId = "",
    firstName = firstName,
    lastName = lastName,
    groupId = 1,
    groupName = "М21",
    competitionId = "1",
    commandName = "",
    startNumber = startNumber,
    startTime = startTime,
    chipNumber = "",
    comment = "",
    isChipGiven = true
)

private fun previewResult(
    participantId: String,
    totalTime: Long?,
    rank: Int?,
    status: ResultStatus,
    splits: List<SplitTime>? = null
) = OrienteeringResult(
    id = 1,
    competitionId = "1",
    groupId = 1,
    participantId = participantId,
    startTime = 1_700_000_000_000L,
    finishTime = if (totalTime != null) 1_700_000_000_000L + totalTime * 1000 else null,
    totalTime = totalTime,
    rank = rank,
    status = status,
    splits = splits
)

private fun previewGroup(title: String, gender: Gender? = null) = ParticipantGroup(
    groupId = 1,
    competitionId = "1",
    title = title,
    gender = gender,
    distanceId = 1
)

private val previewParticipantsM21 = listOf(
    ParticipantWithResult(
        participant = previewParticipant("1", "Иван", "Иванов", "101"),
        result = previewResult(
            participantId = "1",
            totalTime = 3723L,
            rank = 1,
            status = ResultStatus.FINISHED,
            splits = listOf(
                SplitTime(31, 1_700_000_000_000L + 720_000),
                SplitTime(32, 1_700_000_000_000L + 1_800_000),
                SplitTime(33, 1_700_000_000_000L + 3_723_000)
            )
        )
    ),
    ParticipantWithResult(
        participant = previewParticipant("2", "Петр", "Петров", "102"),
        result = previewResult("2", 4015L, 2, ResultStatus.FINISHED)
    ),
    ParticipantWithResult(
        participant = previewParticipant("3", "Алексей", "Сидоров", "103"),
        result = previewResult("3", null, null, ResultStatus.STARTED)
    ),
    ParticipantWithResult(
        participant = previewParticipant("4", "Мария", "Козлова", "104"),
        result = previewResult("4", null, null, ResultStatus.DNS)
    )
)

@Preview(showBackground = true, name = "Список результатов")
@Composable
private fun LiveResultsListPreview() {
    MaterialTheme {
        LiveResultsList(
            participants = previewParticipantsM21,
            onParticipantClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Строка результата — 1 место")
@Composable
private fun LiveResultRowFinishedPreview() {
    MaterialTheme {
        LiveResultRow(
            item = previewParticipantsM21[0],
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Строка результата — на дистанции")
@Composable
private fun LiveResultRowStartedPreview() {
    MaterialTheme {
        LiveResultRow(
            item = previewParticipantsM21[2],
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Заголовок — с временем обновления")
@Composable
private fun LiveResultsHeaderPreview() {
    MaterialTheme {
        LiveResultsHeader(lastUpdated = System.currentTimeMillis())
    }
}

@Preview(showBackground = true, name = "Заголовок — без времени")
@Composable
private fun LiveResultsHeaderNoTimePreview() {
    MaterialTheme {
        LiveResultsHeader(lastUpdated = null)
    }
}

@Preview(showBackground = true, name = "Пустое состояние")
@Composable
private fun LiveResultsEmptyPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                LiveResultsHeader(lastUpdated = null)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Результаты пока не поступали",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Загрузка")
@Composable
private fun LiveResultsLoadingPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                LiveResultsHeader(lastUpdated = null)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Экран с группами")
@Composable
private fun LiveResultsWithGroupsPreview() {
    val groups = listOf(
        GroupWithParticipantsAndResults(
            group = previewGroup("М21", Gender.MALE),
            participants = previewParticipantsM21
        ),
        GroupWithParticipantsAndResults(
            group = previewGroup("Ж21", Gender.FEMALE),
            participants = previewParticipantsM21.map { it.copy(participant = it.participant.copy(groupName = "Ж21")) }
        )
    )
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                LiveResultsHeader(lastUpdated = System.currentTimeMillis())
                val pagerState = rememberPagerState(pageCount = { groups.size })
                val scope = rememberCoroutineScope()
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    groups.forEachIndexed { index, g ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(text = g.group.title) }
                        )
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    LiveResultsList(participants = groups[page].participants, onParticipantClick = {})
                }
            }
        }
    }
}

@Composable
private fun SplitsTable(participant: OrienteeringParticipant, splits: List<SplitTime>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text = "КП", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(text = "Сплит", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(text = "Время", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
    HorizontalDivider(thickness = 0.5.dp)
    splits.forEachIndexed { index, split ->
        val splitMs = if (index == 0) split.timestamp - participant.startTime
                      else split.timestamp - splits[index - 1].timestamp
        val totalMs = split.timestamp - participant.startTime
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(text = split.controlPoint.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(text = splitMs.toSplitTime(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
            Text(text = totalMs.toSplitTime(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        }
        if (index < splits.size - 1) {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}
