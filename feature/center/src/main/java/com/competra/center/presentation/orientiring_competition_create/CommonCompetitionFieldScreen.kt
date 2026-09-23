package com.competra.center.presentation.orientiring_competition_create

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.components.ExposedDropdownMenuOutlined
import com.competra.designsystem.components.ImageCropperDialog
import com.competra.designsystem.components.NetworkImage
import com.competra.designsystem.components.TimePickerDialog
import com.competra.designsystem.theme.Dimens
import com.competra.center.data.creator.OrienteeringCreatorAction
import com.competra.center.data.creator.OrienteeringCreatorState
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.OvertimePolicy
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.resources.R
import com.competra.ui.components.toCropRect
import com.competra.ui.components.toFractionalRect
import com.competra.utils.DateTimeFormat
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * Первый экран создания соревнования: Общая информация.
 * 
 * Включает ввод названия, дат начала (в том числе для многодневных этапов), 
 * адреса, координат места старта и описания.
 * 
 * Реализован автоматический фокус на следующее поле при нажатии "Далее" (Enter) на клавиатуре.
 * 
 * @param competitionId Идентификатор редактируемого соревнования.
 * @param viewModel Вьюмодель процесса создания.
 */
@Composable
fun CommonCompetitionFieldScreen(
    competitionId: String? = null,
    viewModel: OrienteeringCreatorViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.initialize(competitionId)
    }


    LaunchedEffect(state.error) {
        state.error?.let { errorMessage ->
            snackbarHostState.showSnackbar(errorMessage)
//            viewModel.onErrorShown() // важно сбросить ошибку
        }
    }

    CommonCompetitionFieldContent(
        state = state,
        onAction = viewModel::onAction,
        onTitleChanged = viewModel::updateTitle,
        onAddressChanged = viewModel::updateAddress,
        onDescriptionChanged = viewModel::updateDescription,
        onOpenMap = { viewModel.openMapPicker() },
        onBack = viewModel::back,
        onNext = viewModel::saveStepOne
    )
}

/**
 * Контент экрана общей информации о соревновании.
 * Выделен отдельно для поддержки Preview.
 */
@Composable
private fun CommonCompetitionFieldContent(
    state: OrienteeringCreatorState,
    onAction: (OrienteeringCreatorAction) -> Unit,
    onTitleChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onOpenMap: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    val imagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let { onAction(OrienteeringCreatorAction.CoverImagePicked(it)) }
    }

    state.pendingCoverCropUri?.let { uri ->
        ImageCropperDialog(
            uri = uri,
            aspectRatio = 16f / 9f,
            onConfirm = { cropRect -> onAction(OrienteeringCreatorAction.ConfirmCoverCrop(cropRect.toCropRect())) },
            onCancel = { onAction(OrienteeringCreatorAction.CancelCoverCrop) }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationButtons(
                onBack = onBack,
                onNext = onNext,
                nextEnabled = state.title.isNotBlank() && state.address.isNotBlank()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Dimens.SIZE_BASE.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Шаг 1: Общая информация",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            // Debug-инструменты создания: преднабор данных + флаг «тестовое».
            // Виден только в debug-сборке, в релизе блок отсутствует.
            DebugTestToolsBlock(state = state, onAction = onAction)

            // Поле ввода названия
            DSTextInput(
                modifier = Modifier.fillMaxWidth(),
                text = state.title,
                label = { Text("Название соревнования") },
                supportingText = { Text("Так соревнование будет отображаться в списке у участников") },
                onValueChanged = onTitleChanged,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) }
                )
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            // Обложка соревнования
            Text(
                text = "Обложка соревнования",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            NetworkImage(
                url = state.imageUrl,
                cropRect = state.imageCropRect?.toFractionalRect(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(Dimens.SIZE_BASE.dp))
            )
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            OutlinedButton(
                onClick = { imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.imageUrl.isNullOrBlank()) "Выбрать обложку" else "Изменить обложку")
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            // Поля даты и времени начала первого дня
            Text(
                text = "Начало соревнования",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            FieldDescription("Дата и время старта первого участника")
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    DatePicker(state = state, userAction = onAction)
                }
                Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))
                Box(modifier = Modifier.weight(1f)) {
                    TimePicker(state = state, userAction = onAction)
                }
            }
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            TimeZonePicker(
                selectedZoneId = state.timeZoneId,
                onSelect = { onAction(OrienteeringCreatorAction.UpdateTimeZone(it)) }
            )

            // Дополнительные этапы (многодневки) пока отключено
