package com.competra.events.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.competra.designsystem.components.clickRipple
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.Competition
import com.competra.domain.models.Coordinates
import com.competra.domain.models.KindOfSport
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.models.orienteering.ResultsStatus
import com.competra.resources.R
import com.competra.events.data.main.EventsAction
import com.competra.events.data.main.TodayStart
import com.competra.eventdetails.presentation.live_track_runner.LiveTrackConsentDialog
import com.competra.eventdetails.presentation.live_track_runner.LiveTrackPermissionsEffect
import com.competra.events.presentation.filter.EventsFilterBottomSheet
import com.competra.utils.DateTimeFormat
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel

/**
 * Экран списка событий. Список обновляется жестом pull-to-refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(viewModel: EventsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val events = viewModel.events.collectAsLazyPagingItems()
    val refreshState = events.loadState.refresh

    Column(modifier = Modifier.fillMaxSize()) {
        EventsFilterBar(
            isFilterActive = !state.appliedFilter.isEmpty,
            onOpenFilter = { viewModel.onAction(EventsAction.OpenFilterDialog) },
            onResetFilter = { viewModel.onAction(EventsAction.ResetFilter) }
        )

        // Над списком, а не в нём: карточка нужна и когда список не загрузился (нет сети на старте).
        state.todayStarts.forEach { todayStart ->
            TodayStartCard(
                todayStart = todayStart,
                onAction = viewModel::onAction,
                modifier = Modifier.padding(
                    start = Dimens.SIZE_BASE.dp,
                    end = Dimens.SIZE_BASE.dp,
                    bottom = Dimens.SIZE_HALF.dp
                )
            )
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = {
                viewModel.onAction(EventsAction.Refresh)
                events.refresh()
            },
            modifier = Modifier.fillMaxSize()
        ) {
            // Ошибку на весь экран показываем, только если показать нечего: при неудачном
            // обновлении уже загруженный список остаётся (о сбое сообщит глобальный диалог).
            if (refreshState is LoadState.Error && events.itemCount == 0) {
                EventsStateContainer {
                    EventsErrorState(
                        onRetry = { events.retry() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (events.itemCount == 0 && refreshState !is LoadState.Loading) {
                EventsStateContainer {
                    EventsEmptyState(
                        isFilterActive = !state.appliedFilter.isEmpty,
                        onResetFilter = { viewModel.onAction(EventsAction.ResetFilter) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALFER.dp), // Отступ между карточками 12 dp
                    contentPadding = PaddingValues(
                        horizontal = Dimens.SIZE_BASE.dp, // Поля по бокам 16 dp
                        vertical = Dimens.SIZE_HALFER.dp   // Поля сверху/снизу 12 dp
                    )
                ) {
                    items(count = events.itemCount, key = events.itemKey { it.id }) { index ->
                        events[index]?.let { item ->
                            EventItem(item, userAction = viewModel::onAction)
                        }
                    }
                    item {
                        EventsAppendFooter(
                            loadState = events.loadState.append,
                            onRetry = { events.retry() }
                        )
                    }
                }
            }
        }
    }

    state.liveTrackConsentFor?.let {
        LiveTrackConsentDialog(
            onAccept = { viewModel.onAction(EventsAction.LiveTrackConsentAccepted) },
            onDismiss = { viewModel.onAction(EventsAction.LiveTrackConsentDismissed) }
        )
    }
    LiveTrackPermissionsEffect(viewModel.liveTrackPermissionRequests) { granted ->
        viewModel.onAction(EventsAction.LiveTrackPermissionsResult(granted))
    }

    if (state.isFilterDialogOpen) {
        EventsFilterBottomSheet(
            initialFilter = state.appliedFilter,
            onApply = { viewModel.onAction(EventsAction.ApplyFilter(it)) },
            onDismiss = { viewModel.onAction(EventsAction.CloseFilterDialog) }
        )
    }
}

/**
 * Карточка «Сегодня старт»: соревнование пользователя на сегодня из локального кэша и кнопка
 * онлайн-трека, которая работает без интернета. Тап по карточке открывает детали соревнования.
 */
