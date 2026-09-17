package com.competra.center.presentation.participant_list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.competra.utils.isValidStartTimestamp
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.competra.designsystem.components.DSBottomDialog
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.theme.Dimens
import com.competra.center.data.participant_list.ParticipantListAction
import com.competra.center.data.participant_list.ParticipantListState
import com.competra.domain.models.Gender
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.ParticipantGroupParticipants
import com.competra.resources.R
import com.competra.ui.BaseAction
import com.competra.ui.CompetitionStartTimeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.Calendar

/**
 * Экран отображения и управления списком участников соревнований,
 * разделенный на вкладки по группам участников.
 */
@Composable
fun ParticipantListScreen(
    onSave: () -> Unit = {},
    viewModel: ParticipantListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val userAction = remember { viewModel::onAction }

    LaunchedEffect(viewModel) {
        viewModel.getCompetitionDetails()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        ParticipantListContent(userAction = userAction, state = state, onSave = onSave)
    }

    if (state.isShowParticipantCreateDialog) {
        CreateParticipantDialog(
            userAction = userAction,
            group = state.group,
            groupName = state.participantGroupWithParticipants.getOrNull(state.group)?.group?.title ?: "",
            editingParticipant = state.editingParticipant
        )
    }

    state.deletingParticipant?.let { participant ->
        DeleteParticipantDialog(
            participant = participant,
            onDismiss = { userAction.invoke(ParticipantListAction.HideDeleteParticipantDialog) },
            onConfirm = { userAction.invoke(ParticipantListAction.DeleteParticipant(participant)) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ParticipantListContent(
    userAction: (BaseAction) -> Unit,
    state: ParticipantListState,
    onSave: () -> Unit = {}
) {
    val hasAllTab = state.participantGroupWithParticipants.size > 1
    val allTabIndex = 0
    val pageCount = state.participantGroupWithParticipants.size + if (hasAllTab) 1 else 0
    val pagerState = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    val isOnAllTab = hasAllTab && pagerState.currentPage == allTabIndex

    val isCompetitionRunning = state.competition?.competition?.status == CompetitionStatus.IN_PROGRESS
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isCompetitionRunning) {
        if (isCompetitionRunning) {
            while (true) {
                delay(500)
                currentTimeMs = System.currentTimeMillis()
            }
        }
    }

    val startTimeRepository: CompetitionStartTimeRepository = koinInject()
    val repoStartTimeMs by startTimeRepository.startTimeMs.collectAsState()
    val effectiveStartTime = repoStartTimeMs.takeIf { it > 0L }
        ?: state.competition?.startTime?.takeIf { it > 0L }
        ?: 0L
    var stopwatchMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isCompetitionRunning, effectiveStartTime) {
        if (isCompetitionRunning && effectiveStartTime > 0L) {
            while (true) {
                stopwatchMillis = (System.currentTimeMillis() - effectiveStartTime).coerceAtLeast(0L)
                delay(16)
            }
        } else {
            stopwatchMillis = 0L
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isCompetitionRunning && effectiveStartTime > 0L) {
            StopwatchBanner(
                elapsedMillis = stopwatchMillis,
                modifier = Modifier.padding(
                    horizontal = Dimens.SIZE_BASE.dp,
                    vertical = Dimens.SIZE_HALF.dp
                )
            )
        }
        if (state.participantGroupWithParticipants.isNotEmpty()) {
            SecondaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
                indicator = {
//                    if (pagerState.currentPage < tabPositions.size) {
//                        TabRowDefaults.SecondaryIndicator(
//                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
//                            color = MaterialTheme.colorScheme.primary
//                        )
//                    }
                }
            ) {
                if (hasAllTab) {
                    Tab(
                        selected = isOnAllTab,
                        onClick = { scope.launch { pagerState.animateScrollToPage(allTabIndex) } },
                        text = {
                            Text(
                                text = "Все",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isOnAllTab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
                state.participantGroupWithParticipants.forEachIndexed { index, group ->
                    val pageIndex = if (hasAllTab) index + 1 else index
                    Tab(
                        selected = pagerState.currentPage == pageIndex,
                        onClick = { scope.launch { pagerState.animateScrollToPage(pageIndex) } },
                        text = {
                            Text(
                                text = group.group.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (pagerState.currentPage == pageIndex) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (state.participantGroupWithParticipants.isEmpty()) {
                EmptyParticipantsView(message = "Группы участников не найдены")
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    if (hasAllTab && page == 0) {
                        val allParticipants = state.participantGroupWithParticipants
                            .flatMap { it.participants }
                            .sortedWith(
                                compareBy(
                                    { if (isValidTimestamp(it.startTime)) it.startTime else Long.MAX_VALUE },
                                    { it.startNumber.toIntOrNull() ?: Int.MAX_VALUE }
                                )
                            )
                        if (allParticipants.isEmpty()) {
                            EmptyParticipantsView(message = "Нет участников")
                        } else {
                            ParticipantList(
                                participants = allParticipants,
                                showGroupName = true,
                                currentTimeMs = currentTimeMs,
                                isCompetitionRunning = isCompetitionRunning,
                                onEditClick = { participant ->
                                    val groupIndex = state.participantGroupWithParticipants
                                        .indexOfFirst { it.group.groupId == participant.groupId }
                                    if (groupIndex >= 0) {
                                        userAction.invoke(
                                            ParticipantListAction.ShowEditParticipantDialog(groupIndex, participant)
                                        )
                                    }
                                },
                                onDeleteClick = { participant ->
                                    userAction.invoke(ParticipantListAction.ShowDeleteParticipantDialog(participant))
                                }
                            )
                        }
                    } else {
                        val groupIndex = if (hasAllTab) page - 1 else page
                        val participants = state.participantGroupWithParticipants[groupIndex].participants
                            .sortedWith(
                                compareBy(
                                    { if (isValidTimestamp(it.startTime)) it.startTime else Long.MAX_VALUE },
                                    { it.startNumber.toIntOrNull() ?: Int.MAX_VALUE }
                                )
                            )

                        if (participants.isEmpty()) {
                            EmptyParticipantsView(message = "В этой группе пока нет участников")
                        } else {
                            ParticipantList(
                                participants = participants,
                                currentTimeMs = currentTimeMs,
                                isCompetitionRunning = isCompetitionRunning,
                                onEditClick = { participant ->
                                    userAction.invoke(ParticipantListAction.ShowEditParticipantDialog(groupIndex, participant))
                                },
                                onDeleteClick = { participant ->
                                    userAction.invoke(ParticipantListAction.ShowDeleteParticipantDialog(participant))
                                }
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = onSave,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Dimens.SIZE_BASE.dp),
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_check_24px),
                        contentDescription = "Save"
                    )
                    Text(
                        text = "Сохранить",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!isOnAllTab) {
                FloatingActionButton(
                    onClick = {
                        if (state.participantGroupWithParticipants.isNotEmpty()) {
                            val groupIndex = if (hasAllTab) pagerState.currentPage - 1 else pagerState.currentPage
                            userAction.invoke(ParticipantListAction.ShowCreateParticipantDialog(groupIndex))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Dimens.SIZE_BASE.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_add_24px),
                        contentDescription = "Add participant"
                    )
                }
            }

            // DEBUG: генерация тестовых участников — только в debug-сборке и только для
            // тестового соревнования. Размещается над кнопкой добавления участника.
            val context = LocalContext.current
            val isDebuggable = remember {
                (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            }
            val isTestCompetition = state.competition?.competition?.isTest == true
            if (isDebuggable && isTestCompetition && state.participantGroupWithParticipants.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { userAction.invoke(ParticipantListAction.GenerateTestParticipants) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Dimens.SIZE_BASE.dp)
                        .padding(bottom = 72.dp),
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ) {
                    Text(
                        text = "DEBUG: +5 в группу",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyParticipantsView(message: String) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateParticipantDialog(
    userAction: (BaseAction) -> Unit,
    group: Int,
    groupName: String,
    editingParticipant: OrienteeringParticipant?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    DSBottomDialog(
        sheetState = sheetState,
        sheetContent = {
            CreateParticipantDialogContent(
                userAction = userAction,
                group = group,
                groupName = groupName,
                editingParticipant = editingParticipant
            )
        },
        onDismiss = { userAction.invoke(ParticipantListAction.HideCreateParticipantDialog) },
    )
}

@Composable
fun CreateParticipantDialogContent(
    userAction: (BaseAction) -> Unit,
    group: Int,
    groupName: String,
    editingParticipant: OrienteeringParticipant?
) {
    var firstName by remember(editingParticipant) { mutableStateOf(editingParticipant?.firstName ?: "") }
    var secondName by remember(editingParticipant) { mutableStateOf(editingParticipant?.lastName ?: "") }
    var startTimeInput by remember(editingParticipant) {
        mutableStateOf(
            if (editingParticipant != null && isValidTimestamp(editingParticipant.startTime))
                formatStartTime(editingParticipant.startTime)
            else ""
        )
    }
    var startTimeError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .padding(Dimens.SIZE_BASE.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = if (editingParticipant == null) "Новый участник" else "Редактирование",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Группа: $groupName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = Dimens.SIZE_HALF.dp)
        )

        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

        DSTextInput(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label = { Text(text = "Имя") },
            text = firstName,
            onValueChanged = { firstName = it },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Фамилия") },
            text = secondName,
            onValueChanged = { secondName = it },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        if (editingParticipant != null) {
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

            DSTextInput(
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Стартовое время (ЧЧ:ММ или ЧЧ:ММ:СС или ММ:СС)") },
                text = startTimeInput,
                onValueChanged = {
                    startTimeInput = it
                    startTimeError = false
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                isError = startTimeError
            )

            if (startTimeError) {
                Text(
                    text = "Введите время в формате ЧЧ:ММ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = Dimens.SIZE_HALF.dp, top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
            onClick = {
                if (firstName.isNotEmpty() && secondName.isNotEmpty()) {
                    if (editingParticipant == null) {
                        userAction.invoke(
                            ParticipantListAction.CreateNewParticipant(
                                group = group,
                                firstName = firstName,
                                secondName = secondName
                            )
                        )
                        firstName = ""
                        secondName = ""
                        focusRequester.requestFocus()
                    } else {
                        val parsedStartTime = if (startTimeInput.isNotEmpty()) {
                            parseTimeInput(startTimeInput, editingParticipant.startTime)
                        } else {
                            editingParticipant.startTime
                        }
                        if (startTimeInput.isNotEmpty() && parsedStartTime == null) {
                            startTimeError = true
                            return@Button
                        }
                        userAction.invoke(
                            ParticipantListAction.UpdateParticipant(
                                participant = editingParticipant.copy(
                                    firstName = firstName,
                                    lastName = secondName,
                                    startTime = parsedStartTime ?: editingParticipant.startTime
                                )
                            )
                        )
                    }
                }
            }
        ) {
            Text(
                text = if (editingParticipant == null) "Добавить участника" else "Сохранить изменения",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteParticipantDialog(
    participant: OrienteeringParticipant,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    DSBottomDialog(
        sheetState = sheetState,
        sheetContent = {
            Column(
                modifier = Modifier
                    .padding(Dimens.SIZE_BASE.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Удалить участника?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                Text(
                    text = "${participant.firstName} ${participant.lastName} (№${participant.startNumber})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = onConfirm
                ) {
                    Text(text = "Удалить", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
                    onClick = onDismiss
                ) {
                    Text(text = "Отмена", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
            }
        },
        onDismiss = onDismiss
    )
}

@Composable
fun ParticipantList(
    participants: List<OrienteeringParticipant>,
    showGroupName: Boolean = false,
    currentTimeMs: Long = 0L,
    isCompetitionRunning: Boolean = false,
    onEditClick: (OrienteeringParticipant) -> Unit,
    onDeleteClick: (OrienteeringParticipant) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.SIZE_BASE.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
    ) {
        itemsIndexed(participants) { index, participant ->
            ParticipantCard(
                participant = participant,
                displayIndex = index + 1,
                showGroupName = showGroupName,
                currentTimeMs = currentTimeMs,
                isCompetitionRunning = isCompetitionRunning,
                onEditClick = { onEditClick(participant) },
                onDeleteClick = { onDeleteClick(participant) }
            )
        }
    }
}

@Composable
fun ParticipantCard(
    participant: OrienteeringParticipant,
    displayIndex: Int,
    showGroupName: Boolean = false,
    currentTimeMs: Long = 0L,
    isCompetitionRunning: Boolean = false,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val hasValidStart = isValidTimestamp(participant.startTime)
    val startTimeText = if (hasValidStart) formatStartTime(participant.startTime) else "—"
    val hasStarted = hasValidStart && isCompetitionRunning && participant.startTime <= currentTimeMs
    val secondsLeft = if (hasValidStart && isCompetitionRunning && !hasStarted)
        ((participant.startTime - currentTimeMs + 999) / 1000).toInt().coerceIn(0, 5)
    else -1
    val isCountingDown = secondsLeft in 0..5

    val cardColor = when {
        hasStarted -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
        isCountingDown -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(Dimens.SIZE_BASE.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Номер участника
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = participant.startNumber.ifEmpty { displayIndex.toString() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${participant.firstName} ${participant.lastName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (showGroupName && participant.groupName.isNotEmpty()) {
                    Text(
                        text = participant.groupName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (participant.commandName.isNotEmpty()) {
                    Text(
                        text = participant.commandName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    isCountingDown -> Text(
                        text = "${secondsLeft}с",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    hasStarted -> Text(
                        text = "✓ $startTimeText",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    else -> Text(
                        text = "Старт: $startTimeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.edit),
                    contentDescription = "Edit participant",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), CircleShape)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.delete),
                    contentDescription = "Delete participant",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** Возвращает true, если [ms] — это реальная дата (не плейсхолдер и не отрицательное значение). */
private fun isValidTimestamp(ms: Long): Boolean = ms.isValidStartTimestamp()

private fun formatStartTime(startTimeMs: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = startTimeMs
    val h = calendar.get(Calendar.HOUR_OF_DAY)
    val m = calendar.get(Calendar.MINUTE)
    val s = calendar.get(Calendar.SECOND)
    return if (s > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", h, m)
    }
}

/**
 * Парсит строку времени в Unix timestamp (мс).
 *
 * Поддерживаемые форматы:
 * - "ЧЧ:ММ"      (2 части, первая ≤ 23) → часы:минуты
 * - "ММ:СС"      (2 части, первая > 23) → минуты:секунды
 * - "ЧЧ:ММ:СС"  (3 части)              → часы:минуты:секунды
 *
 * Дата берётся из [baseDateMs] если это реальная дата, иначе — текущая.
 * Возвращает null при неверном формате.
 */
private fun parseTimeInput(timeStr: String, baseDateMs: Long): Long? {
    val parts = timeStr.trim().split(":")
    val hours: Int
    val minutes: Int
    val seconds: Int

    when (parts.size) {
        2 -> {
            val first = parts[0].toIntOrNull() ?: return null
            val second = parts[1].toIntOrNull() ?: return null
            if (second !in 0..59) return null
            if (first > 23) {
                // Интерпретируем как ММ:СС
                if (first > 59) return null
                hours = 0
                minutes = first
                seconds = second
            } else {
                // Интерпретируем как ЧЧ:ММ
                hours = first
                minutes = second
                seconds = 0
            }
        }
        3 -> {
            hours = parts[0].toIntOrNull() ?: return null
            minutes = parts[1].toIntOrNull() ?: return null
            seconds = parts[2].toIntOrNull() ?: return null
            if (hours !in 0..23 || minutes !in 0..59 || seconds !in 0..59) return null
        }
        else -> return null
    }

    val calendar = Calendar.getInstance()
    // Используем дату из baseDateMs только если это реальная дата, иначе — сегодня
    if (isValidTimestamp(baseDateMs)) {
        calendar.timeInMillis = baseDateMs
    }
    calendar.set(Calendar.HOUR_OF_DAY, hours)
    calendar.set(Calendar.MINUTE, minutes)
    calendar.set(Calendar.SECOND, seconds)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

/**
 * Баннер секундомера — показывает прошедшее время соревнования в формате ЧЧ:ММ:СС.сс.
 */
@Composable
private fun StopwatchBanner(elapsedMillis: Long, modifier: Modifier = Modifier) {
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
            text = "%02d:%02d:%02d.%02d".format(hours, minutes, seconds, hundredths),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.SIZE_HALF.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ParticipantListScreenPreview() {
    MaterialTheme {
        ParticipantListContent(
            userAction = {},
            state = ParticipantListState(
                participantGroupWithParticipants = listOf(
                    ParticipantGroupParticipants(
                        group = ParticipantGroup(
                            groupId = 1L,
                            competitionId = "1",
                            title = "M21",
                            gender = Gender.MALE,
                            minAge = 21,
                            maxAge = null,
                            distanceId = 1L,
                            maxParticipants = 100,
                            isSynced = true,
                            lastModified = System.currentTimeMillis()
                        ),
                        participants = listOf(
                            OrienteeringParticipant(
                                id = "id1",
                                userId = "1",
                                firstName = "Иван",
                                lastName = "Иванов",
                                groupId = 1,
                                groupName = "M21",
                                competitionId = "1",
                                commandName = "Зенит",
                                startNumber = "1",
                                startTime = 10L,
                                chipNumber = "12345",
                                comment = "",
                                isChipGiven = false
                            )
                        )
                    )
                )
            )
        )
    }
}