//            if (state.stages.isNotEmpty()) {
            if (false) {
                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
                Text(
                    text = "Дополнительные дни",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                state.stages.forEachIndexed { index, stage ->
                    Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            StageDatePicker(index = index, date = stage.startDate, userAction = onAction)
                        }
                        Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            StageTimePicker(index = index, date = stage.startDate, userAction = onAction)
                        }
                        IconButton(onClick = { onAction(OrienteeringCreatorAction.RemoveStage(index)) }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.delete),
                                contentDescription = "Remove stage",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Добавление новый дней соревнования отключено на первом этапе
//            TextButton(
//                onClick = { onAction(OrienteeringCreatorAction.AddStage) },
//                modifier = Modifier.padding(top = 4.dp)
//            ) {
//                Icon(ImageVector.vectorResource(R.drawable.ic_add_24px), contentDescription = null)
//                Spacer(modifier = Modifier.width(4.dp))
//                Text("Добавить день")
//            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            // Поле ввода адреса
            DSTextInput(
                modifier = Modifier.fillMaxWidth(),
                text = state.address,
                label = { Text("Место проведения (адрес)") },
                supportingText = { Text("Место сбора и старта участников") },
                onValueChanged = onAddressChanged,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) }
                )
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

            // Выбор координат на карте
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.SIZE_HALF.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenMap,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.map_24dp),
                        contentDescription = "Open map",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))
                Column {
                    Text(text = "Координаты старта", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "lat: ${"%.5f".format(state.coordinates.latitude)}, lon: ${"%.5f".format(state.coordinates.longitude)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FieldDescription("Точка старта на карте — поможет участникам построить маршрут")
                }
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            OrienteeringCompetitionDirection(state = state, userAction = onAction)
            FieldDescription("Тип ориентирования: в заданном направлении, по выбору или маркированная трасса")
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            PunchingSystemSelector(state = state, userAction = onAction)
            FieldDescription("Оборудование, которым участники отмечаются на КП")
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            StartTimeModeSelector(state = state, userAction = onAction)
            FieldDescription("Как назначается стартовое время каждого участника")
            if (state.startTimeMode != StartTimeMode.BY_START_STATION) {
                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                StartIntervalSelector(state = state, userAction = onAction)
                FieldDescription("Промежуток между стартами участников при стартах по протоколу")
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            ControlTimeBlock(state = state, userAction = onAction)

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            // Поле ввода описания
            DSTextInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                text = state.description,
                label = { Text("Описание соревнования") },
                supportingText = { Text("Подробности для участников: программа, требования, особенности") },
                onValueChanged = onDescriptionChanged,
                singleLine = false,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )
            
            Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))
        }
    }
}

/**
 * Блок кнопок навигации для переключения между шагами создания соревнования.
 *
 * @param onBack Обработчик нажатия кнопки "Назад".
 * @param onNext Обработчик нажатия основной кнопки действия (например, "Далее" или "Сохранить").
 * @param nextEnabled Флаг доступности кнопки продолжения.
 * @param nextText Текст, отображаемый на основной кнопке (по умолчанию "Далее").
 */
@Composable
fun NavigationButtons(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean = true,
    nextText: String = "Далее"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.SIZE_BASE.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Назад")
        }
        Button(onClick = onNext, enabled = nextEnabled) {
            Text(nextText)
        }
    }
}

/**
 * Компонент выбора даты (Stage).
 */
@Composable
private fun StageDatePicker(index: Int, date: Long, userAction: (OrienteeringCreatorAction) -> Unit) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val focusManager = LocalFocusManager.current

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val dateMillis = LocalDate.of(year, month + 1, dayOfMonth)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                userAction.invoke(OrienteeringCreatorAction.UpdateStageDate(index, dateMillis))
                focusManager.clearFocus()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    DSTextInput(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) datePickerDialog.show() },
        label = { Text(text = "Дата") },
        text = DateTimeFormat.transformLongToDisplayDate(date),
        readOnly = true
    )
}