@Composable
private fun TodayStartCard(
    todayStart: TodayStart,
    onAction: (EventsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val start = todayStart.start
    val session = todayStart.session
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickRipple { onAction(EventsAction.EventClick(start.competitionId)) },
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(Dimens.SIZE_BASE.dp)) {
            Text(
                text = stringResource(R.string.today_start_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(Dimens.SIZE_QUARTER.dp))
            Text(
                text = start.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.today_start_time, DateTimeFormat.transformLongToTime(start.startDate, start.zone)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            if (todayStart.isTrackInProgress) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            if (session?.isRecording == true) R.string.today_start_recording else R.string.today_start_uploading
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { onAction(EventsAction.OpenLiveTrack(start.competitionId)) }) {
                        Text(text = stringResource(R.string.today_start_open_track))
                    }
                }
            } else {
                Button(
                    onClick = { onAction(EventsAction.StartLiveTrack(start.competitionId)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.today_start_track_button))
                }
                Spacer(modifier = Modifier.height(Dimens.SIZE_QUARTER.dp))
                Text(
                    text = stringResource(R.string.today_start_track_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Прокручиваемая обёртка для пустого состояния и ошибки на весь экран: без вложенной прокрутки
 * жест pull-to-refresh на них не срабатывает.
 */
@Composable
private fun EventsStateContainer(content: @Composable () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Box(modifier = Modifier.fillParentMaxSize()) { content() } }
    }
}

/** Футер списка: спиннер во время догрузки следующей страницы, кнопка повтора при ошибке. */
@Composable
private fun EventsAppendFooter(loadState: LoadState, onRetry: () -> Unit) {
    when (loadState) {
        is LoadState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SIZE_BASE.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        is LoadState.Error -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SIZE_BASE.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.events_error_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))
                TextButton(onClick = onRetry, modifier = Modifier.wrapContentSize()) {
                    Text(text = stringResource(R.string.events_error_retry))
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun EventsEmptyState(
    isFilterActive: Boolean,
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleRes = if (isFilterActive) {
        R.string.events_empty_filter_title
    } else {
        R.string.events_empty_title
    }
    val subtitleRes = if (isFilterActive) {
        R.string.events_empty_filter_subtitle
    } else {
        R.string.events_empty_subtitle
    }

    Column(
        modifier = modifier.padding(Dimens.SIZE_DOUBLE.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_info_24px),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_TWO.dp))
        Text(
            text = stringResource(id = titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_SINGLE.dp))
        Text(
            text = stringResource(id = subtitleRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (isFilterActive) {
            Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))
            OutlinedButton(onClick = onResetFilter) {
                Text(text = stringResource(id = R.string.filter_reset))
            }
        }
    }
}

/** Экран-заглушка при ошибке загрузки списка соревнований. */
@Composable
private fun EventsErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Dimens.SIZE_DOUBLE.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_info_24px),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_TWO.dp))
        Text(
            text = stringResource(id = R.string.events_error_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_SINGLE.dp))
        Text(
            text = stringResource(id = R.string.events_error_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(id = R.string.events_error_retry))
        }
    }
}

@Composable
private fun EventsFilterBar(
    isFilterActive: Boolean,
    onOpenFilter: () -> Unit,
    onResetFilter: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SIZE_TWO.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFilterActive) {
            IconButton(onClick = onResetFilter) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.filter_off),
                    contentDescription = stringResource(R.string.filter_reset),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(onClick = onOpenFilter) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.filter),
                contentDescription = stringResource(R.string.filter_open),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Карточка события в списке.
 *
 * @param event Модель данных соревнования.
 * @param userAction Коллбэк для обработки действий пользователя.
 * @param index Индекс элемента в списке.
 */
@Composable
fun EventItem(
    event: Competition,
    userAction: (EventsAction) -> Unit
) {
    val accent = statusColor(event.status)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickRipple {
                userAction.invoke(EventsAction.EventClick(event.id))
            },
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp), // Скругление карточки 16 dp
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.SIZE_TWO.dp), // 2 dp
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(Dimens.SIZE_SINGLE.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Боковая акцент-полоса по статусу соревнования
            Box(
                modifier = Modifier
                    .width(Dimens.SIZE_HALF.dp)
                    .fillMaxHeight()
                    .background(accent)
            )

            Column(modifier = Modifier.padding(Dimens.SIZE_BASE.dp)) {
                // Верхняя строка: бейдж вида спорта + статус
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SportBadge(event.kindOfSport.name)
                    Spacer(modifier = Modifier.weight(1f))
                    StatusChip(event.status)
                }

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                // Заголовок
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_SIX.dp))

                // Дата и время
                val eventZone = runCatching { java.time.ZoneId.of(event.timeZoneId) }
                    .getOrDefault(java.time.ZoneId.systemDefault())
                MetaRow(
                    iconRes = com.competra.resources.R.drawable.ic_date_range_24px,
                    text = DateTimeFormat.transformLongToDisplayDate(event.startDate, eventZone) +
                        " " + DateTimeFormat.transformLongToTime(event.startDate, eventZone) +
                        " (" + event.timeZoneId + ")"
                )

                if (!event.address.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Dimens.SIZE_QUARTER.dp))
                    MetaRow(
                        iconRes = com.competra.resources.R.drawable.ic_location_on_24px,
                        text = event.address ?: ""
                    )
                }
            }
        }
    }
}

