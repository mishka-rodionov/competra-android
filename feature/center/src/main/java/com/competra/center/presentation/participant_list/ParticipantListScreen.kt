package com.competra.center.presentation.participant_list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.competra.center.data.draw.LateEntryPlacement
import com.competra.center.data.participant_list.LateEntryPreview
import com.competra.center.data.participant_list.ParticipantListAction
import com.competra.center.data.participant_list.ParticipantListState
import com.competra.domain.models.cyclic_event.protocolCommandSuggestions
import com.competra.domain.models.cyclic_event.protocolCommandNames
import com.competra.domain.models.cyclic_event.normalizeCommandName
import com.competra.domain.models.cyclic_event.COMMAND_NAME_MAX_LENGTH
import com.competra.domain.models.Gender
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.canBeMarkedDns
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.models.orienteering.isParticipantDeletionLocked
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.ParticipantGroupParticipants
import com.competra.resources.R
import com.competra.ui.BaseAction
import com.competra.ui.millisUntilNextCountdownTick
import com.competra.ui.CompetitionStartTimeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.Calendar
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.competra.domain.models.orienteering.KnownParticipant
import com.competra.domain.models.orienteering.ParticipantHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            editingParticipant = state.editingParticipant,
            lateEntry = state.lateEntry,
            protocolTeams = remember(state.participantGroupWithParticipants) {
                protocolCommandNames(state.participantGroupWithParticipants.flatMap { it.participants }.map { it.commandName })
            },
            // Кто уже в протоколе, не подсказываем — диалог открыт, пока вносят участников подряд.
            participantHistory = remember(state.participantHistory, state.participantGroupWithParticipants) {
                ParticipantHistory.excludeProtocol(
                    state.participantHistory,
                    state.participantGroupWithParticipants.flatMap { it.participants }
                )
            }
        )
    }

    state.deletingParticipant?.let { participant ->
        DeleteParticipantDialog(
            participant = participant,
            onDismiss = { userAction.invoke(ParticipantListAction.HideDeleteParticipantDialog) },
            onConfirm = { userAction.invoke(ParticipantListAction.DeleteParticipant(participant)) }
        )
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { userAction.invoke(ParticipantListAction.DismissError) },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { userAction.invoke(ParticipantListAction.DismissError) }) {
                    Text(text = "OK")
                }
            }
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
    // После старта участников не удаляют — вместо удаления доступна отметка «Не стартовал»
    val isDeletionLocked = state.competition?.competition?.status?.isParticipantDeletionLocked == true
    val onDnsToggle: (OrienteeringParticipant, Boolean) -> Unit = { participant, isDns ->
        userAction.invoke(ParticipantListAction.SetParticipantDns(participant, isDns))
    }
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isCompetitionRunning, state.participantGroupWithParticipants) {
        if (isCompetitionRunning) {
            // Тик на границах секунд до ближайшего старта — отсчёт совпадает с сигналами
            while (true) {
                val now = System.currentTimeMillis()
                currentTimeMs = now
                val nextStart = state.participantGroupWithParticipants
                    .flatMap { it.participants }
                    .filter { it.startTime > now }
                    .minOfOrNull { it.startTime }
                delay(millisUntilNextCountdownTick(now, nextStart))
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
                                isDeletionLocked = isDeletionLocked,
                                resultStatuses = state.resultStatuses,
                                onDnsToggle = onDnsToggle,
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
                                isDeletionLocked = isDeletionLocked,
                                resultStatuses = state.resultStatuses,
                                onDnsToggle = onDnsToggle,
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
    editingParticipant: OrienteeringParticipant?,
    /** Варианты стартового времени дозаявки после жеребьёвки (null — жеребьёвки не было). */
    lateEntry: LateEntryPreview? = null,
    /** Подписи команд из протокола соревнования — для подсказок ([protocolCommandNames]). */
    protocolTeams: List<String> = emptyList(),
    /** Люди из прошлых соревнований организатора, которых ещё нет в протоколе. */
    participantHistory: List<KnownParticipant> = emptyList()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    DSBottomDialog(
        sheetState = sheetState,
        sheetContent = {
            CreateParticipantDialogContent(
                userAction = userAction,
                group = group,
                groupName = groupName,
                editingParticipant = editingParticipant,
                lateEntry = lateEntry,
                protocolTeams = protocolTeams,
                participantHistory = participantHistory
            )
        },
        onDismiss = { userAction.invoke(ParticipantListAction.HideCreateParticipantDialog) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateParticipantDialogContent(
    userAction: (BaseAction) -> Unit,
    group: Int,
    groupName: String,
    editingParticipant: OrienteeringParticipant?,
    lateEntry: LateEntryPreview? = null,
    protocolTeams: List<String> = emptyList(),
    participantHistory: List<KnownParticipant> = emptyList()
) {
    var placement by remember { mutableStateOf(LateEntryPlacement.FREE_SLOT) }
    var firstName by remember(editingParticipant) { mutableStateOf(editingParticipant?.firstName ?: "") }
    var secondName by remember(editingParticipant) { mutableStateOf(editingParticipant?.lastName ?: "") }
    var commandName by remember(editingParticipant) { mutableStateOf(editingParticipant?.commandName ?: "") }
    var startTimeInput by remember(editingParticipant) {
        mutableStateOf(
            if (editingParticipant != null && isValidTimestamp(editingParticipant.startTime))
                formatStartTime(editingParticipant.startTime)
            else ""
        )
    }
    var startTimeError by remember { mutableStateOf(false) }
    // Подсказка, из которой заполнены поля; сбрасывается, если имя потом поправили вручную.
    var pickedFromHistory by remember { mutableStateOf<KnownParticipant?>(null) }
    val isPickedNameIntact = pickedFromHistory?.let { it.firstName == firstName && it.lastName == secondName } == true

    // Массовый выбор из прошлых соревнований — полноэкранный диалог поверх формы одного участника.
    var isBulkMode by remember { mutableStateOf(false) }
    if (isBulkMode) {
        HistoryBulkSelectDialog(
            groupName = groupName,
            history = participantHistory,
            lateEntry = lateEntry,
            onBack = { isBulkMode = false },
            onAdd = { selected, bulkPlacement ->
                userAction.invoke(ParticipantListAction.AddParticipantsFromHistory(group, selected, bulkPlacement))
            }
        )
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .padding(Dimens.SIZE_BASE.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (editingParticipant == null) "Новый участник" else "Редактирование",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // Сброс всех полей разом — например, если выбрали не ту подсказку.
            if (editingParticipant == null) {
                TextButton(
                    enabled = secondName.isNotEmpty() || firstName.isNotEmpty() ||
                        commandName.isNotEmpty() || startTimeInput.isNotEmpty(),
                    onClick = {
                        secondName = ""
                        firstName = ""
                        commandName = ""
                        startTimeInput = ""
                        startTimeError = false
                        pickedFromHistory = null
                        focusRequester.requestFocus()
                    }
                ) {
                    Text("Очистить")
                }
            }
        }

        Text(
            text = "Группа: $groupName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = Dimens.SIZE_HALF.dp)
        )

        if (editingParticipant == null && participantHistory.isNotEmpty()) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { isBulkMode = true }
            ) {
                Text("Выбрать из прошлых соревнований")
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

        DSTextInput(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label = { Text(text = "Фамилия") },
            text = secondName,
            onValueChanged = { secondName = it },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Имя") },
            text = firstName,
            onValueChanged = { firstName = it },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        // Подсказки из прошлых соревнований организатора: ищем по началу фамилии и имени сразу
        // в обоих полях; тап заполняет фамилию, имя и команду.
        if (editingParticipant == null && !isPickedNameIntact) {
            val historySuggestions = remember(participantHistory, firstName, secondName) {
                ParticipantHistory.search(participantHistory, "$secondName $firstName")
            }
            if (historySuggestions.isNotEmpty()) {
                ParticipantHistorySuggestions(
                    suggestions = historySuggestions,
                    onPick = { known ->
                        firstName = known.firstName
                        secondName = known.lastName
                        commandName = known.commandName.take(COMMAND_NAME_MAX_LENGTH)
                        pickedFromHistory = known
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

        // Команда — подпись в протоколе, по ней считается командный зачёт. Подсказки из протокола
        // помогают писать одну команду одинаково.
        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Команда (необязательно)") },
            text = commandName,
            onValueChanged = { commandName = it.take(COMMAND_NAME_MAX_LENGTH) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        val teamSuggestions = remember(protocolTeams, commandName) {
            protocolCommandSuggestions(protocolTeams, commandName, limit = 4)
        }
        if (teamSuggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_QUARTER.dp)) {
                teamSuggestions.forEach { suggestion ->
                    SuggestionChip(onClick = { commandName = suggestion }, label = { Text(suggestion) })
                }
            }
        }

        // Дозаявка после жеребьёвки: организатор выбирает, куда поставить участника в протоколе.
        if (editingParticipant == null && lateEntry != null) {
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            Text(
                text = "Стартовое время",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_QUARTER.dp)) {
                FilterChip(
                    selected = placement == LateEntryPlacement.FREE_SLOT,
                    onClick = { placement = LateEntryPlacement.FREE_SLOT },
                    label = { Text("Свободная минута · ${formatStartTime(lateEntry.freeSlotTime)}") }
                )
                FilterChip(
                    selected = placement == LateEntryPlacement.END,
                    onClick = { placement = LateEntryPlacement.END },
                    label = { Text("В конец · ${formatStartTime(lateEntry.endTime)}") }
                )
                FilterChip(
                    selected = placement == LateEntryPlacement.MANUAL,
                    onClick = { placement = LateEntryPlacement.MANUAL },
                    label = { Text("Вручную") }
                )
            }
            if (placement == LateEntryPlacement.MANUAL) {
                DSTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Стартовое время (ЧЧ:ММ или ЧЧ:ММ:СС)") },
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
        }

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
                        val isManual = lateEntry != null && placement == LateEntryPlacement.MANUAL
                        val manualStartTime = if (isManual) {
                            parseTimeInput(startTimeInput, lateEntry.freeSlotTime) ?: run {
                                startTimeError = true
                                return@Button
                            }
                        } else null
                        userAction.invoke(
                            ParticipantListAction.CreateNewParticipant(
                                group = group,
                                firstName = firstName,
                                secondName = secondName,
                                commandName = commandName,
                                placement = placement,
                                manualStartTime = manualStartTime,
                                fromHistory = isPickedNameIntact
                            )
                        )
                        // Команду не сбрасываем: участников одной команды обычно вносят подряд.
                        firstName = ""
                        secondName = ""
                        pickedFromHistory = null
                        startTimeInput = ""
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
                                    commandName = normalizeCommandName(commandName).orEmpty(),
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

/**
 * Список подсказок из истории участников организатора под полями имени.
 *
 * @param suggestions Найденные люди.
 * @param onPick Выбор подсказки.
 */
@Composable
private fun ParticipantHistorySuggestions(
    suggestions: List<KnownParticipant>,
    onPick: (KnownParticipant) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.SIZE_QUARTER.dp),
        shape = RoundedCornerShape(Dimens.SIZE_HALF.dp),
        tonalElevation = 2.dp
    ) {
        Column {
            Text(
                text = "Участвовали раньше",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Dimens.SIZE_BASE.dp,
                    end = Dimens.SIZE_BASE.dp,
                    top = Dimens.SIZE_HALF.dp
                )
            )
            suggestions.forEach { known ->
                ListItem(
                    modifier = Modifier.clickable { onPick(known) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("${known.lastName} ${known.firstName}") },
                    supportingContent = {
                        Text(
                            text = historySupportingText(known),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

/**
 * Массовый выбор людей из прошлых соревнований организатора на весь экран: поиск, галочки и
 * «Добавить в группу …». Все отмеченные добавляются в группу, для которой открыт диалог.
 * Свайпом не закрывается — только «Назад» (и системной кнопкой «назад»).
 *
 * @param groupName Название группы.
 * @param history Люди из истории, которых ещё нет в протоколе.
 * @param lateEntry Варианты времени дозаявки после жеребьёвки (null — жеребьёвки не было).
 * @param onBack Вернуться к форме одного участника.
 * @param onAdd Добавить отмеченных (в порядке истории) с выбранным способом дозаявки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryBulkSelectDialog(
    groupName: String,
    history: List<KnownParticipant>,
    lateEntry: LateEntryPreview?,
    onBack: () -> Unit,
    onAdd: (List<KnownParticipant>, LateEntryPlacement) -> Unit
) {
    var query by remember { mutableStateOf("") }
    // Ключи, а не объекты: история перестраивается, когда приходит серверная часть.
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var placement by remember { mutableStateOf(LateEntryPlacement.FREE_SLOT) }
    // Сначала те, кто в прошлый раз бежал в этой же группе.
    val ordered = remember(history, groupName) { ParticipantHistory.prioritizeGroup(history, groupName) }
    val filtered = remember(ordered, query) { ParticipantHistory.filter(ordered, query) }
    val selected = remember(history, selectedKeys) {
        history.filter { ParticipantHistory.keyOf(it) in selectedKeys }
    }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .padding(Dimens.SIZE_BASE.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Из прошлых соревнований",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onBack) {
                        Text("Назад")
                    }
                }

                Text(
                    text = "Добавление участников в группу: $groupName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = Dimens.SIZE_HALF.dp)
                )

                DSTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Поиск по фамилии или имени") },
                    text = query,
                    onValueChanged = { query = it },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                if (filtered.isEmpty()) {
                    Text(
                        text = "Никого не найдено",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = Dimens.SIZE_BASE.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filtered, key = { ParticipantHistory.keyOf(it) }) { known ->
                            val key = ParticipantHistory.keyOf(known)
                            val isChecked = key in selectedKeys
                            val toggle = {
                                selectedKeys = if (isChecked) selectedKeys - key else selectedKeys + key
                            }
                            ListItem(
                                modifier = Modifier.clickable { toggle() },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = {
                                    Checkbox(checked = isChecked, onCheckedChange = { toggle() })
                                },
                                headlineContent = { Text("${known.lastName} ${known.firstName}") },
                                supportingContent = {
                                    Text(
                                        text = historySupportingText(known),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }

                // Дозаявка после жеребьёвки: всех отмеченных ставим одинаково, по очереди.
                if (lateEntry != null) {
                    Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                    Text(
                        text = "Стартовое время",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_QUARTER.dp)) {
                        FilterChip(
                            selected = placement == LateEntryPlacement.FREE_SLOT,
                            onClick = { placement = LateEntryPlacement.FREE_SLOT },
                            label = { Text("Свободные минуты") }
                        )
                        FilterChip(
                            selected = placement == LateEntryPlacement.END,
                            onClick = { placement = LateEntryPlacement.END },
                            label = { Text("В конец протокола") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                // Отмеченные могут быть скрыты поиском — сколько их, видно здесь.
                Text(
                    text = if (selected.isEmpty()) "Отметьте участников" else "Отмечено: ${selected.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
                    enabled = selected.isNotEmpty(),
                    onClick = { onAdd(selected, placement) }
                ) {
                    Text(
                        text = "Добавить в группу $groupName",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Вторая строка подсказки: «Команда · Группа · 12.10.2026». */
private fun historySupportingText(known: KnownParticipant): String =
    listOfNotNull(
        known.commandName.takeIf { it.isNotBlank() },
        known.lastGroupName.takeIf { it.isNotBlank() },
        formatHistoryDate(known.lastCompetitionDate)
    ).joinToString(" · ")

/** Дата прошлого соревнования для подсказки: «12.10.2026». */
private fun formatHistoryDate(timestamp: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))

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
                    text = "${participant.firstName} ${participant.lastName}" +
                        participant.startNumber.takeIf { it.isNotBlank() }?.let { " (№$it)" }.orEmpty(),
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

/** Высота зоны плавающих кнопок внизу экрана: сама кнопка (56dp) + отступы вокруг неё. */
private val FAB_AREA_HEIGHT = 88.dp

@Composable
fun ParticipantList(
    participants: List<OrienteeringParticipant>,
    showGroupName: Boolean = false,
    currentTimeMs: Long = 0L,
    isCompetitionRunning: Boolean = false,
    isDeletionLocked: Boolean = false,
    resultStatuses: Map<String, ResultStatus> = emptyMap(),
    onDnsToggle: (OrienteeringParticipant, Boolean) -> Unit = { _, _ -> },
    onEditClick: (OrienteeringParticipant) -> Unit,
    onDeleteClick: (OrienteeringParticipant) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Снизу — запас под плавающие кнопки «Сохранить»/«+», иначе они перекрывают последнего участника.
        contentPadding = PaddingValues(
            start = Dimens.SIZE_BASE.dp,
            top = Dimens.SIZE_BASE.dp,
            end = Dimens.SIZE_BASE.dp,
            bottom = FAB_AREA_HEIGHT
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
    ) {
        itemsIndexed(participants) { _, participant ->
            ParticipantCard(
                participant = participant,
                showGroupName = showGroupName,
                currentTimeMs = currentTimeMs,
                isCompetitionRunning = isCompetitionRunning,
                isDeletionLocked = isDeletionLocked,
                resultStatus = resultStatuses[participant.id],
                onDnsToggle = { isDns -> onDnsToggle(participant, isDns) },
                onEditClick = { onEditClick(participant) },
                onDeleteClick = { onDeleteClick(participant) }
            )
        }
    }
}

@Composable
fun ParticipantCard(
    participant: OrienteeringParticipant,
    showGroupName: Boolean = false,
    currentTimeMs: Long = 0L,
    isCompetitionRunning: Boolean = false,
    isDeletionLocked: Boolean = false,
    resultStatus: ResultStatus? = null,
    onDnsToggle: (Boolean) -> Unit = {},
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isDns = resultStatus == ResultStatus.DNS
    val hasValidStart = isValidTimestamp(participant.startTime)
    val startTimeText = if (hasValidStart) formatStartTime(participant.startTime) else "—"
    val hasStarted = hasValidStart && isCompetitionRunning && participant.startTime <= currentTimeMs
    val secondsLeft = if (hasValidStart && isCompetitionRunning && !hasStarted)
        ((participant.startTime - currentTimeMs + 999) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    else -1
    val isCountingDown = secondsLeft in 1..5

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
                    // Без номера (до жеребьёвки) — прочерк, а не порядковый номер строки,
                    // чтобы не путать его с настоящим стартовым номером / номером чипа
                    text = participant.startNumber.ifEmpty { "—" },
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
                    isDns -> Text(
                        text = "Не стартовал",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
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

            if (!isDeletionLocked) {
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
            } else if (isDns || resultStatus.canBeMarkedDns) {
                // Участник со стартом/финишем уже стартовал — для него отметка недоступна
                Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))

                DnsToggleButton(isDns = isDns, onClick = { onDnsToggle(!isDns) })
            }
        }
    }
}

/**
 * Переключатель отметки «Не стартовал» (н/с). Заменяет кнопку удаления после старта соревнования.
 *
 * @param isDns Участник уже отмечен как «Не стартовал» — кнопка залита и снимает отметку.
 */
@Composable
private fun DnsToggleButton(isDns: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isDns) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        contentColor = if (isDns) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .height(32.dp)
            .semantics {
                contentDescription = if (isDns) "Снять отметку «Не стартовал»" else "Отметить «Не стартовал»"
            }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "н/с",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
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
