package com.competra.center.presentation.read_card

import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.competra.center.data.read_card.OrientReadCardAction
import com.competra.center.data.read_card.finishPunch
import com.competra.center.presentation.common.GroupsWithoutDistanceBanner
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.ByChoiceMode
import com.competra.domain.models.orienteering.ControlPoint
import com.competra.domain.models.orienteering.ControlPointRole
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.SplitTime
import com.competra.domain.models.orienteering.controlPointDistanceMeters
import com.competra.domain.models.orienteering.paceMinPerKm
import com.competra.domain.models.orienteering.ranksByScore
import com.competra.resources.R
import com.competra.utils.DateTimeFormat
import com.competra.utils.orienteering.toPace
import com.competra.utils.orienteering.toRaceTime
import com.competra.utils.orienteering.toSplitTime
import com.competra.nfchelper.nfccard.Config
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран для отображения данных, считанных с чипа участника соревнований по ориентированию.
 *
 * Улучшенный дизайн с использованием карточек, иконок и логического разделения блоков информации.
 *
 * @param viewModel ViewModel для управления состоянием экрана.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrientReadCardScreen(viewModel: OrientReadCardViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Результат ждёт «Сохранить» (DSQ или правка отметок): «Назад» по привычке закрыл бы экран
    // без сохранения, а организатор решил бы, что чип уже записан. Предупреждаем.
    BackHandler(enabled = state.isPendingSave && !state.isCompetitionFinished) {
        viewModel.onAction(OrientReadCardAction.BackPressedWithPendingSave)
    }
    if (state.isLeaveConfirmShown) {
        LeaveWithPendingSaveDialog(
            onSaveAndLeave = { viewModel.onAction(OrientReadCardAction.SaveAndLeave) },
            onLeave = { viewModel.onAction(OrientReadCardAction.LeaveWithoutSaving) },
            onDismiss = { viewModel.onAction(OrientReadCardAction.DismissLeaveConfirm) }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GroupsWithoutDistanceBanner(
                titles = state.groupsWithoutDistance,
                message = "Чипы участников этих групп не будут посчитаны, пока дистанция не назначена.",
                modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp, vertical = 8.dp)
            )
            state.scanNotice?.let { notice ->
                ScanNoticeBanner(
                    notice = notice,
                    modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp, vertical = 8.dp)
                )
            }
            if (state.isFinishRegistration && state.unassignedReads.isNotEmpty()) {
                UnassignedReadsBanner(
                    count = state.unassignedReads.size,
                    onOpen = { viewModel.onAction(OrientReadCardAction.ShowUnassignedReads) },
                    modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp, vertical = 8.dp)
                )
            }
            if (state.unsyncedStationCps.isNotEmpty()) {
                ScanNoticeBanner(
                    notice = "Время станции не синхронизировано: " +
                        state.unsyncedStationCps.joinToString { cp ->
                            when (cp) {
                                Config.START_STATION -> "старт"
                                Config.FINISH_STATION -> "финиш"
                                else -> "КП $cp"
                            }
                        } +
                        ". Время отметок, сплиты и результат по ним неверны — синхронизируйте время " +
                        "станции («Центр» → «Станции и чипы» → «Станция») и проверьте результат.",
                    modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp, vertical = 8.dp)
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                if (state.participant == null) {
                    EmptyReadCardView(isReadOnly = state.isCompetitionFinished)
                } else {
                    ReadCardContent(
                        participant = state.participant!!,
                        result = state.participantResult,
                        rawSplits = state.rawSplits,
                        groupRank = state.groupRank,
                        groupTotalFinished = state.groupTotalFinished,
                        expectedCpOrder = state.expectedCpNumbers,
                        expectedControlPoints = state.expectedControlPoints,
                        startControlPoint = state.startControlPoint,
                        startPoint = state.startPoint,
                        competitionDirection = state.competitionDirection,
                        byChoiceMode = state.byChoiceMode,
                        isPendingSave = state.isPendingSave,
                        isReadOnly = state.isCompetitionFinished,
                        statusMessage = state.statusMessage,
                        isFinishRegistration = state.isFinishRegistration,
                        canCredit = state.finishPunch != null,
                        canCreditByReadTime = state.finishPunch == null &&
                            state.finishControlPoint != null &&
                            state.participantResult?.readAt != null &&
                            (state.participantResult?.startTime ?: 0L) > 0L,
                        onSetStatus = { finished -> viewModel.onAction(OrientReadCardAction.SetManualStatus(finished)) },
                        onCreditByReadTime = { viewModel.onAction(OrientReadCardAction.CreditFinishByReadTime) },
                        onEditParticipant = { viewModel.onAction(OrientReadCardAction.ShowEditParticipant) },
                        onUnlinkChip = { viewModel.onAction(OrientReadCardAction.ShowUnlinkChip) },
                        onEditSplit = { index -> viewModel.onAction(OrientReadCardAction.EditSplitClicked(index)) },
                        onCreditCp = { cpNumber, distanceOrdinal ->
                            viewModel.onAction(OrientReadCardAction.CreditMissedCp(cpNumber, distanceOrdinal))
                        },
                        onSaveResult = { viewModel.onAction(OrientReadCardAction.SaveResult) }
                    )
                }
            }
        }

        val editingIndex = state.editingSplitIndex
        val rawSplits = state.rawSplits
        if (editingIndex != null && rawSplits != null && editingIndex in rawSplits.indices) {
            EditSplitBottomSheet(
                split = rawSplits[editingIndex],
                sheetState = sheetState,
                onDismiss = { viewModel.onAction(OrientReadCardAction.DismissEditSplit) },
                onSave = { newTimestamp ->
                    viewModel.onAction(OrientReadCardAction.SaveSplitEdit(editingIndex, newTimestamp))
                },
                onDelete = {
                    viewModel.onAction(OrientReadCardAction.DeleteSplit(editingIndex))
                }
            )
        }

        if (state.isUnassignedListShown) {
            UnassignedReadsSheet(
                reads = state.unassignedReads,
                startStationCode = state.defaultStartStationCode,
                finishStationCode = state.defaultFinishStationCode,
                matchingGroups = state.unassignedMatchingGroups,
                onOpen = { chip -> viewModel.onAction(OrientReadCardAction.OpenAssignRead(chip)) },
                onDelete = { chip -> viewModel.onAction(OrientReadCardAction.DeleteUnassignedRead(chip)) },
                onDismiss = { viewModel.onAction(OrientReadCardAction.HideUnassignedReads) }
            )
        }

        state.assigningRead?.let { read ->
            AssignReadSheet(
                read = read,
                groups = state.assignGroups,
                candidates = state.assignCandidates,
                history = state.participantHistory,
                createdGroupId = state.createdGroupId,
                matchingGroupIds = state.assignMatchingGroupIds,
                startStationCode = state.defaultStartStationCode,
                finishStationCode = state.defaultFinishStationCode,
                onCreate = { lastName, firstName, commandName, groupId, fromHistory, isOutOfCompetition ->
                    viewModel.onAction(
                        OrientReadCardAction.CreateParticipantFromRead(
                            lastName, firstName, commandName, groupId, fromHistory, isOutOfCompetition
                        )
                    )
                },
                onAssign = { id -> viewModel.onAction(OrientReadCardAction.AssignReadToParticipant(id)) },
                onCreateGroup = { title -> viewModel.onAction(OrientReadCardAction.CreateGroupForRead(title)) },
                onDismiss = { viewModel.onAction(OrientReadCardAction.DismissAssignRead) }
            )
        }

        val participant = state.participant
        if (state.isEditParticipantShown && participant != null) {
            EditParticipantSheet(
                participant = participant,
                groups = state.assignGroups,
                createdGroupId = state.createdGroupId,
                matchingGroupIds = state.assignMatchingGroupIds,
                onSave = { lastName, firstName, commandName, groupId, isOutOfCompetition ->
                    viewModel.onAction(
                        OrientReadCardAction.UpdateParticipantFromRead(lastName, firstName, commandName, groupId, isOutOfCompetition)
                    )
                },
                onCreateGroup = { title -> viewModel.onAction(OrientReadCardAction.CreateGroupForRead(title)) },
                onDismiss = { viewModel.onAction(OrientReadCardAction.DismissEditParticipant) }
            )
        }
        if (state.isUnlinkChipShown && participant != null) {
            UnlinkChipDialog(
                participant = participant,
                onUnlink = { delete -> viewModel.onAction(OrientReadCardAction.UnlinkChip(delete)) },
                onDismiss = { viewModel.onAction(OrientReadCardAction.DismissUnlinkChip) }
            )
        }
    }
}

