package com.competra.center.presentation.read_card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.competra.designsystem.components.DSBottomDialog
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.components.ExposedDropdownMenuOutlined
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.KnownParticipant
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.ParticipantHistory
import com.competra.domain.models.orienteering.SplitTime
import com.competra.domain.models.orienteering.UnassignedChipRead
import com.competra.utils.orienteering.toRaceTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Плашка «Неразобранные считывания: N» (режим «Регистрация на финише») — открывает их список.
 */
@Composable
internal fun UnassignedReadsBanner(count: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SIZE_BASE.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Неразобранные считывания: $count",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Чипы, ещё не привязанные к участникам",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            TextButton(onClick = onOpen) { Text("Разобрать") }
        }
    }
}

/**
 * Список неразобранных считываний: номер чипа, время считывания, число отметок и время на
 * дистанции (от стартовой до финишной станции, если обе отметки есть).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnassignedReadsSheet(
    reads: List<UnassignedChipRead>,
    startStationCode: Int?,
    finishStationCode: Int?,
    /** Подходящие по отметкам группы по номеру чипа. */
    matchingGroups: Map<Int, List<String>>,
    onOpen: (chipNumber: Int) -> Unit,
    onDelete: (chipNumber: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var deleting by remember { mutableStateOf<UnassignedChipRead?>(null) }
    DSBottomDialog(
        sheetState = sheetState,
        onDismiss = onDismiss,
        sheetContent = {
            Column(modifier = Modifier.padding(Dimens.SIZE_BASE.dp)) {
                Text(
                    text = "Неразобранные считывания",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                if (reads.isEmpty()) {
                    Text(
                        text = "Все считывания разобраны",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(reads, key = { it.chipNumber }) { read ->
                        UnassignedReadRow(
                            read = read,
                            startStationCode = startStationCode,
                            finishStationCode = finishStationCode,
                            matchingGroups = matchingGroups[read.chipNumber].orEmpty(),
                            onClick = { onOpen(read.chipNumber) },
                            onDelete = { deleting = read }
                        )
                        HorizontalDivider()
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
            }
        }
    )
    deleting?.let { read ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить считывание?") },
            text = { Text("Считывание чипа №${read.chipNumber} будет удалено и не попадёт в результаты.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(read.chipNumber)
                    deleting = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun UnassignedReadRow(
    read: UnassignedChipRead,
    startStationCode: Int?,
    finishStationCode: Int?,
    matchingGroups: List<String>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val raceTime = raceTimeOf(read.splits, startStationCode, finishStationCode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.SIZE_HALF.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Чип №${read.chipNumber}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Считан в ${formatClock(read.readAt)} · отметок: ${read.splits.size}" +
                    (raceTime?.let { " · время $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (read.splits.isNotEmpty()) {
                Text(
                    text = punchChain(read.splits, startStationCode, finishStationCode),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (matchingGroups.isNotEmpty()) {
                Text(
                    text = "По отметкам: ${matchingGroups.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        TextButton(onClick = onDelete) { Text("Удалить") }
    }
}

/**
 * Карточка участника для считывания незнакомого чипа: «Новый участник» (ФИО с подсказками по
 * прошлым соревнованиям, группа, команда) или «Привязать к существующему» (участник без результата).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssignReadSheet(
    read: UnassignedChipRead,
    groups: List<ParticipantGroup>,
    candidates: List<OrienteeringParticipant>,
    history: List<KnownParticipant>,
    createdGroupId: Long?,
    /** Группы, подходящие по отметкам чипа. */
    matchingGroupIds: Set<Long>,
    startStationCode: Int?,
    finishStationCode: Int?,
    onCreate: (lastName: String, firstName: String, commandName: String, groupId: Long, fromHistory: Boolean) -> Unit,
    onAssign: (participantId: String) -> Unit,
    onCreateGroup: (title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by rememberSaveable(read.chipNumber) { mutableStateOf(0) }
    DSBottomDialog(
        sheetState = sheetState,
        onDismiss = onDismiss,
        sheetContent = {
            Column(modifier = Modifier.padding(Dimens.SIZE_BASE.dp)) {
                Text(
                    text = "Чип №${read.chipNumber}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Считан в ${formatClock(read.readAt)} · отметок: ${read.splits.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                PunchSummary(splits = read.splits, startStationCode = startStationCode, finishStationCode = finishStationCode)
                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                if (candidates.isNotEmpty()) {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Новый участник") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Уже заявлен") })
                    }
                    Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                }
                if (tab == 0 || candidates.isEmpty()) {
                    ParticipantForm(
                        formKey = "new-${read.chipNumber}",
                        groups = groups,
                        history = history,
                        createdGroupId = createdGroupId,
                        matchingGroupIds = matchingGroupIds,
                        submitText = "Сохранить и посчитать результат",
                        onSubmit = onCreate,
                        onCreateGroup = onCreateGroup
                    )
                } else {
                    ExistingParticipantPicker(candidates = candidates, onAssign = onAssign)
                }
                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
            }
        }
    )
}

/**
 * Форма участника: ФИО (с подсказками из [history]), группа (+ «новая группа», с пометкой
 * подходящих по отметкам [matchingGroupIds]) и команда. Общая для «Нового участника» и
 * «Изменить участника» ([initial] — текущие данные участника).
 *
 * @param formKey Ключ сохраняемого состояния полей — своя форма на каждый чип / участника.
 */
@Composable
private fun ParticipantForm(
    formKey: String,
    groups: List<ParticipantGroup>,
    history: List<KnownParticipant>,
    createdGroupId: Long?,
    matchingGroupIds: Set<Long>,
    submitText: String,
    onSubmit: (lastName: String, firstName: String, commandName: String, groupId: Long, fromHistory: Boolean) -> Unit,
    onCreateGroup: (title: String) -> Unit,
    initial: OrienteeringParticipant? = null,
) {
    var lastName by rememberSaveable(formKey) { mutableStateOf(initial?.lastName.orEmpty()) }
    var firstName by rememberSaveable(formKey) { mutableStateOf(initial?.firstName.orEmpty()) }
    var commandName by rememberSaveable(formKey) { mutableStateOf(initial?.commandName.orEmpty()) }
    var groupId by rememberSaveable(formKey) { mutableStateOf(initial?.groupId) }
    var fromHistory by rememberSaveable(formKey) { mutableStateOf(initial != null) }
    var isNewGroupInput by rememberSaveable(formKey) { mutableStateOf(false) }
    var newGroupTitle by rememberSaveable(formKey) { mutableStateOf("") }

    // Группа, созданная из карточки, сразу выбирается.
    LaunchedEffect(createdGroupId) {
        if (createdGroupId != null) {
            groupId = createdGroupId
            isNewGroupInput = false
            newGroupTitle = ""
        }
    }
    // Единственная подходящая по отметкам группа выбирается сразу, если группа ещё не выбрана.
    LaunchedEffect(matchingGroupIds) {
        if (groupId == null && matchingGroupIds.size == 1) groupId = matchingGroupIds.first()
    }
    val matchingTitles = groups.filter { it.groupId in matchingGroupIds }.map { it.title }

    val suggestions = remember(lastName, firstName, history) {
        if (fromHistory) emptyList() else ParticipantHistory.search(history, "$lastName $firstName", limit = 5)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)) {
        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            text = lastName,
            onValueChanged = {
                lastName = it
                fromHistory = false
            },
            label = { Text("Фамилия") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            text = firstName,
            onValueChanged = {
                firstName = it
                fromHistory = false
            },
            label = { Text("Имя") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
        suggestions.forEach { known ->
            val extra = listOf(known.lastGroupName, known.commandName).filter { it.isNotBlank() }.joinToString(" · ")
            Text(
                text = "${known.lastName} ${known.firstName}" + if (extra.isNotEmpty()) " · $extra" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        lastName = known.lastName
                        firstName = known.firstName
                        if (commandName.isBlank()) commandName = known.commandName
                        if (groupId == null) {
                            groupId = groups.firstOrNull {
                                ParticipantHistory.normalizeGroupName(it.title) ==
                                    ParticipantHistory.normalizeGroupName(known.lastGroupName)
                            }?.groupId
                        }
                        fromHistory = true
                    }
                    .padding(vertical = 6.dp)
            )
        }
        if (isNewGroupInput) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DSTextInput(
                    modifier = Modifier.weight(1f),
                    text = newGroupTitle,
                    onValueChanged = { newGroupTitle = it },
                    label = { Text("Новая группа") },
                    singleLine = true
                )
                TextButton(
                    onClick = { onCreateGroup(newGroupTitle) },
                    enabled = newGroupTitle.isNotBlank()
                ) { Text("Создать") }
            }
        } else {
            ExposedDropdownMenuOutlined(
                modifier = Modifier.fillMaxWidth(),
                label = "Группа",
                items = groups,
                selectedItem = groups.firstOrNull { it.groupId == groupId },
                onItemSelected = { groupId = it.groupId },
                itemToString = { if (it.groupId in matchingGroupIds) "${it.title} · по отметкам" else it.title }
            )
            if (matchingTitles.isNotEmpty()) {
                Text(
                    text = "По отметкам подходит: ${matchingTitles.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TextButton(onClick = { isNewGroupInput = true }) { Text("+ новая группа") }
        }
        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            text = commandName,
            onValueChanged = { commandName = it },
            label = { Text("Команда (необязательно)") },
            singleLine = true
        )
        val selectedGroupId = groupId
        Button(
            onClick = {
                if (selectedGroupId != null) onSubmit(lastName, firstName, commandName, selectedGroupId, fromHistory)
            },
            enabled = lastName.isNotBlank() && firstName.isNotBlank() && selectedGroupId != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
        ) {
            Text(submitText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExistingParticipantPicker(
    candidates: List<OrienteeringParticipant>,
    onAssign: (participantId: String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, candidates) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) candidates else candidates.filter {
            "${it.lastName} ${it.firstName} ${it.startNumber}".lowercase().contains(q)
        }
    }
    Column {
        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            text = query,
            onValueChanged = { query = it },
            label = { Text("Поиск по ФИО или номеру") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
            items(filtered, key = { it.id }) { participant ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAssign(participant.id) }
                        .padding(vertical = Dimens.SIZE_HALF.dp)
                ) {
                    Text(
                        text = "${participant.lastName} ${participant.firstName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = listOf(
                            participant.groupName,
                            "№${participant.startNumber}".takeIf { participant.startNumber.isNotBlank() },
                            "чип ${participant.chipNumber}".takeIf { participant.chipNumber.isNotBlank() }
                        ).filterNotNull().joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/**
 * Ручной статус результата (режим «Регистрация на финише»): «Снять» засчитанный результат,
 * «Засчитать» снятый по отметке финиша, а без отметки финиша — «по времени считывания».
 *
 * @param canCredit Есть время старта и отметка финишной станции — можно засчитать.
 * @param canCreditByReadTime Отметки финиша нет, но известно время считывания.
 */
@Composable
internal fun ManualStatusPanel(
    result: OrienteeringResult,
    canCredit: Boolean,
    canCreditByReadTime: Boolean,
    onSetStatus: (finished: Boolean) -> Unit,
    onCreditByReadTime: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)) {
        if (result.isStatusManual) {
            Text(
                text = "Статус выставлен организатором вручную",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when (result.status) {
            ResultStatus.FINISHED, ResultStatus.OVERTIME -> OutlinedButton(
                onClick = { onSetStatus(false) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Снять результат") }

            ResultStatus.DSQ -> {
                if (canCredit) {
                    OutlinedButton(onClick = { onSetStatus(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Засчитать результат")
                    }
                }
                if (canCreditByReadTime) {
                    OutlinedButton(onClick = onCreditByReadTime, modifier = Modifier.fillMaxWidth()) {
                        Text("Засчитать финиш по времени считывания")
                    }
                }
            }

            else -> Unit
        }
    }
}

/**
 * Кнопки под участником на экране считывания (режим «Регистрация на финише»): исправить
 * участника (ФИО, команда, группа) или отвязать чип, если он считан не тому человеку.
 */
@Composable
internal fun FinishParticipantActions(onEdit: () -> Unit, onUnlink: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)) {
        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Изменить участника") }
        OutlinedButton(onClick = onUnlink, modifier = Modifier.weight(1f)) { Text("Отвязать чип") }
    }
}

/**
 * Карточка «Изменить участника»: ФИО, команда, группа. При смене группы результат
 * пересчитывается по дистанции новой группы.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditParticipantSheet(
    participant: OrienteeringParticipant,
    groups: List<ParticipantGroup>,
    createdGroupId: Long?,
    matchingGroupIds: Set<Long>,
    onSave: (lastName: String, firstName: String, commandName: String, groupId: Long) -> Unit,
    onCreateGroup: (title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    DSBottomDialog(
        sheetState = sheetState,
        onDismiss = onDismiss,
        sheetContent = {
            Column(modifier = Modifier.padding(Dimens.SIZE_BASE.dp)) {
                Text(
                    text = "Изменить участника",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "При смене группы результат пересчитается по её дистанции",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                ParticipantForm(
                    formKey = "edit-${participant.id}",
                    groups = groups,
                    history = emptyList(),
                    createdGroupId = createdGroupId,
                    matchingGroupIds = matchingGroupIds,
                    submitText = "Сохранить",
                    onSubmit = { lastName, firstName, commandName, groupId, _ ->
                        onSave(lastName, firstName, commandName, groupId)
                    },
                    onCreateGroup = onCreateGroup,
                    initial = participant
                )
                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
            }
        }
    )
}

/**
 * Подтверждение «Отвязать чип»: считывание вернётся в неразобранные. Участника, внесённого на
 * финише по ошибке, можно удалить; заявленный заранее остаётся без чипа и результата.
 */
@Composable
internal fun UnlinkChipDialog(
    participant: OrienteeringParticipant,
    onUnlink: (deleteParticipant: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отвязать чип №${participant.chipNumber}?") },
        text = {
            Text(
                "Результат ${participant.lastName} ${participant.firstName} будет сброшен, а считывание " +
                    "вернётся в неразобранные — его можно сразу привязать к другому участнику.\n\n" +
                    "«Удалить участника» — если его внесли по ошибке. «Оставить участника» — если он заявлен, " +
                    "но чип считан не ему: он останется без чипа и результата."
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onUnlink(true) }) {
                    Text("Удалить участника", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { onUnlink(false) }) { Text("Оставить участника") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}

/**
 * Отметки чипа для карточки незнакомого чипа: цепочка КП по времени и время на дистанции
 * (от стартовой до финишной станции). Пустой чип — предупреждение.
 */
@Composable
internal fun PunchSummary(splits: List<SplitTime>, startStationCode: Int?, finishStationCode: Int?) {
    if (splits.isEmpty()) {
        Text(
            text = "На чипе нет отметок — возможно, чип не очищали перед стартом или участник не стартовал",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        return
    }
    val start = startStationCode?.let { code -> splits.firstOrNull { it.controlPoint == code } }
    val raceTime = raceTimeOf(splits, startStationCode, finishStationCode)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(Dimens.SIZE_BASE.dp)) {
            Text(
                text = "Отметки на чипе",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = punchChain(splits, startStationCode, finishStationCode),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            val times = listOfNotNull(
                start?.let { "старт ${formatClock(it.timestamp)}" },
                raceTime?.let { "на дистанции $it" } ?: "нет отметки финиша".takeIf { start != null }
            )
            if (times.isNotEmpty()) {
                Text(
                    text = times.joinToString(" · ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Цепочка КП по времени отметок: «240 (старт) → 46 → 47 → 48 → 245 (финиш)». */
private fun punchChain(splits: List<SplitTime>, startStationCode: Int?, finishStationCode: Int?): String =
    splits.sortedBy { it.timestamp }.joinToString(" → ") { split ->
        when (split.controlPoint) {
            startStationCode -> "${split.controlPoint} (старт)"
            finishStationCode -> "${split.controlPoint} (финиш)"
            else -> split.controlPoint.toString()
        }
    }

/** Время от отметки стартовой станции до первой отметки финишной после неё; null — нет одной из них. */
private fun raceTimeOf(splits: List<SplitTime>, startStationCode: Int?, finishStationCode: Int?): String? {
    val start = startStationCode?.let { code -> splits.firstOrNull { it.controlPoint == code } } ?: return null
    val finish = finishStationCode?.let { code ->
        splits.firstOrNull { it.controlPoint == code && it.timestamp > start.timestamp }
    } ?: return null
    return ((finish.timestamp - start.timestamp) / 1000L).toRaceTime()
}

private fun formatClock(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