/** Строка мета-информации: иконка + текст (дата, место). */
@Composable
private fun MetaRow(iconRes: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(Dimens.SIZE_QUARTER.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Бейдж вида спорта. */
@Composable
private fun SportBadge(name: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.SIZE_BASE.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = Dimens.SIZE_HALF.dp, vertical = Dimens.SIZE_QUARTER.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Чип статуса соревнования.
 * Активные статусы (открытая регистрация, идущие) выделяются сплошной заливкой,
 * пассивные — мягким тинтом, чтобы важные события сразу бросались в глаза.
 */
@Composable
private fun StatusChip(status: CompetitionStatus) {
    val label = statusLabel(status)
    if (label.isEmpty()) return
    val color = statusColor(status)

    if (isEmphasizedStatus(status)) {
        // Сплошная яркая плашка с контрастным текстом
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.SIZE_BASE.dp))
                .background(color)
                .padding(horizontal = Dimens.SIZE_TEN.dp, vertical = Dimens.SIZE_SIX.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = statusOnColor(status)
            )
        }
    } else {
        // Приглушённый тинт + точка
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.SIZE_BASE.dp))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = Dimens.SIZE_HALF.dp, vertical = Dimens.SIZE_QUARTER.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.SIZE_HALF.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(Dimens.SIZE_QUARTER.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

/** Активные статусы, которые нужно акцентировать сильнее остальных. */
private fun isEmphasizedStatus(status: CompetitionStatus): Boolean =
    status == CompetitionStatus.REGISTRATION_OPEN || status == CompetitionStatus.IN_PROGRESS

/** Контрастный цвет текста для сплошной плашки активного статуса. */
@Composable
private fun statusOnColor(status: CompetitionStatus): Color = when (status) {
    CompetitionStatus.REGISTRATION_OPEN -> MaterialTheme.colorScheme.onPrimary
    CompetitionStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onSecondary
    else -> MaterialTheme.colorScheme.onSurface
}

/** Семантический цвет статуса (см. DESIGN.md). */
@Composable
private fun statusColor(status: CompetitionStatus): Color = when (status) {
    CompetitionStatus.REGISTRATION_OPEN -> MaterialTheme.colorScheme.primary
    CompetitionStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
    CompetitionStatus.REGISTRATION_CLOSED -> MaterialTheme.colorScheme.tertiary
    CompetitionStatus.FINISHED, CompetitionStatus.ARCHIVED -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.onSurfaceVariant // DRAFT, CREATED, UNKNOWN
}

/** Подпись статуса из строковых ресурсов. */
@Composable
private fun statusLabel(status: CompetitionStatus): String = when (status) {
    CompetitionStatus.DRAFT -> stringResource(R.string.status_draft)
    CompetitionStatus.CREATED -> stringResource(R.string.status_created)
    CompetitionStatus.REGISTRATION_OPEN -> stringResource(R.string.status_registration_open)
    CompetitionStatus.REGISTRATION_CLOSED -> stringResource(R.string.status_registration_closed)
    CompetitionStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
    CompetitionStatus.FINISHED -> stringResource(R.string.status_finished)
    CompetitionStatus.ARCHIVED -> stringResource(R.string.status_archived)
    CompetitionStatus.UNKNOWN -> ""
}

@Preview(showBackground = true)
@Composable
fun EventItemPreview() {
    val mockEvent = Competition(
        title = "Чемпионат по спортивному ориентированию \"Осенний лес 2024\"",
        startDate = System.currentTimeMillis(),
        endDate = System.currentTimeMillis() + 86400000L,
        kindOfSport = KindOfSport.Orienteering,
        description = "Традиционные соревнования в живописном лесу. Вас ждут интересные дистанции различной сложности, электронная отметка и горячий чай на финише. Приглашаются все желающие!",
        address = "Загородный парк, г. Владимир",
        mainOrganizerId = "125",
        coordinates = Coordinates(0.0, 0.0),
        status = CompetitionStatus.DRAFT,
        resultsStatus = ResultsStatus.NOT_PUBLISHED,
        timeZoneId = "Europe/Moscow"
    )

    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            EventItem(
                event = mockEvent,
                userAction = {}
            )
        }
    }
}