/**
 * Компонент выбора времени (Stage).
 */
@Composable
private fun StageTimePicker(index: Int, date: Long, userAction: (OrienteeringCreatorAction) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    DSTextInput(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { showDialog = it.isFocused },
        label = { Text(text = "Время") },
        text = DateTimeFormat.transformLongToTime(date),
        readOnly = true
    )

    if (showDialog) {
        TimePickerDialog(
            onDismissRequest = {
                showDialog = false
                focusManager.clearFocus()
            },
            onConfirm = { hour, minute ->
                userAction.invoke(
                    OrienteeringCreatorAction.UpdateStageTime(index, "%02d:%02d".format(hour, minute))
                )
                showDialog = false
                focusManager.clearFocus()
            }
        )
    }
}

@Composable
private fun OrienteeringCompetitionDirection(
    state: OrienteeringCreatorState,
    userAction: (OrienteeringCreatorAction) -> Unit
) {
    val context = LocalContext.current
    ExposedDropdownMenuOutlined(
        label = stringResource(R.string.label_direction),
        items = OrienteeringDirection.entries,
        selectedItem = state.competitionDirection,
        onItemSelected = {
            userAction.invoke(OrienteeringCreatorAction.UpdateCompetitionDirection(it))
        },
        itemToString = {
            when(it) {
                OrienteeringDirection.FORWARD -> context.getString(R.string.label_direction_forward)
                OrienteeringDirection.BY_CHOICE -> context.getString(R.string.label_direction_by_choice)
                OrienteeringDirection.MARKING -> context.getString(R.string.label_direction_marking)
            }
        }
    )
}

/**
 * Компонент выбора режима времени старта.
 */
@Composable
private fun StartTimeModeSelector(
    state: OrienteeringCreatorState,
    userAction: (OrienteeringCreatorAction) -> Unit
) {
    val context = LocalContext.current
    Column {
        ExposedDropdownMenuOutlined(
            label = stringResource(R.string.label_start_time_mode),
            items = StartTimeMode.entries,
            selectedItem = state.startTimeMode,
            onItemSelected = {
                userAction.invoke(OrienteeringCreatorAction.UpdateStartTimeMode(it))
            },
            itemToString = {
                when (it) {
                    StartTimeMode.STRICT -> context.getString(R.string.label_start_time_mode_strict)
                    StartTimeMode.USER_SET -> context.getString(R.string.label_start_time_mode_user_set)
                    StartTimeMode.BY_START_STATION -> context.getString(R.string.label_start_time_mode_by_start_station)
                }
            }
        )
    }
}

/** Доступные значения интервала между стартами спортсменов (в секундах): от 20 до 180 с шагом 20. */
private val START_INTERVAL_OPTIONS = (1..9).map { it * 20 }

private fun formatIntervalSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return when {
        minutes == 0 -> "$secs сек"
        secs == 0 -> "$minutes мин"
        else -> "$minutes мин $secs сек"
    }
}

/** Системы отметки, для которых имеет смысл понятие электронной стартовой станции. */
private val ELECTRONIC_PUNCHING_SYSTEMS = setOf(
    PunchingSystem.SPORTIDUINO,
    PunchingSystem.SPORTIDENT,
    PunchingSystem.SFR
)

@Composable
private fun PunchingSystemSelector(
    state: OrienteeringCreatorState,
    userAction: (OrienteeringCreatorAction) -> Unit
) {
    val context = LocalContext.current
    val items = if (state.startTimeMode == StartTimeMode.BY_START_STATION) {
        PunchingSystem.entries.filter { it in ELECTRONIC_PUNCHING_SYSTEMS }
    } else {
        PunchingSystem.entries
    }
    ExposedDropdownMenuOutlined(
        label = stringResource(R.string.label_punching_system),
        items = items,
        selectedItem = state.punchingSystem,
        onItemSelected = {
            userAction.invoke(OrienteeringCreatorAction.UpdatePunchingSystem(it))
        },
        itemToString = {
            when (it) {
                PunchingSystem.PENCIL -> context.getString(R.string.label_punching_system_pencil)
                PunchingSystem.PUNCH -> context.getString(R.string.label_punching_system_punch)
                PunchingSystem.SPORTIDUINO -> context.getString(R.string.label_punching_system_sportiduino)
                PunchingSystem.SFR -> context.getString(R.string.label_punching_system_sfr)
                PunchingSystem.SPORTIDENT -> context.getString(R.string.label_punching_system_sportident)
            }
        }
    )
}

