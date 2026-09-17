package com.competra.center.presentation.orientiring_competition_create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.competra.designsystem.components.DSBottomDialog
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.theme.Dimens
import com.competra.center.data.creator.OrienteeringCreatorAction
import com.competra.center.data.creator.OrienteeringCreatorState
import com.competra.domain.models.orienteering.ControlPoint
import com.competra.domain.models.orienteering.ControlPointRole
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.resources.R

/**
 * Диалог редактирования/создания дистанции.
 * 
 * Реализован принудительный переход фокуса между полями ввода с использованием FocusRequester
 * и гарантированное скрытие клавиатуры через SoftwareKeyboardController.
 * 
 * @param userAction Обработчик действий ViewModel.
 * @param state Текущее состояние процесса создания.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistanceEditor(
    userAction: (OrienteeringCreatorAction) -> Unit,
    state: OrienteeringCreatorState,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialDistance = state.distances.getOrNull(state.editDistanceIndex)

    var title by remember { mutableStateOf(initialDistance?.name ?: "") }
    var lengthMeters by remember { mutableStateOf(initialDistance?.lengthMeters?.toString() ?: "") }
    var climbMeters by remember { mutableStateOf(initialDistance?.climbMeters?.toString() ?: "") }
    var controlsCount by remember {
        mutableStateOf(
            initialDistance?.controlsCount?.toString() ?: ""
        )
    }
    var controlPointsList by remember {
        mutableStateOf<List<ControlPoint>>(initialDistance?.controlPoints.orEmpty())
    }
    val isByChoice = state.competitionDirection == OrienteeringDirection.BY_CHOICE
    var currentInput by remember { mutableStateOf("") }
    var finishCpInput by remember {
        mutableStateOf(initialDistance?.finishControlPoint?.toString() ?: "")
    }
    var showFinishCpError by remember { mutableStateOf(false) }
    var startCpInput by remember {
        mutableStateOf(initialDistance?.startControlPoint?.toString() ?: "")
    }
    var showStartCpError by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf(initialDistance?.description ?: "") }

    // Финишное КП обязательно для электронных систем отметки.
    val isFinishCpRequired = state.punchingSystem in setOf(
        PunchingSystem.SPORTIDUINO,
        PunchingSystem.SPORTIDENT,
        PunchingSystem.SFR
    )

    // Стартовое КП (номер отдельной физической старт-станции) обязательно только при старте
    // по отметке на стартовой станции — только там реальное время старта участника берётся из чипа.
    val isStartCpRequired = state.startTimeMode == StartTimeMode.BY_START_STATION

    // Создаем реквизиторы фокуса для каждого поля
    val titleFocus = remember { FocusRequester() }
    val lengthFocus = remember { FocusRequester() }
    val climbFocus = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }
    val pointsFocus = remember { FocusRequester() }
    val finishCpFocus = remember { FocusRequester() }
    val startCpFocus = remember { FocusRequester() }
    val descFocus = remember { FocusRequester() }
    
    LaunchedEffect(controlPointsList) {
        if (controlPointsList.isNotEmpty()) {
            controlsCount = controlPointsList.size.toString()
        }
    }

    val addCurrentInput: () -> Unit = {
        val number = currentInput.trim().toIntOrNull()
        if (number != null) {
            controlPointsList = controlPointsList + ControlPoint(number = number, score = if (isByChoice) 2 else 0)
            currentInput = ""
        }
    }

    val updateControlPoint: (Int, ControlPoint) -> Unit = { index, updated ->
        controlPointsList = controlPointsList.toMutableList().also { it[index] = updated }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    DSBottomDialog(
        sheetState = sheetState,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.SIZE_BASE.dp)
                    .padding(bottom = Dimens.SIZE_BASE.dp)
            ) {
                Text(
                    text = if (state.editDistanceIndex == -1) "Новая дистанция" else "Редактирование дистанции",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = Dimens.SIZE_BASE.dp)
                )

                // Название дистанции
                DSTextInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocus),
                    label = { Text("Название дистанции") },
                    supportingText = { Text("Под этим именем участники увидят дистанцию при выборе") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { lengthFocus.requestFocus() }
                    ),
                    text = title,
                    onValueChanged = { title = it }
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Длина
                    DSTextInput(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(lengthFocus),
                        label = { Text("Длина (м)") },
                        supportingText = { Text("Длина по оптимальному маршруту") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { climbFocus.requestFocus() }
                        ),
                        text = lengthMeters,
                        onValueChanged = { lengthMeters = it }
                    )

                    // Набор высоты
                    DSTextInput(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(climbFocus),
                        label = { Text("Набор высоты (м)") },
                        supportingText = { Text("Суммарный набор высоты") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { pointsFocus.requestFocus() }
                        ),
                        text = climbMeters,
                        onValueChanged = { climbMeters = it }
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                // Количество КП
//                DSTextInput(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .focusRequester(controlsFocus),
//                    label = { Text("Количество КП") },
//                    supportingText = { Text("Сколько КП нужно взять для зачёта прохождения") },
//                    singleLine = true,
//                    keyboardOptions = KeyboardOptions(
//                        keyboardType = KeyboardType.Number,
//                        imeAction = ImeAction.Next
//                    ),
//                    keyboardActions = KeyboardActions(
//                        onNext = { pointsFocus.requestFocus() }
//                    ),
//                    text = controlsCount,
//                    onValueChanged = { controlsCount = it }
//                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                // Список КП (чипы)
                Text(
                    text = "Список КП",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimens.SIZE_QUARTER.dp, bottom = Dimens.SIZE_QUARTER.dp)
                )
                FieldDescription(
                    if (isByChoice) {
                        "Набор КП дистанции. Порядок взятия участник выбирает сам"
                    } else {
                        "Номера КП в порядке прохождения по дистанции"
                    }
                )

                if (controlPointsList.isNotEmpty()) {
                    if (isByChoice) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_QUARTER.dp)) {
                            controlPointsList.forEachIndexed { index, cp ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_QUARTER.dp)
                                ) {
                                    Text(
                                        text = cp.number.toString(),
                                        modifier = Modifier.width(40.dp)
                                    )
                                    DSTextInput(
                                        modifier = Modifier.width(90.dp),
                                        label = { Text("Баллы") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        text = cp.score.toString(),
                                        onValueChanged = { newValue ->
                                            val score = newValue.filter { it.isDigit() }.toIntOrNull() ?: 0
                                            updateControlPoint(index, cp.copy(score = score))
                                        }
                                    )
                                    FilterChip(
                                        selected = cp.role == ControlPointRole.REQUIRED,
                                        onClick = {
                                            val newRole = if (cp.role == ControlPointRole.REQUIRED) {
                                                ControlPointRole.ORDINARY
                                            } else {
                                                ControlPointRole.REQUIRED
                                            }
                                            updateControlPoint(index, cp.copy(role = newRole))
                                        },
                                        label = { Text("Обязательный") }
                                    )
                                    IconButton(
                                        onClick = {
                                            controlPointsList = controlPointsList.toMutableList()
                                                .also { it.removeAt(index) }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = ImageVector.vectorResource(R.drawable.close_24px),
                                            contentDescription = "Удалить КП ${cp.number}"
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(Dimens.SIZE_QUARTER.dp))
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_QUARTER.dp),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_QUARTER.dp)
                        ) {
                            controlPointsList.forEachIndexed { index, cp ->
                                InputChip(
                                    selected = false,
                                    onClick = { /* no-op */ },
                                    label = { Text(text = cp.number.toString()) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = ImageVector.vectorResource(R.drawable.close_24px),
                                            contentDescription = "Удалить КП ${cp.number}",
                                            modifier = Modifier
                                                .size(Dimens.SIZE_BASE.dp)
                                                .clickable {
                                                    controlPointsList = controlPointsList.toMutableList()
                                                        .also { it.removeAt(index) }
                                                }
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(Dimens.SIZE_QUARTER.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DSTextInput(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(pointsFocus),
                        label = { Text("Добавить КП") },
                        placeholder = { Text("31") },
                        supportingText = { Text("Введите номер КП и нажмите Enter или ✚") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { addCurrentInput() }
                        ),
                        text = currentInput,
                        onValueChanged = { newValue ->
                            currentInput = newValue.filter { it.isDigit() }
                        }
                    )
                    IconButton(onClick = addCurrentInput) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_add_24px),
                            contentDescription = "Добавить КП"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                // Финишное КП
                DSTextInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(finishCpFocus),
                    label = {
                        Text(
                            if (isFinishCpRequired) "Финишное КП *" else "Финишное КП"
                        )
                    },
                    supportingText = {
                        val errorText = "Для систем SPORTIDUINO/SPORTIDENT/SFR необходимо указать финишное КП"
                        val hintText = "Номер КП на финише — по нему рассчитывается финишное время"
                        Text(if (showFinishCpError) errorText else hintText)
                    },
                    isError = showFinishCpError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { if (isStartCpRequired) startCpFocus.requestFocus() else descFocus.requestFocus() }
                    ),
                    text = finishCpInput,
                    onValueChanged = { newValue ->
                        finishCpInput = newValue.filter { it.isDigit() }
                        if (showFinishCpError && finishCpInput.toIntOrNull() != null) {
                            showFinishCpError = false
                        }
                    }
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                // Стартовое КП — номер отдельной физической старт-станции (только при старте
                // по отметке на стартовой станции).
                if (isStartCpRequired) {
                    DSTextInput(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(startCpFocus),
                        label = { Text("Стартовое КП *") },
                        supportingText = {
                            val errorText = "Укажите номер стартового КП — по нему определяется реальное время старта"
                            val hintText = "Номер КП на стартовой станции — по нему рассчитывается время старта"
                            Text(if (showStartCpError) errorText else hintText)
                        },
                        isError = showStartCpError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { descFocus.requestFocus() }
                        ),
                        text = startCpInput,
                        onValueChanged = { newValue ->
                            startCpInput = newValue.filter { it.isDigit() }
                            if (showStartCpError && startCpInput.toIntOrNull() != null) {
                                showStartCpError = false
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                }

                // Описание
                DSTextInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(descFocus),
                    label = { Text("Описание") },
                    supportingText = { Text("Дополнительные сведения о дистанции для участников") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            focusManager.clearFocus()
                        },
                        onDone = {
                            descFocus.freeFocus()
                            focusManager.clearFocus(force = true)
                            view.clearFocus()
                            keyboardController?.hide()
                        }
                    ),
                    text = description,
                    onValueChanged = { description = it }
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
                    onClick = {
                        val finishCp = finishCpInput.toIntOrNull()
                        if (isFinishCpRequired && finishCp == null) {
                            showFinishCpError = true
                            finishCpFocus.requestFocus()
                            return@Button
                        }

                        val startCp = startCpInput.toIntOrNull()
                        if (isStartCpRequired && startCp == null) {
                            showStartCpError = true
                            startCpFocus.requestFocus()
                            return@Button
                        }

                        // Сбрасываем фокус и скрываем клавиатуру при нажатии кнопки сохранения
                        focusManager.clearFocus()
                        keyboardController?.hide()

                        val points = controlPointsList

                        userAction.invoke(
                            OrienteeringCreatorAction.CreateDistance(
                                distance = Distance(
                                    id = if (state.editDistanceIndex == -1) 0L else state.distances[state.editDistanceIndex].id,
                                    competitionId = state.competitionId.orEmpty(),
                                    name = title,
                                    lengthMeters = lengthMeters.toIntOrNull() ?: 0,
                                    climbMeters = climbMeters.toIntOrNull() ?: 0,
                                    controlsCount = controlsCount.toIntOrNull() ?: 0,
                                    description = description,
                                    controlPoints = points,
                                    finishControlPoint = finishCp,
                                    startControlPoint = startCp
                                ),
                                index = state.editDistanceIndex
                            )
                        )
                    }
                ) {
                    Text(text = "Готово", fontWeight = FontWeight.Bold)
                }
            }
        },
        onDismiss = {
            userAction.invoke(OrienteeringCreatorAction.HideDistanceCreateDialog)
        },
    )
}
