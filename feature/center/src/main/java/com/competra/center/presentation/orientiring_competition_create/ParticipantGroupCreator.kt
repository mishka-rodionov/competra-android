package com.competra.center.presentation.orientiring_competition_create

import com.competra.domain.models.orienteering.GroupTitleInfo
import com.competra.domain.models.orienteering.NO_DISTANCE_ID
import com.competra.domain.models.orienteering.StandardGroups
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.competra.designsystem.components.DSBottomDialog
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.theme.Dimens
import com.competra.center.data.creator.OrienteeringCreatorAction
import com.competra.center.data.creator.OrienteeringCreatorState
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.isGroupControlTimeRequired
import com.competra.domain.models.orienteering.effectiveFormat
import com.competra.center.data.creator.defaultFormat
import com.competra.resources.R

/**
 * Редактор группы участников соревнований по ориентированию.
 * 
 * Включает выбор дистанции из списка существующих для данного соревнования.
 * 
 * @param userAction Обработчик действий ViewModel.
 * @param state Текущее состояние процесса создания.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantGroupEditor(
    userAction: (OrienteeringCreatorAction) -> Unit,
    state: OrienteeringCreatorState,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val initialGroup = state.participantGroups.getOrNull(state.editGroupIndex)
    
    var groupTitle by remember { mutableStateOf(initialGroup?.title ?: "") }
    var minAge by remember { mutableStateOf(initialGroup?.minAge?.toString() ?: "") }
    var maxAge by remember { mutableStateOf(initialGroup?.maxAge?.toString() ?: "") }
    var maxParticipants by remember { mutableStateOf(initialGroup?.maxParticipants?.toString() ?: "") }
    var selectedGender by remember { mutableStateOf(initialGroup?.gender) }
    // Последние пол/возраст, подставленные по названию: поле, которое организатор поменял сам,
    // при дальнейшем вводе названия не перетираем.
    var autoFilled by remember { mutableStateOf<GroupTitleInfo?>(null) }
    // Выбранная дистанция для группы
    var selectedDistanceId by remember {
        mutableLongStateOf(initialGroup?.distanceId ?: state.distances.firstOrNull()?.id ?: NO_DISTANCE_ID)
    }
    // Штраф очками за опоздание есть только на дистанциях с баллами («по выбору» по баллам) — формат
    // берётся у выбранной дистанции группы, без дистанции — у соревнования.
    val isScoreO = state.distances.firstOrNull { it.id == selectedDistanceId }
        .effectiveFormat(state.defaultFormat).ranksByScore
    // Пусто = наследовать КВ соревнования. Дефолт для BY_CHOICE не подставляем: иначе
    // организатор случайно переопределит КВ во всех группах.
    var timeLimitMinutes by remember {
        mutableStateOf(initialGroup?.timeLimitMinutes?.toString() ?: "")
    }
    var scorePenaltyPerMinute by remember {
        mutableStateOf(initialGroup?.scorePenaltyPerMinute?.toString() ?: if (isScoreO) "1" else "")
    }
    var maxLatenessMinutes by remember {
        mutableStateOf(initialGroup?.maxLatenessMinutes?.toString() ?: if (isScoreO) "30" else "")
    }
    // Пусто = N командного зачёта как у соревнования.
    var teamCountedResults by remember {
        mutableStateOf(initialGroup?.teamCountedResults?.toString() ?: "")
    }

    // На дистанции с баллами со штрафом за опоздание штраф считается от КВ — без КВ (своего или
    // соревнования) он не начислится, поэтому тогда поле КВ группы обязательно.
    val isControlTimeRequired = isGroupControlTimeRequired(
        groupFormat = state.distances.firstOrNull { it.id == selectedDistanceId }.effectiveFormat(state.defaultFormat),
        scoreOvertimePolicy = state.scoreOvertimePolicy,
        competitionControlTimeMinutes = state.controlTimeMinutes
    )
    var showControlTimeError by remember { mutableStateOf(false) }

    // Сменили дистанцию на дистанцию с баллами — подставляем умолчания штрафа, если поля пусты.
    LaunchedEffect(isScoreO) {
        if (isScoreO) {
            if (scorePenaltyPerMinute.isEmpty()) scorePenaltyPerMinute = "1"
            if (maxLatenessMinutes.isEmpty()) maxLatenessMinutes = "30"
        }
    }

    // Реквизиторы фокуса
    val titleFocus = remember { FocusRequester() }
    val minAgeFocus = remember { FocusRequester() }
    val maxAgeFocus = remember { FocusRequester() }
    val limitFocus = remember { FocusRequester() }

    DSBottomDialog(
        sheetState = sheetState,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.SIZE_BASE.dp)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = if (state.editGroupIndex == -1) "Создание группы" else "Редактирование группы",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Название группы
                DSTextInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocus),
                    label = { Text(text = stringResource(R.string.label_participant_group_title)) },
                    supportingText = { Text("Принятое обозначение группы, например М21, Ж18 или Open") },
                    isError = state.errors.isGroupTitleError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { minAgeFocus.requestFocus() }),
                    text = groupTitle,
                    onValueChanged = { newTitle ->
                        groupTitle = newTitle
                        if (initialGroup == null) {
                            val info = StandardGroups.parseTitle(newTitle)
                            val prev = autoFilled
                            if (minAge == (prev?.minAge?.toString() ?: "")) minAge = info?.minAge?.toString() ?: ""
                            if (maxAge == (prev?.maxAge?.toString() ?: "")) maxAge = info?.maxAge?.toString() ?: ""
                            if (selectedGender == prev?.gender) selectedGender = info?.gender
                            autoFilled = info
                        }
                    }
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

                // Выбор дистанции
                Text(
                    text = "Выберите дистанцию",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                FieldDescription("Дистанция, которую будут проходить участники этой группы")
                
                if (state.distances.isEmpty()) {
                    Text(
                        text = "Дистанций пока нет — группа будет без дистанции. Назначьте её, когда добавите дистанции.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            NoDistanceSelectCard(
                                isSelected = state.distances.none { it.id == selectedDistanceId },
                                onSelect = { selectedDistanceId = NO_DISTANCE_ID }
                            )
                        }
                        items(state.distances) { distance ->
                            DistanceSelectCard(
                                distance = distance,
                                isSelected = selectedDistanceId == distance.id,
                                onSelect = { selectedDistanceId = distance.id }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

                // Возрастные ограничения
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DSTextInput(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(minAgeFocus),
                        label = { Text(text = "Мин. возраст") },
                        supportingText = { Text("Минимальный возраст участника") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { maxAgeFocus.requestFocus() }),
                        text = minAge,
                        onValueChanged = { minAge = it })

                    DSTextInput(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(maxAgeFocus),
                        label = { Text(text = "Макс. возраст") },
                        supportingText = { Text("Максимальный возраст участника") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { limitFocus.requestFocus() }),
                        text = maxAge,
                        onValueChanged = { maxAge = it })
                }

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                // Лимит участников
                DSTextInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(limitFocus),
                    label = { Text(text = "Лимит участников") },
                    supportingText = { Text("Максимум зарегистрированных в группе") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    text = maxParticipants,
                    onValueChanged = { maxParticipants = it }
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                DSTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isControlTimeRequired) "Контрольное время (мин) *" else "Контрольное время (мин)") },
                    supportingText = {
                        Text(
                            when {
                                state.controlTimeMinutes != null ->
                                    "Пусто — как у соревнования: ${state.controlTimeMinutes} мин"
                                isControlTimeRequired ->
                                    "Обязательно: на дистанции «по выбору» по баллам штраф начисляется после КВ"
                                else -> "Через сколько минут после старта нужно финишировать"
                            }
                        )
                    },
                    isError = showControlTimeError && isControlTimeRequired,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    text = timeLimitMinutes,
                    onValueChanged = {
                        timeLimitMinutes = it.filter { c -> c.isDigit() }
                        showControlTimeError = false
                    }
                )

                state.teamScoring?.let { teamScoring ->
                    Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                    DSTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Участников в командный зачёт") },
                        supportingText = { Text("Пусто — как у соревнования: ${teamScoring.groupCountedResults}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        text = teamCountedResults,
                        onValueChanged = { teamCountedResults = it.filter { c -> c.isDigit() } }
                    )
                }

                if (isScoreO) {
                    Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                    Text(
                        text = "Формат \"по выбору\" по баллам",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FieldDescription("Штраф за опоздание сверх контрольного времени")

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DSTextInput(
                            modifier = Modifier.weight(1f),
                            label = { Text("Штраф (очки/мин)") },
                            supportingText = { Text("За каждую минуту опоздания") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            text = scorePenaltyPerMinute,
                            onValueChanged = { scorePenaltyPerMinute = it.filter { c -> c.isDigit() } }
                        )

                        DSTextInput(
                            modifier = Modifier.weight(1f),
                            label = { Text("Порог обнуления (мин)") },
                            supportingText = { Text("Опоздание сверх — результат 0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            text = maxLatenessMinutes,
                            onValueChanged = { maxLatenessMinutes = it.filter { c -> c.isDigit() } }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))

                // Кнопка сохранения
                Button(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
                    enabled = groupTitle.isNotBlank(),
                    onClick = {
                        if (isControlTimeRequired && (timeLimitMinutes.toIntOrNull() ?: 0) <= 0) {
                            showControlTimeError = true
                            return@Button
                        }
                        userAction.invoke(
                            OrienteeringCreatorAction.CreateParticipantGroup(
                                participantGroup = ParticipantGroup(
                                    groupId = initialGroup?.groupId ?: 0L,
                                    competitionId = state.competitionId.orEmpty(),
                                    title = groupTitle,
                                    gender = selectedGender,
                                    minAge = minAge.toIntOrNull(),
                                    maxAge = maxAge.toIntOrNull(),
                                    distanceId = selectedDistanceId, // Сохраняем ID выбранной дистанции
                                    maxParticipants = maxParticipants.toIntOrNull(),
                                    timeLimitMinutes = timeLimitMinutes.toIntOrNull(),
                                    scorePenaltyPerMinute = if (isScoreO) scorePenaltyPerMinute.toIntOrNull() else null,
                                    maxLatenessMinutes = if (isScoreO) maxLatenessMinutes.toIntOrNull() else null,
                                    // Без командного зачёта поле скрыто — сохраняем прежнее значение.
                                    teamCountedResults = if (state.teamScoring != null) {
                                        teamCountedResults.toIntOrNull()?.takeIf { it > 0 }
                                    } else {
                                        initialGroup?.teamCountedResults
                                    },
                                    isSynced = false,
                                    lastModified = System.currentTimeMillis()
                                ),
                                index = state.editGroupIndex
                            )
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.label_save_group), fontWeight = FontWeight.Bold)
                }
            }
        },
        onDismiss = {
            userAction.invoke(OrienteeringCreatorAction.HideGroupCreateDialog)
        },
    )
}

/** Карточка «дистанция не назначена» — группа открыта для регистрации до планирования дистанций. */
@Composable
private fun NoDistanceSelectCard(isSelected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .selectable(selected = isSelected, onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Не назначена",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                RadioButton(selected = isSelected, onClick = onSelect, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Назначите позже", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Карточка выбора дистанции для группы.
 */
@Composable
private fun DistanceSelectCard(
    distance: Distance,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .selectable(
                selected = isSelected,
                onClick = onSelect
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = distance.name ?: "Без названия",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Длина: ${distance.lengthMeters}м",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "КП: ${distance.controlsCount}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