/**
 * Контрольное время (КВ) соревнования и политика его применения.
 *
 * КВ здесь — умолчание для всех групп; группа может переопределить его своим значением
 * (см. ParticipantGroupCreator). Селектор политики показывается только когда КВ задано:
 * без КВ выбирать нечего.
 */
@Composable
private fun ControlTimeBlock(
    state: OrienteeringCreatorState,
    userAction: (OrienteeringCreatorAction) -> Unit
) {
    val isByChoice = state.competitionDirection == OrienteeringDirection.BY_CHOICE
    val policies = if (isByChoice) {
        OvertimePolicy.entries
    } else {
        // Штраф очками осмыслен только в score-О: в остальных форматах очков нет.
        OvertimePolicy.entries.filter { it != OvertimePolicy.SCORE_PENALTY }
    }

    Column {
        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Контрольное время (мин)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            text = state.controlTimeMinutes?.toString() ?: "",
            onValueChanged = { raw ->
                userAction.invoke(
                    OrienteeringCreatorAction.UpdateControlTime(raw.filter { it.isDigit() }.toIntOrNull())
                )
            }
        )
        FieldDescription("За сколько минут участник должен уложиться. Группа может задать своё КВ")

        if (state.controlTimeMinutes != null) {
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            ExposedDropdownMenuOutlined(
                label = "При превышении КВ",
                items = policies,
                selectedItem = state.overtimePolicy,
                onItemSelected = { userAction.invoke(OrienteeringCreatorAction.UpdateOvertimePolicy(it)) },
                itemToString = {
                    when (it) {
                        OvertimePolicy.IGNORE -> "Не учитывать"
                        OvertimePolicy.DISQUALIFY -> "Дисквалифицировать"
                        OvertimePolicy.SCORE_PENALTY -> "Штраф очками"
                    }
                }
            )
            FieldDescription(
                when (state.overtimePolicy) {
                    OvertimePolicy.IGNORE ->
                        "КВ показывается участникам, но результаты засчитываются всем"
                    OvertimePolicy.DISQUALIFY ->
                        "Превысившие КВ снимаются и не получают места"
                    OvertimePolicy.SCORE_PENALTY ->
                        "Опоздание штрафуется очками — настраивается у каждой группы"
                }
            )
        }
    }
}

@Composable
private fun StartIntervalSelector(
    state: OrienteeringCreatorState,
    userAction: (OrienteeringCreatorAction) -> Unit
) {
    ExposedDropdownMenuOutlined(
        label = stringResource(R.string.label_start_interval),
        items = START_INTERVAL_OPTIONS,
        selectedItem = state.startIntervalSeconds,
        onItemSelected = {
            userAction.invoke(OrienteeringCreatorAction.UpdateStartInterval(it))
        },
        itemToString = { formatIntervalSeconds(it) }
    )
}

/**
 * Debug-блок инструментов разработчика на экране создания соревнования.
 *
 * Виден только в debug-сборке (определяется по флагу [android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE]).
 * Содержит:
 * - переключатель «Тестовое соревнование» — помечает соревнование как тестовое
 *   ([OrienteeringCreatorState.isTest]); на сервере такие скрыты из публичной ленты;
 * - кнопку «Заполнить тестовыми данными» — подставляет в форму преднабор
 *   ([TestCompetitionFixtures]) и включает флаг «тестовое».
 *
 * В релизной сборке блок не отрисовывается.
 */
