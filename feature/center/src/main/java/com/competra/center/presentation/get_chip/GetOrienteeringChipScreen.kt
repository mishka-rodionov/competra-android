package com.competra.center.presentation.get_chip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.components.clickRipple
import com.competra.designsystem.theme.Dimens
import com.competra.center.data.get_chip.GetOrienteeringChipAction
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.resources.R
import com.competra.utils.DateTimeFormat
import com.competra.utils.isValidStartTimestamp
import org.koin.androidx.compose.koinViewModel

/**
 * Экран для выдачи чипов участникам соревнований.
 * Содержит вкладки по группам участников.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetOrienteeringChipScreen(
    competitionId: String,
    viewModel: GetOrienteeringChipViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadParticipants(competitionId)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Заголовок экрана
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SIZE_BASE.dp)
            ) {
                Text(
                    text = "Выдача чипов",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Введите номера чипов и отметьте выданные",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.groupsWithParticipants.isNotEmpty()) {
                val groups = state.groupsWithParticipants
                // Вкладка «Все» показывается только когда групп больше одной.
                val hasAllTab = groups.size > 1
                val tabCount = groups.size + if (hasAllTab) 1 else 0
                val safeTabIndex = selectedTabIndex.coerceIn(0, tabCount - 1)
                val isAllTab = hasAllTab && safeTabIndex == 0

                // Табы: «Все» (если есть) + по группам
                SecondaryScrollableTabRow(
                    modifier = Modifier.fillMaxWidth(),
                    selectedTabIndex = safeTabIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {},
                    indicator = {}
                ) {
                    if (hasAllTab) {
                        Tab(
                            selected = isAllTab,
                            onClick = { selectedTabIndex = 0 },
                            text = {
                                Text(
                                    text = "Все",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isAllTab) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                    groups.forEachIndexed { index, groupWithParticipants ->
                        val tabIndex = if (hasAllTab) index + 1 else index
                        Tab(
                            selected = safeTabIndex == tabIndex,
                            onClick = { selectedTabIndex = tabIndex },
                            text = {
                                Text(
                                    text = groupWithParticipants.group.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (safeTabIndex == tabIndex) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // На вкладке «Все» — плоский список всех участников, отсортированный по
                // стартовому времени (затем по стартовому номеру). На вкладке группы — её участники.
                val participantsToShow = if (isAllTab) {
                    groups.flatMap { it.participants }
                        .sortedWith(
                            compareBy(
                                { if (isValidTimestamp(it.startTime)) it.startTime else Long.MAX_VALUE },
                                { it.startNumber.toIntOrNull() ?: Int.MAX_VALUE }
                            )
                        )
                } else {
                    val groupIndex = if (hasAllTab) safeTabIndex - 1 else safeTabIndex
                    groups.getOrNull(groupIndex)?.participants ?: emptyList()
                }

                Column(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(Dimens.SIZE_BASE.dp),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
                    ) {
                        items(participantsToShow, key = { it.id }) { participant ->
                            ParticipantChipCard(
                                participant = participant,
                                showGroupName = isAllTab,
                                onChipGivenChanged = { isGiven ->
                                    viewModel.onAction(
                                        GetOrienteeringChipAction.ToggleChipGiven(
                                            participant.id,
                                            isGiven
                                        )
                                    )
                                },
                                onChipNumberChanged = { newNumber ->
                                    viewModel.onAction(
                                        GetOrienteeringChipAction.UpdateChipNumber(
                                            participant.id,
                                            newNumber
                                        )
                                    )
                                }
                            )
                        }
                    }

                    // Кнопка сохранения изменений
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.SIZE_BASE.dp)
                    ) {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            onClick = { viewModel.onAction(GetOrienteeringChipAction.SaveChanges) },
                            enabled = !state.isSaving,
                            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (state.isSaving) "Сохранение..." else "Сохранить изменения",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (state.isLoading) {
                LoadingPlaceholder()
            } else {
                EmptyPlaceholder(message = "Участники не найдены")
            }
        }
    }
}

/**
 * Карточка участника для выдачи чипа.
 */
@Composable
private fun ParticipantChipCard(
    participant: OrienteeringParticipant,
    onChipNumberChanged: (String) -> Unit,
    onChipGivenChanged: (Boolean) -> Unit,
    showGroupName: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (participant.isChipGiven)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(Dimens.SIZE_BASE.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${participant.lastName} ${participant.firstName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (showGroupName && participant.groupName.isNotEmpty()) {
                    Text(
                        text = participant.groupName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Старт: ${
                        if (participant.startTime.isValidStartTimestamp()) {
                            DateTimeFormat.transformLongToTime(participant.startTime)
                        } else {
                            "—"
                        }
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))

            // Ввод номера чипа
            DSTextInput(
                modifier = Modifier.width(100.dp),
                text = participant.chipNumber,
                onValueChanged = onChipNumberChanged,
                label = { Text("Чип") }
            )

            Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))

            // Чекбокс выдачи
            Checkbox(
                checked = participant.isChipGiven,
                onCheckedChange = onChipGivenChanged,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** true, если стартовое время реально установлено (а не 0/мусор). */
private fun isValidTimestamp(ms: Long): Boolean = ms.isValidStartTimestamp()

@Composable
private fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyPlaceholder(message: String) {
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
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
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