/**
 * Подсказка о скане, по которому результат не посчитан (см. [com.competra.center.data.read_card.OrientReadCardState.scanNotice]).
 */
@Composable
private fun ScanNoticeBanner(notice: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            text = notice,
            modifier = Modifier.padding(Dimens.SIZE_BASE.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

/**
 * Баннер режима «только просмотр»: соревнование завершено, чип можно отсканировать,
 * но данные не сохраняются и не влияют на результаты.
 */
@Composable
private fun ReadOnlyBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SIZE_BASE.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.play_arrow_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))
            Column {
                Text(
                    text = "Соревнование завершено",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Данные показаны только для просмотра и не сохраняются в результаты.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * Баннер с причиной дисквалификации ([CheckResult.message]) — например, отсутствие отметки на
 * стартовой станции или не заданное в настройках дистанции стартовое КП.
 */
@Composable
private fun DsqReasonBanner(reason: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SIZE_BASE.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Дисквалификация (DSQ)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * Предупреждение при выходе с экрана считывания, когда результат ещё не сохранён.
 */
@Composable
private fun LeaveWithPendingSaveDialog(
    onSaveAndLeave: () -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Результат не сохранён") },
        text = { Text("Если выйти сейчас, результат и все несохранённые изменения будут потеряны.") },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onSaveAndLeave) { Text("Сохранить и выйти", fontWeight = FontWeight.Bold) }
                TextButton(onClick = onLeave) {
                    Text("Выйти без сохранения", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Остаться") }
            }
        }
    )
}

/**
 * Основное содержимое экрана при наличии данных.
 */
@Composable
private fun ReadCardContent(
    participant: OrienteeringParticipant,
    result: OrienteeringResult?,
    rawSplits: List<SplitTime>? = null,
    groupRank: Int? = null,
    groupTotalFinished: Int = 0,
    expectedCpOrder: List<Int> = emptyList(),
    expectedControlPoints: List<ControlPoint> = emptyList(),
    startControlPoint: Int? = null,
    startPoint: ControlPoint? = null,
    competitionDirection: OrienteeringDirection = OrienteeringDirection.FORWARD,
    byChoiceMode: ByChoiceMode = ByChoiceMode.DEFAULT,
    isPendingSave: Boolean = false,
    isReadOnly: Boolean = false,
    statusMessage: String? = null,
    isFinishRegistration: Boolean = false,
    canCredit: Boolean = false,
    canCreditByReadTime: Boolean = false,
    onSetStatus: (finished: Boolean) -> Unit = {},
    onCreditByReadTime: () -> Unit = {},
    /** «Изменить участника» (режим «Регистрация на финише»). */
    onEditParticipant: () -> Unit = {},
    /** «Отвязать чип» (режим «Регистрация на финише»). */
    onUnlinkChip: () -> Unit = {},
    onEditSplit: (index: Int) -> Unit = {},
    onCreditCp: (cpNumber: Int, distanceOrdinal: Int) -> Unit = { _, _ -> },
    onSaveResult: () -> Unit = {},
) {
    val isByChoice = competitionDirection == OrienteeringDirection.BY_CHOICE
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.SIZE_BASE.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_BASE.dp)
    ) {
        // Заголовок экрана
        item {
            Text(
                text = "Результат участника",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Соревнование завершено — данные показаны только для просмотра
        if (isReadOnly) {
            item {
                ReadOnlyBanner()
            }
        }

        // Карточка участника
        item {
            ParticipantInfoCard(participant)
        }

        // Режим «Регистрация на финише»: участника внесли прямо на финише — ошибку (не та группа,
        // не тот человек) исправляют здесь же.
        if (isFinishRegistration && !isReadOnly) {
            item {
                FinishParticipantActions(onEdit = onEditParticipant, onUnlink = onUnlinkChip)
            }
        }

        // Причина статуса результата (в первую очередь — почему DSQ): без неё нулевое время
        // и дисквалификация выглядят необъяснимым сбоем, а не следствием конкретной причины.
        if (result?.status == ResultStatus.DSQ && !statusMessage.isNullOrEmpty()) {
            item {
                DsqReasonBanner(statusMessage)
            }
        }

        // Карточка итогового времени
        if (result != null) {
            item {
                RaceSummaryCard(participant, result, groupRank, groupTotalFinished, competitionDirection, byChoiceMode)
            }

            // Секция сплитов
            val displaySplits = rawSplits ?: result.splits
            if (!displaySplits.isNullOrEmpty()) {
                // Последовательность КП дистанции для сверки порядка отметок.
                // Для BY_CHOICE порядок посещения КП не регламентирован — карточка неактуальна.
                if (expectedCpOrder.isNotEmpty() && !isByChoice) {
                    item {
                        DistanceCpSequenceCard(expectedCpOrder)
                    }
                }

                item {
                    Text(
                        text = "Сплиты по пунктам",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                item {
                    SplitsCard(
                        participant = participant,
                        splits = displaySplits,
                        expectedCpOrder = expectedCpOrder,
                        expectedControlPoints = expectedControlPoints,
                        startControlPoint = startControlPoint,
                        startPoint = startPoint,
                        competitionDirection = competitionDirection,
                        byChoiceMode = byChoiceMode,
                        onEditSplit = if (isReadOnly) null else onEditSplit,
                        onCreditCp = if (isPendingSave) onCreditCp else null,
                    )
                }
            }
        }

        // Режим «Регистрация на финише»: правильность прохождения оценивает организатор.
        if (isFinishRegistration && result != null && !isReadOnly) {
            item {
                ManualStatusPanel(
                    result = result,
                    canCredit = canCredit,
                    canCreditByReadTime = canCreditByReadTime,
                    onSetStatus = onSetStatus,
                    onCreditByReadTime = onCreditByReadTime
                )
            }
        }

        if (isPendingSave) {
            item {
                Button(
                    onClick = onSaveResult,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
                ) {
                    Text("Сохранить результат", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Карточка с последовательностью КП дистанции участника.
 *
 * Каждый элемент — «порядковый_номер-номер_КП» (например «1-36, 2-37, 3-38»),
 * чтобы организатор мог сверить правильность порядка отметок.
 */
@Composable
private fun DistanceCpSequenceCard(expectedCpOrder: List<Int>) {
    val sequence = remember(expectedCpOrder) {
        expectedCpOrder.mapIndexed { index, cp -> "${index + 1}-$cp" }.joinToString(", ")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(Dimens.SIZE_BASE.dp)) {
            Text(
                text = "Последовательность КП дистанции",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sequence,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Карточка с информацией об участнике.
 */
@Composable
internal fun ParticipantInfoCard(participant: OrienteeringParticipant) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(Dimens.SIZE_BASE.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар/Иконка профиля
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_account_circle_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))

            Column {
                Text(
                    text = "${participant.lastName} ${participant.firstName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = participant.groupName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                if (participant.commandName.isNotEmpty()) {
                    Text(
                        text = participant.commandName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Карточка с общим временем гонки.
 */
@Composable
internal fun RaceSummaryCard(
    participant: OrienteeringParticipant,
    result: OrienteeringResult,
    groupRank: Int? = null,
    groupTotalFinished: Int = 0,
    competitionDirection: OrienteeringDirection = OrienteeringDirection.FORWARD,
    byChoiceMode: ByChoiceMode = ByChoiceMode.DEFAULT,
) {
    // Баллы — только в score-О; «по выбору» с минимумом КП, как и остальные форматы, — по времени.
    val isByChoice = ranksByScore(competitionDirection, byChoiceMode)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(Dimens.SIZE_BASE.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                InfoColumn(
                    label = "Старт",
                    value = DateTimeFormat.transformLongToTime(participant.startTime),
                    modifier = Modifier.weight(1f)
                )
                Column(
                    modifier = Modifier.weight(1.6f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isByChoice) "БАЛЛЫ" else "ОБЩЕЕ ВРЕМЯ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isByChoice) {
                            (result.totalScore ?: 0).toString()
                        } else {
                            result.totalTime?.toRaceTime() ?: "00:00:00"
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
                InfoColumn(
                    label = "Финиш",
                    value = DateTimeFormat.transformLongToTime(result.finishTime),
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                )
            }

            if (isByChoice && result.scorePenalty > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Штраф за опоздание: −${result.scorePenalty} очков. Итоговый результат: ${result.totalScore ?: 0} очков",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (groupRank != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Dimens.SIZE_BASE.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "МЕСТО В ГРУППЕ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (groupTotalFinished > 0) "$groupRank из $groupTotalFinished" else "$groupRank",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

internal sealed class SplitDisplayItem {
    /**
     * Фактическая отметка с чипа.
     *
     * @param chipIndex Индекс отметки в хронологическом порядке чипа (фактический порядок взятия).
     * @param isExtra true — отметка не учитывается: КП нет в дистанции участника (лишняя отметка) или
     * это повтор КП дистанции сверх нужного ([isRepeat]).
     * @param distanceOrdinal Порядковый номер этого КП по дистанции (1-based), либо null, если КП
     * не из дистанции (лишний) или не был сопоставлен с ожидаемой позицией.
     * @param isOutOfOrder true — сопоставленная отметка взята не в том порядке (нарушает возрастание
     * позиций дистанции относительно остальных отметок).
     * @param isStartPunch true — это отметка на стартовой станции ([Distance.startControlPoint]).
     * Она не входит в проверяемую последовательность КП дистанции (используется только для расчёта
     * времени старта при BY_START_STATION), поэтому не должна подсвечиваться как лишняя/ошибочная.
     * @param isPreStart true — отметка сделана раньше времени старта (например, финишной станции в
     * стартовом городке). В результат не входит (см. punchesAfterStart) и не сопоставляется с КП
     * дистанции — иначе она «занимала» бы КП, а настоящая отметка выглядела бы лишней.
     * @param isRepeat true — неучтённая отметка ([isExtra]) КП, который в дистанции есть: участник
     * отметился на нём ещё раз (например, в «по выбору» повторно на уже взятом КП — очки за него
     * не начисляются второй раз).
     */
    data class Actual(
        val split: SplitTime,
        val chipIndex: Int,
        val isExtra: Boolean,
        val distanceOrdinal: Int? = null,
        val isOutOfOrder: Boolean = false,
        val isStartPunch: Boolean = false,
        val isPreStart: Boolean = false,
        val isRepeat: Boolean = false,
    ) : SplitDisplayItem()

    /** @param distanceOrdinal Порядковый номер пропущенного КП по дистанции (1-based). */
    data class Missed(val cpNumber: Int, val distanceOrdinal: Int) : SplitDisplayItem()
}

/**
 * Формирует список строк таблицы сплитов в **фактическом порядке отметок** (хронология чипа).
 *
 * Для каждой отметки вычисляется её позиция по дистанции ([SplitDisplayItem.Actual.distanceOrdinal]),
 * чтобы судья мог увидеть нарушение порядка: если значения «Дист.» не растут 1, 2, 3…, КП взяты не по
 * порядку. Пропущенные КП дистанции (не отмеченные на чипе) добавляются отдельными строками в конец.
 *
 * Позиции дистанции сопоставляются с отметками, взятыми по порядку (см. [alignInCourseOrder]), — у
 * пройденной дистанции это ровно засчитанные отметки. КП дистанции, взятый вне этого порядка (раньше
 * времени или повторно), подсвечивается как «взят не в том порядке»; «лишняя» — только отметка КП,
 * которого в дистанции нет.
 *
 * Для формата «по выбору» ([isByChoice]) порядок посещения КП не регламентирован, поэтому:
 * - строка «ПРОПУЩЕН» показывается только для обязательных КП ([requiredCpNumbers]) — остальные КП
 *   дистанции опциональны, участник вправе их не брать, это не ошибка;
 * - подсветка «взят не в том порядке» не вычисляется вовсе.
 */
internal fun buildSplitDisplayItems(
    rawSplits: List<SplitTime>,
    expectedCpOrder: List<Int>,
    requiredCpNumbers: Set<Int> = emptySet(),
    isByChoice: Boolean = false,
    startControlPoint: Int? = null,
    startTime: Long? = null,
): List<SplitDisplayItem> {
    val preStartIndices = rawSplits.indices.filter { startTime != null && rawSplits[it].timestamp < startTime }.toSet()
    if (expectedCpOrder.isEmpty()) {
        return rawSplits.mapIndexed { i, s ->
            val isPreStart = i in preStartIndices
            SplitDisplayItem.Actual(
                s, i, isExtra = false,
                isStartPunch = !isPreStart && s.controlPoint == startControlPoint,
                isPreStart = isPreStart,
            )
        }
    }
    // Отметки до старта в сопоставлении с дистанцией не участвуют (помечаем их «занятыми» заранее).
    val shownIndices = preStartIndices.toMutableSet()
    // Порядковый номер по дистанции для каждой сопоставленной фактической отметки.
    val distanceOrdinalByIndex = HashMap<Int, Int>()
    val missed = mutableListOf<SplitDisplayItem.Missed>()

    val outOfOrderIndices = HashSet<Int>()
    if (isByChoice) {
        // Порядок не регламентирован: каждой позиции дистанции (с учётом повторов одного КП) — самая
        // ранняя ещё не занятая отметка того же номера; отметки сверх нужного количества — лишние.
        expectedCpOrder.forEachIndexed { orderIndex, expectedCp ->
            val ordinal = orderIndex + 1
            val visitedIdx = rawSplits.indices.firstOrNull {
                it !in shownIndices && rawSplits[it].controlPoint == expectedCp
            }
            if (visitedIdx != null) {
                distanceOrdinalByIndex[visitedIdx] = ordinal
                shownIndices.add(visitedIdx)
            } else if (expectedCp in requiredCpNumbers) {
                missed.add(SplitDisplayItem.Missed(expectedCp, ordinal))
            }
        }
    } else {
        // 1. Отметки, взятые по порядку дистанции: наибольшее совпадение по порядку с самыми ранними
        //    отметками. Если дистанция пройдена, это ровно те отметки, по которым засчитан результат
        //    (checkControlPointOrderPro) и строится таблица сплитов группы (matchToCourse).
        val candidates = rawSplits.indices.filter { it !in shownIndices }
        alignInCourseOrder(expectedCpOrder, candidates.map { rawSplits[it].controlPoint })
            .forEach { (orderIndex, candidatePos) ->
                val idx = candidates[candidatePos]
                distanceOrdinalByIndex[idx] = orderIndex + 1
                shownIndices.add(idx)
            }
        // 2. Позиции дистанции, не взятые по порядку: если КП есть на чипе в другом месте — это он,
        //    взятый не в том порядке; иначе КП пропущен.
        val matchedOrdinals = distanceOrdinalByIndex.values.toSet()
        expectedCpOrder.forEachIndexed { orderIndex, expectedCp ->
            val ordinal = orderIndex + 1
            if (ordinal in matchedOrdinals) return@forEachIndexed
            val visitedIdx = rawSplits.indices.firstOrNull {
                it !in shownIndices && rawSplits[it].controlPoint == expectedCp
            }
            if (visitedIdx != null) {
                distanceOrdinalByIndex[visitedIdx] = ordinal
                shownIndices.add(visitedIdx)
                outOfOrderIndices.add(visitedIdx)
            } else {
                missed.add(SplitDisplayItem.Missed(expectedCp, ordinal))
            }
        }
        // 3. Оставшиеся отметки КП дистанции (повтор не к месту: участник вернулся и взял КП ещё раз
        //    по порядку) — тоже «не в том порядке», а не лишние: такой КП в дистанции есть.
        val courseCps = expectedCpOrder.toSet()
        rawSplits.indices
            .filter { it !in shownIndices && rawSplits[it].controlPoint in courseCps }
            .forEach { outOfOrderIndices.add(it) }
    }

    // Все отметки в фактическом (хронологическом) порядке + пропущенные КП в конце.
    // «Лишняя» — отметка, которой не нашлось места в дистанции: чужой КП либо повтор сверх нужного
    // (повтор — [SplitDisplayItem.Actual.isRepeat], у него своя подпись в легенде).
    val courseCpNumbers = expectedCpOrder.toSet()
    val actualItems = rawSplits.indices.map { i ->
        val ordinal = distanceOrdinalByIndex[i]
        val isPreStart = i in preStartIndices
        val isStartPunch = !isPreStart && ordinal == null && startControlPoint != null &&
            rawSplits[i].controlPoint == startControlPoint
        val isOutOfOrder = i in outOfOrderIndices && !isPreStart
        val isExtra = ordinal == null && !isOutOfOrder && !isStartPunch && !isPreStart
        SplitDisplayItem.Actual(
            split = rawSplits[i],
            chipIndex = i,
            isExtra = isExtra,
            isRepeat = isExtra && rawSplits[i].controlPoint in courseCpNumbers,
            distanceOrdinal = ordinal,
            isOutOfOrder = isOutOfOrder,
            isStartPunch = isStartPunch,
            isPreStart = isPreStart,
        )
    }

    return actualItems + missed
}

/**
 * Наибольшее совпадение отметок [punches] с порядком дистанции [course] (наибольшая общая
 * подпоследовательность). Из равных вариантов берутся самые ранние отметки — тот же выбор, что у
 * последовательного поиска при проверке дистанции, поэтому у пройденной дистанции сопоставление
 * совпадает с засчитанным результатом.
 *
 * @return пары (позиция в [course], позиция в [punches]) по возрастанию
 */
internal fun alignInCourseOrder(course: List<Int>, punches: List<Int>): List<Pair<Int, Int>> {
    val n = course.size
    val m = punches.size
    // lcs[i][j] — длина наибольшего совпадения course[i..] и punches[j..].
    val lcs = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            lcs[i][j] = if (course[i] == punches[j]) {
                lcs[i + 1][j + 1] + 1
            } else {
                maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
    }
    val result = mutableListOf<Pair<Int, Int>>()
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            course[i] == punches[j] && lcs[i][j] == lcs[i + 1][j + 1] + 1 -> {
                result.add(i to j)
                i++
                j++
            }
            // Пропускаем отметку, пока позиция дистанции ещё может совпасть с более поздней.
            lcs[i][j + 1] == lcs[i][j] -> j++
            else -> i++
        }
    }
    return result
}

/**
 * Карточка со списком сплитов.
 *
 * - Жёлтый фон: лишняя отметка — КП, которому не нашлось места в дистанции (чужой КП либо повтор
 *   сверх нужного количества для повторяющихся КП дистанции).
 * - Красный фон: КП дистанции, которого нет в чипе (пропущен).
 */
@Composable
internal fun SplitsCard(
    participant: OrienteeringParticipant,
    splits: List<SplitTime>,
    expectedCpOrder: List<Int> = emptyList(),
    expectedControlPoints: List<ControlPoint> = emptyList(),
    startControlPoint: Int? = null,
    startPoint: ControlPoint? = null,
    competitionDirection: OrienteeringDirection = OrienteeringDirection.FORWARD,
    byChoiceMode: ByChoiceMode = ByChoiceMode.DEFAULT,
    onEditSplit: ((index: Int) -> Unit)? = null,
    onCreditCp: ((cpNumber: Int, distanceOrdinal: Int) -> Unit)? = null,
) {
    val isByChoice = competitionDirection == OrienteeringDirection.BY_CHOICE
    // Колонка «Очки» — только в score-О; в «по выбору» с минимумом КП там помечаются обязательные КП.
    val showsScore = ranksByScore(competitionDirection, byChoiceMode)
    val requiredCpNumbers = remember(expectedControlPoints) {
        expectedControlPoints.filter { it.role == ControlPointRole.REQUIRED }.map { it.number }.toSet()
    }
    val scoreByNumber = remember(expectedControlPoints) {
        expectedControlPoints.associate { it.number to it.score }
    }
    val byChoiceMarker: (cpNumber: Int) -> String = { cpNumber ->
        when {
            showsScore -> (scoreByNumber[cpNumber] ?: 0).toString()
            cpNumber in requiredCpNumbers -> "Обяз."
            else -> ""
        }
    }
    val displayItems = remember(splits, expectedCpOrder, requiredCpNumbers, isByChoice, startControlPoint, participant.startTime) {
        buildSplitDisplayItems(splits, expectedCpOrder, requiredCpNumbers, isByChoice, startControlPoint, participant.startTime)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Факт", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.6f))
                Text(
                    text = when {
                        showsScore -> "Очки"
                        isByChoice -> ""
                        else -> "Дист"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(0.6f)
                )
                Text(text = "КП", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.8f))
                Text(text = "Сплит", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(text = "Время", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            displayItems.forEachIndexed { displayIndex, item ->
                // Предыдущий timestamp для расчёта сплита (ближайший Actual до текущего, кроме отметок до старта)
                val prevTimestamp = run {
                    for (i in displayIndex - 1 downTo 0) {
                        val prev = displayItems[i]
                        if (prev is SplitDisplayItem.Actual && !prev.isPreStart) return@run prev.split.timestamp
                    }
                    participant.startTime
                }

                when (item) {
                    is SplitDisplayItem.Actual -> {
                        val splitTimeStr = if (item.isPreStart) "—" else (item.split.timestamp - prevTimestamp).toSplitTime()
                        val totalTimeStr = if (!item.isExtra && !item.isPreStart) {
                            (item.split.timestamp - participant.startTime).toSplitTime()
                        } else "—"
                        // Темп на перегоне: нужны координаты и предыдущего, и текущего КП по порядку
                        // дистанции (distanceOrdinal — 1-based позиция, как в SplitsTableBuilder).
                        // Для первой позиции (ordinal 1) предыдущая точка — старт (startPoint); без
                        // его координат темп не считается.
                        // Для BY_CHOICE порядок посещения произвольный, «перегон» между соседними по
                        // списку дистанции КП не соответствует реальному пути участника — не считаем.
                        val pace = item.distanceOrdinal
                            ?.takeIf { !isByChoice }
                            ?.let { ordinal ->
                                val legMeters = controlPointDistanceMeters(
                                    if (ordinal == 1) startPoint else expectedControlPoints.getOrNull(ordinal - 2),
                                    expectedControlPoints.getOrNull(ordinal - 1),
                                )
                                paceMinPerKm((item.split.timestamp - prevTimestamp) / 1000, legMeters)
                            }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        item.isPreStart -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                                        item.isExtra -> Color(0xFFFFEB3B).copy(alpha = 0.25f)
                                        item.isOutOfOrder -> Color(0xFFFF9800).copy(alpha = 0.22f)
                                        item.isStartPunch -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val textColor = when {
                                item.isPreStart -> MaterialTheme.colorScheme.onSurfaceVariant
                                item.isExtra -> Color(0xFF9E8000)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text = (item.chipIndex + 1).toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.6f),
                                color = textColor
                            )
                            Text(
                                text = if (isByChoice) {
                                    byChoiceMarker(item.split.controlPoint)
                                } else {
                                    item.distanceOrdinal?.toString() ?: when {
                                        item.isPreStart -> "До ст."
                                        item.isStartPunch -> "Старт"
                                        else -> ""
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (item.isOutOfOrder) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(0.6f),
                                color = if (item.isOutOfOrder) Color(0xFFE65100) else textColor
                            )
                            Text(
                                text = item.split.controlPoint.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.8f),
                                color = textColor
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = splitTimeStr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = when {
                                        item.isPreStart -> MaterialTheme.colorScheme.onSurfaceVariant
                                        item.isExtra -> Color(0xFF9E8000)
                                        else -> MaterialTheme.colorScheme.secondary
                                    }
                                )
                                pace?.let {
                                    Text(
                                        text = "${it.toPace()} /км",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                text = totalTimeStr,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End,
                                color = textColor
                            )
                            if (onEditSplit != null) {
                                IconButton(
                                    onClick = { onEditSplit(item.chipIndex) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.edit),
                                        contentDescription = "Редактировать КП ${item.split.controlPoint}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    is SplitDisplayItem.Missed -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.6f),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (isByChoice) {
                                    byChoiceMarker(item.cpNumber)
                                } else {
                                    item.distanceOrdinal.toString()
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.6f),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = item.cpNumber.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.8f),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "ПРОПУЩЕН",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(2f),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            if (onCreditCp != null) {
                                TextButton(
                                    onClick = { onCreditCp(item.cpNumber, item.distanceOrdinal) }
                                ) {
                                    Text(
                                        text = "Засчитать",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (displayIndex < displayItems.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Легенда подсветки — показываем только встретившиеся в таблице маркеры.
            val hasOutOfOrder = displayItems.any { it is SplitDisplayItem.Actual && it.isOutOfOrder }
            val hasForeign = displayItems.any { it is SplitDisplayItem.Actual && it.isExtra && !it.isRepeat }
            val hasRepeat = displayItems.any { it is SplitDisplayItem.Actual && it.isRepeat }
            val hasExtra = hasForeign || hasRepeat
            val hasMissed = displayItems.any { it is SplitDisplayItem.Missed }
            val hasPreStart = displayItems.any { it is SplitDisplayItem.Actual && it.isPreStart }
            if (hasOutOfOrder || hasExtra || hasMissed || hasPreStart) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (hasOutOfOrder) {
                        SplitsLegendItem(Color(0xFFFF9800).copy(alpha = 0.5f), "Взят не в том порядке")
                    }
                    if (hasExtra) {
                        // Чужой КП и повтор КП дистанции подсвечиваются одинаково: оба не учитываются.
                        val label = when {
                            hasForeign && hasRepeat -> "Лишняя или повторная отметка (не учитывается)"
                            hasRepeat -> "Повторная отметка (не учитывается)"
                            else -> "Лишняя отметка (нет в дистанции)"
                        }
                        SplitsLegendItem(Color(0xFFFFEB3B).copy(alpha = 0.6f), label)
                    }
                    if (hasMissed) {
                        SplitsLegendItem(MaterialTheme.colorScheme.errorContainer, "Пропущенный КП")
                    }
                    if (hasPreStart) {
                        SplitsLegendItem(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            "Отметка до старта (не учитывается)"
                        )
                    }
                }
            }
        }
    }
}

/**
 * Строка легенды: цветной маркер + пояснение.
 */
@Composable
private fun SplitsLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun InfoColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/**
 * Заглушка при отсутствии данных (чип не считан).
 */
@Composable
private fun EmptyReadCardView(isReadOnly: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SIZE_DOUBLE.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.play_arrow_24px), // Используем как символ готовности/ожидания
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
        Text(
            text = "Ожидание чипа",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isReadOnly) {
                "Соревнование завершено. Можно приложить NFC-чип участника для просмотра данных — " +
                    "результаты при этом не сохраняются."
            } else {
                "Приложите NFC-чип участника к устройству для считывания результатов гонки."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// region Previews

private val previewParticipant = OrienteeringParticipant(
    id = "1",
    userId = "user_1",
    firstName = "Иван",
    lastName = "Петров",
    groupId = 1L,
    groupName = "М21",
    competitionId = "1",
    commandName = "СК Компас",
    startNumber = "101",
    startTime = 1700000000000L,
    chipNumber = "500123",
    comment = "",
    isChipGiven = true
)

private val previewSplits = listOf(
    SplitTime(controlPoint = 31, timestamp = 1700000120000L),
    SplitTime(controlPoint = 32, timestamp = 1700000300000L),
    SplitTime(controlPoint = 33, timestamp = 1700000510000L),
    SplitTime(controlPoint = 34, timestamp = 1700000780000L),
    SplitTime(controlPoint = 99, timestamp = 1700000960000L),
)

private val previewResult = OrienteeringResult(
    id = 1L,
    competitionId = "1",
    groupId = 1L,
    participantId = "1",
    startTime = 1700000000000L,
    finishTime = 1700000960000L,
    totalTime = 960L,
    rank = 3,
    status = ResultStatus.FINISHED,
    splits = previewSplits
)

@Preview(name = "Ожидание чипа", showBackground = true)
@Composable
private fun EmptyReadCardPreview() {
    MaterialTheme {
        EmptyReadCardView()
    }
}

@Preview(name = "Баннер «только просмотр»", showBackground = true)
@Composable
private fun ReadOnlyBannerPreview() {
    MaterialTheme {
        ReadOnlyBanner()
    }
}

@Preview(name = "Ожидание чипа — соревнование завершено", showBackground = true)
@Composable
private fun EmptyReadCardReadOnlyPreview() {
    MaterialTheme {
        EmptyReadCardView(isReadOnly = true)
    }
}

@Preview(name = "Результат участника", showBackground = true)
@Composable
private fun ReadCardContentPreview() {
    MaterialTheme {
        ReadCardContent(
            participant = previewParticipant,
            result = previewResult,
            rawSplits = previewSplits,
            expectedCpOrder = listOf(31, 32, 33, 34, 99)
        )
    }
}

@Preview(name = "Результат без сплитов", showBackground = true)
@Composable
private fun ReadCardContentNoSplitsPreview() {
    MaterialTheme {
        ReadCardContent(
            participant = previewParticipant,
            result = previewResult.copy(splits = emptyList()),
            rawSplits = emptyList()
        )
    }
}

@Preview(name = "Карточка участника", showBackground = true)
@Composable
private fun ParticipantInfoCardPreview() {
    MaterialTheme {
        ParticipantInfoCard(participant = previewParticipant)
    }
}

@Preview(name = "Карточка итогового времени", showBackground = true)
@Composable
private fun RaceSummaryCardPreview() {
    MaterialTheme {
        RaceSummaryCard(participant = previewParticipant, result = previewResult, groupRank = 2, groupTotalFinished = 5)
    }
}

@Preview(name = "Сплиты", showBackground = true)
@Composable
private fun SplitsCardPreview() {
    MaterialTheme {
        SplitsCard(
            participant = previewParticipant,
            splits = previewSplits,
            expectedCpOrder = listOf(31, 32, 33, 34, 99)
        )
    }
}

@Preview(name = "Сплиты — лишний КП + пропуск", showBackground = true)
@Composable
private fun SplitsCardDsqPreview() {
    MaterialTheme {
        SplitsCard(
            participant = previewParticipant,
            splits = listOf(
                SplitTime(controlPoint = 31, timestamp = 1700000120000L),
                SplitTime(controlPoint = 55, timestamp = 1700000200000L), // лишний
                SplitTime(controlPoint = 33, timestamp = 1700000510000L), // 32 пропущен
                SplitTime(controlPoint = 34, timestamp = 1700000780000L),
                SplitTime(controlPoint = 99, timestamp = 1700000960000L),
            ),
            expectedCpOrder = listOf(31, 32, 33, 34, 99)
        )
    }
}

@Preview(name = "Сплиты — бабочка (повторы КП)", showBackground = true)
@Composable
private fun SplitsCardButterflyPreview() {
    MaterialTheme {
        // Дистанция-бабочка: центральный КП 50 берётся дважды — [50, 31, 50, 32].
        SplitsCard(
            participant = previewParticipant,
            splits = listOf(
                SplitTime(controlPoint = 50, timestamp = 1700000100000L),
                SplitTime(controlPoint = 32, timestamp = 1700000250000L), // взят раньше времени
                SplitTime(controlPoint = 50, timestamp = 1700000400000L),
                SplitTime(controlPoint = 31, timestamp = 1700000550000L),
                SplitTime(controlPoint = 50, timestamp = 1700000700000L), // лишний повтор сверх нужного
            ),
            expectedCpOrder = listOf(50, 31, 50, 32)
        )
    }
}

// endregion

/**
 * Нижний лист для редактирования отметки на конкретном КП.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSplitBottomSheet(
    split: SplitTime,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSave: (newTimestamp: Long) -> Unit,
    onDelete: () -> Unit
) {
    var timeStr by remember(split) {
        mutableStateOf(DateTimeFormat.transformLongToTime(split.timestamp))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SIZE_BASE.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Редактировать КП ${split.controlPoint}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            DSTextInput(
                modifier = Modifier.fillMaxWidth(),
                text = timeStr,
                onValueChanged = { timeStr = it },
                label = { Text("Время отметки (HH:mm:ss)") }
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            Button(
                onClick = {
                    val newTimestamp = DateTimeFormat.updateTimeInTimestamp(split.timestamp, timeStr)
                    if (newTimestamp != null) onSave(newTimestamp)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
            ) {
                Text("Сохранить изменения", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                )
            ) {
                Text("Удалить отметку", fontWeight = FontWeight.Bold)
            }
        }
    }
}