@Composable
private fun DebugTestToolsBlock(
    state: OrienteeringCreatorState,
    onAction: (OrienteeringCreatorAction) -> Unit
) {
    val context = LocalContext.current
    val isDebuggable = remember {
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    if (!isDebuggable) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(Dimens.SIZE_BASE.dp)) {
            Text(
                text = "DEBUG · инструменты отладки",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Тестовое соревнование",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Скрыто из публичной ленты, видно только вам",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Switch(
                    checked = state.isTest,
                    onCheckedChange = { onAction(OrienteeringCreatorAction.UpdateIsTest(it)) }
                )
            }
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            Button(
                onClick = { onAction(OrienteeringCreatorAction.FillWithTestData) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Заполнить тестовыми данными")
            }
        }
    }
}

/**
 * Короткая подсказка под полем ввода, объясняющая его назначение участнику-организатору.
 * Используется для компонентов, у которых нет встроенного supportingText.
 */
@Composable
internal fun FieldDescription(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Dimens.SIZE_HALF.dp, top = 2.dp)
    )
}

/** Ключи savedStateHandle для передачи координат с экрана карты. */
internal const val MAP_RESULT_LAT = "map_result_lat"
internal const val MAP_RESULT_LON = "map_result_lon"

@Preview(showBackground = true, name = "Empty state")
@Composable
private fun CommonCompetitionFieldPreview() {
    MaterialTheme {
        CommonCompetitionFieldContent(
            state = OrienteeringCreatorState(),
            onAction = {},
            onTitleChanged = {},
            onAddressChanged = {},
            onDescriptionChanged = {},
            onOpenMap = {},
            onBack = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true, name = "Filled state")
@Composable
private fun CommonCompetitionFieldFilledPreview() {
    MaterialTheme {
        CommonCompetitionFieldContent(
            state = OrienteeringCreatorState(
                title = "Чемпионат по ориентированию",
                address = "Лесной массив, 5км",
                description = "Ежегодные соревнования для профессионалов и любителей."
            ),
            onAction = {},
            onTitleChanged = {},
            onAddressChanged = {},
            onDescriptionChanged = {},
            onOpenMap = {},
            onBack = {},
            onNext = {}
        )
    }
}

/**
 * Компонент выбора часового пояса соревнования.
 *
 * Использует системный список IANA-зон, по умолчанию подсвечивает текущий выбор.
 * Реализация — bottom sheet с поиском, чтобы не загромождать форму.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimeZonePicker(
    selectedZoneId: String,
    onSelect: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var showSheet by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val now = remember { java.time.Instant.now() }
    val allZones = remember { java.time.ZoneId.getAvailableZoneIds().sorted() }
    val filtered = remember(query, allZones) {
        if (query.isBlank()) allZones else allZones.filter { it.contains(query, ignoreCase = true) }
    }
    val displayLabel = remember(selectedZoneId) { zoneLabel(selectedZoneId, now) }

    DSTextInput(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) { showSheet = true; focusManager.clearFocus() } },
        label = { Text(text = "Часовой пояс") },
        text = displayLabel,
        readOnly = true,
        trailingIcon = {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_build_24px),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )

    if (showSheet) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Прокручиваем к выбранной зоне при открытии листа или сбросе поиска,
        // чтобы пользователь сразу видел текущий выбор и соседние зоны.
        LaunchedEffect(showSheet, query, selectedZoneId, filtered) {
            val idx = filtered.indexOf(selectedZoneId)
            if (idx >= 0) listState.scrollToItem(idx)
        }

        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SIZE_BASE.dp)
            ) {
                DSTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Поиск") },
                    text = query,
                    onValueChanged = { query = it }
                )
                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(filtered) { zone ->
                        Text(
                            text = zoneLabel(zone, now),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Dimens.SIZE_HALF.dp))
                                .background(
                                    if (zone == selectedZoneId) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable {
                                    onSelect(zone)
                                    showSheet = false
                                }
                                .padding(Dimens.SIZE_HALF.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Возвращает читаемую подпись зоны вида `Europe/Saratov (UTC+04:00)`.
 * Для зоны UTC (offset `Z`) подставляет `+00:00`, чтобы формат был единообразным.
 */
private fun zoneLabel(zoneId: String, instant: java.time.Instant): String = runCatching {
    val offset = java.time.ZoneId.of(zoneId).rules.getOffset(instant)
    val offsetStr = offset.id.takeIf { it != "Z" } ?: "+00:00"
    "$zoneId (UTC$offsetStr)"
}.getOrDefault(zoneId)
