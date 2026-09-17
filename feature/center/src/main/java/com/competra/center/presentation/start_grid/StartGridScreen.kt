package com.competra.center.presentation.start_grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.competra.utils.isValidStartTimestamp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.competra.center.data.start_grid.StartGridAction
import com.competra.center.data.start_grid.StartGridState
import com.competra.center.data.start_grid.StartSlot
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.resources.R
import com.competra.ui.BaseAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.util.Calendar

/**
 * Экран «Стартовая решётка» — помогает судье на старте.
 *
 * Горизонтальный pager, где каждая страница — одна стартовая минута. На странице список
 * участников этой минуты, в шапке — стартовое время, число участников и обратный отсчёт.
 * Вверху идёт секундомер соревнования; pager сам прокручивается на текущую минуту.
 */
@Composable
fun StartGridScreen(
    viewModel: StartGridViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onAction(StartGridAction.Reload)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        StartGridContent(state = state, onAction = viewModel::onAction)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StartGridContent(
    state: StartGridState,
    onAction: (BaseAction) -> Unit
) {
    val slots = state.slots
    val pagerState = rememberPagerState { slots.size }
    val scope = rememberCoroutineScope()

    // Тикаем «сейчас» раз в полсекунды для обратного отсчёта в шапке страницы.
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMs = System.currentTimeMillis()
            delay(500)
        }
    }

    // Автоследование за текущей минутой. Ручной свайп отключает следование,
    // кнопка «Сейчас» — включает обратно.
    var autoFollow by remember { mutableStateOf(true) }
    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) autoFollow = false
        }
    }
    LaunchedEffect(state.currentSlotIndex, autoFollow, state.isCompetitionRunning, slots.size) {
        if (autoFollow && state.isCompetitionRunning && state.currentSlotIndex in slots.indices) {
            pagerState.animateScrollToPage(state.currentSlotIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StopwatchBanner(
            elapsedMillis = state.stopwatchMillis,
            isRunning = state.isCompetitionRunning,
            modifier = Modifier.padding(
                horizontal = Dimens.SIZE_BASE.dp,
                vertical = Dimens.SIZE_HALF.dp
            )
        )

        if (slots.isEmpty()) {
            EmptyView(message = "Нет участников со стартовыми временами.\nСначала проведите жеребьёвку.")
            return@Column
        }

        // Поиск по стартовому номеру — прыжок на минуту нужного участника.
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { value ->
                onAction(StartGridAction.SearchChanged(value))
                val number = value.trim()
                if (number.isNotEmpty()) {
                    val target = slots.indexOfFirst { slot ->
                        slot.participants.any { it.startNumber == number }
                    }
                    if (target >= 0) {
                        autoFollow = false
                        scope.launch { pagerState.animateScrollToPage(target) }
                    }
                }
            },
            label = { Text("Поиск по номеру") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SIZE_BASE.dp, vertical = Dimens.SIZE_HALF.dp)
        )

        SecondaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage.coerceIn(0, slots.lastIndex),
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {},
            indicator = {}
        ) {
            slots.forEachIndexed { index, slot ->
                val isCurrent = index == state.currentSlotIndex && state.isCompetitionRunning
                val isSelected = pagerState.currentPage == index
                Tab(
                    selected = isSelected,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatTime(slot.startTime),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isCurrent) {
                                Text(
                                    text = "сейчас",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val slot = slots[page]
                Column(modifier = Modifier.fillMaxSize()) {
                    SlotHeader(
                        slot = slot,
                        currentTimeMs = currentTimeMs,
                        isCompetitionRunning = state.isCompetitionRunning
                    )
                    StartSlotParticipantList(participants = slot.participants)
                }
            }

            // Кнопка возврата к текущей минуте, если судья листал вручную.
            if (state.isCompetitionRunning && !autoFollow && state.currentSlotIndex in slots.indices) {
                ExtendedFloatingActionButton(
                    onClick = {
                        autoFollow = true
                        scope.launch { pagerState.animateScrollToPage(state.currentSlotIndex) }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Dimens.SIZE_BASE.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(text = "Сейчас", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Шапка страницы стартовой минуты: время, число участников и обратный отсчёт.
 */
@Composable
private fun SlotHeader(
    slot: StartSlot,
    currentTimeMs: Long,
    isCompetitionRunning: Boolean
) {
    val msLeft = slot.startTime - currentTimeMs
    val countdownText = when {
        !isCompetitionRunning -> null
        msLeft <= 0L -> "стартовали"
        else -> {
            val totalSec = msLeft / 1000L
            "до старта %02d:%02d".format(totalSec / 60, totalSec % 60)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SIZE_BASE.dp, vertical = Dimens.SIZE_HALF.dp),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SIZE_BASE.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Старт ${formatTime(slot.startTime)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = participantsCountText(slot.participants.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (countdownText != null) {
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (msLeft <= 0L) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Вертикальный список участников стартовой минуты.
 */
@Composable
private fun StartSlotParticipantList(participants: List<OrienteeringParticipant>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.SIZE_BASE.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
    ) {
        items(participants, key = { it.id }) { participant ->
            StartGridParticipantCard(participant = participant)
        }
    }
}

/**
 * Карточка участника: стартовый номер, ФИО, группа, номер чипа.
 */
@Composable
private fun StartGridParticipantCard(participant: OrienteeringParticipant) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(Dimens.SIZE_BASE.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = participant.startNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${participant.lastName} ${participant.firstName}".trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (participant.groupName.isNotEmpty()) {
                    Text(
                        text = participant.groupName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (participant.chipNumber.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "чип",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = participant.chipNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyView(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SIZE_DOUBLE.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_location_on_24px),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Баннер секундомера соревнования (формат ЧЧ:ММ:СС.сс).
 * Когда соревнование не запущено — показывает подсказку вместо хода времени.
 */
@Composable
private fun StopwatchBanner(
    elapsedMillis: Long,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val hours = elapsedMillis / 3_600_000L
    val minutes = (elapsedMillis % 3_600_000L) / 60_000L
    val seconds = (elapsedMillis % 60_000L) / 1_000L
    val hundredths = (elapsedMillis % 1_000L) / 10
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
    ) {
        Text(
            text = if (isRunning) {
                "%02d:%02d:%02d.%02d".format(hours, minutes, seconds, hundredths)
            } else {
                "Соревнование не запущено"
            },
            style = if (isRunning) MaterialTheme.typography.displaySmall
            else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.SIZE_HALF.dp),
            textAlign = TextAlign.Center
        )
    }
}

/** Форматирует абсолютное стартовое время в ЧЧ:ММ (или ЧЧ:ММ:СС при ненулевых секундах). */
private fun formatTime(startTimeMs: Long): String {
    if (!startTimeMs.isValidStartTimestamp()) return "—"
    val calendar = Calendar.getInstance().apply { timeInMillis = startTimeMs }
    val h = calendar.get(Calendar.HOUR_OF_DAY)
    val m = calendar.get(Calendar.MINUTE)
    val s = calendar.get(Calendar.SECOND)
    return if (s > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(h, m)
}

/** Склонение «N участник/участника/участников». */
private fun participantsCountText(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> "участников"
        mod10 == 1 -> "участник"
        mod10 in 2..4 -> "участника"
        else -> "участников"
    }
    return "$count $word"
}
