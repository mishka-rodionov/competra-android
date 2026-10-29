package com.competra.center.presentation.draw

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.competra.center.data.draw.DrawAction
import com.competra.center.presentation.participant_list.ParticipantCard
import com.competra.designsystem.theme.Dimens
import com.competra.resources.R
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Экран для жеребьевки участников соревнования.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DrawParticipantsScreen(viewModel: DrawViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val userAction = remember { viewModel::onAction }
    val scope = rememberCoroutineScope()

    val participantsByGroup = remember(state.participants) {
        state.participants
            .groupBy { it.groupId }
            .entries
            .sortedBy { it.value.firstOrNull()?.groupName ?: "" }
            .map { (_, participants) ->
                participants.first().groupName to
                    participants.sortedBy { it.startNumber.toIntOrNull() ?: Int.MAX_VALUE }
            }
    }

    val pagerState = rememberPagerState { participantsByGroup.size }
    var cardsVisible by remember { mutableStateOf(true) }
    var distanceDialogVisible by remember { mutableStateOf(false) }

    if (distanceDialogVisible) {
        DistanceDrawDialog(
            onDismiss = { distanceDialogVisible = false },
            onConfirm = { corridors, gap ->
                distanceDialogVisible = false
                userAction.invoke(DrawAction.StartDistanceDrawOperation(corridors, gap))
            }
        )
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -10f) cardsVisible = false
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0f) cardsVisible = true
                return Offset.Zero
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            AnimatedVisibility(visible = cardsVisible) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Жеребьёвка",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(Dimens.SIZE_BASE.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max)
                            .padding(horizontal = Dimens.SIZE_BASE.dp)
                            .padding(bottom = Dimens.SIZE_HALF.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
                    ) {
                        DrawModeCard(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = ImageVector.vectorResource(R.drawable.ic_shuffle_24px),
                            title = "Общая жеребьёвка",
                            description = "Группы чередуются, старты не пересекаются",
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = { userAction.invoke(DrawAction.StartDrawOperation) }
                        )
                        DrawModeCard(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = ImageVector.vectorResource(R.drawable.ic_groups_24px),
                            title = "По группам",
                            description = "Каждая группа перемешивается независимо",
                            accentColor = MaterialTheme.colorScheme.secondary,
                            onClick = { userAction.invoke(DrawAction.StartGroupDrawOperation) }
                        )
                    }
                    DrawModeCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.SIZE_BASE.dp)
                            .padding(bottom = Dimens.SIZE_BASE.dp),
                        icon = ImageVector.vectorResource(R.drawable.map_24dp),
                        title = "По дистанциям",
                        description = if (state.groupsWithoutDistance.isEmpty()) {
                            "Участники с одинаковой дистанцией не стартуют вместе"
                        } else {
                            "Недоступно: не назначена дистанция у групп ${state.groupsWithoutDistance.joinToString()}"
                        },
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        enabled = state.groupsWithoutDistance.isEmpty(),
                        onClick = { distanceDialogVisible = true }
                    )
                }
            }

            if (state.participants.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = if (cardsVisible) 0.dp else 4.dp
                ) {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp)
                        )
                        Text(
                            text = "Результаты жеребьёвки",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = Dimens.SIZE_BASE.dp,
                                end = Dimens.SIZE_BASE.dp,
                                top = Dimens.SIZE_HALF.dp,
                                bottom = Dimens.SIZE_HALF.dp
                            )
                        )
                        if (participantsByGroup.size > 1) {
                            SecondaryScrollableTabRow(
                                selectedTabIndex = pagerState.currentPage,
                                edgePadding = 16.dp,
                                containerColor = MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.primary,
                                divider = {}
                            ) {
                                participantsByGroup.forEachIndexed { index, (groupName, _) ->
                                    Tab(
                                        selected = pagerState.currentPage == index,
                                        onClick = {
                                            scope.launch { pagerState.animateScrollToPage(index) }
                                        },
                                        text = {
                                            Text(
                                                text = groupName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (pagerState.currentPage == index)
                                                    FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val participants = participantsByGroup[page].second
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Dimens.SIZE_BASE.dp,
                            end = Dimens.SIZE_BASE.dp,
                            top = Dimens.SIZE_HALF.dp,
                            bottom = Dimens.SIZE_BASE.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
                    ) {
                        itemsIndexed(
                            items = participants,
                            key = { _, p -> p.id }
                        ) { _, participant ->
                            ParticipantCard(
                                participant = participant,
                                onEditClick = {},
                                onDeleteClick = {}
                            )
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f)) {
                    EmptyDrawView()
                }
            }
        }
    }
}

/**
 * Диалог ввода параметров жеребьёвки по дистанциям.
 *
 * @param onConfirm вызывается с числом коридоров и зазором (оба ≥ 1) при подтверждении.
 */
@Composable
private fun DistanceDrawDialog(
    onDismiss: () -> Unit,
    onConfirm: (corridors: Int, gap: Int) -> Unit
) {
    var corridorsText by remember { mutableStateOf("3") }
    var gapText by remember { mutableStateOf("2") }

    val corridors = corridorsText.toIntOrNull()
    val gap = gapText.toIntOrNull()
    val isValid = corridors != null && corridors >= 1 && gap != null && gap >= 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Жеребьёвка по дистанциям") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_BASE.dp)) {
                Text(
                    text = "Участники одной дистанции не стартуют в одну минуту.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = corridorsText,
                    onValueChange = { corridorsText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Стартовых коридоров") },
                    supportingText = { Text("Максимум одновременных стартов в минуту") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gapText,
                    onValueChange = { gapText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Зазор (интервалов)") },
                    supportingText = { Text("Минимум интервалов между стартами одной дистанции") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(corridors!!, gap!!) },
                enabled = isValid
            ) {
                Text("Начать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun DrawModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val cornerRadius = Dimens.SIZE_BASE.dp
    Card(
        modifier = modifier.dashedBorder(1.5.dp, accentColor.copy(alpha = 0.4f), cornerRadius),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.SIZE_BASE.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                maxLines = if (enabled) 3 else 5,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimens.SIZE_HALF.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text(text = "Начать", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun Modifier.dashedBorder(
    strokeWidth: Dp,
    color: Color,
    cornerRadius: Dp
): Modifier = this.drawWithContent {
    drawContent()
    val strokePx = strokeWidth.toPx()
    val stroke = Stroke(
        width = strokePx,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
    )
    val inset = strokePx / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = stroke
    )
}

/**
 * Вид при отсутствии участников для жеребевки.
 */
@Composable
private fun EmptyDrawView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.SIZE_DOUBLE.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_location_on_24px),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
        Text(
            text = "Жеребьёвка ещё не проводилась.\nВыберите режим и нажмите «Начать».",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
