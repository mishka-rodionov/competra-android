package com.competra.center.presentation.orientiring_competition_create

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.components.TimePickerDialog
import com.competra.designsystem.theme.Dimens
import com.competra.center.data.creator.OrienteeringCreatorAction
import com.competra.center.data.creator.OrienteeringCreatorState
import com.competra.domain.models.orienteering.RegistrationEndMode
import com.competra.resources.R
import com.competra.utils.DateTimeFormat
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * Второй экран создания соревнования: Регистрация.
 *
 * @param competitionId Идентификатор соревнования.
 * @param viewModel Вьюмодель процесса создания.
 */
@Composable
fun RegistrationCompetitionFieldScreen(
    competitionId: String,
    viewModel: OrienteeringCreatorViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize(competitionId)
    }

    RegistrationCompetitionFieldContent(
        state = state,
        onBack = viewModel::back,
        onNext = viewModel::saveStepTwo,
        onUpdateMaxParticipants = viewModel::updateMaxParticipants,
        onUpdateFeeEnabled = viewModel::updateFeeEnabled,
        onUpdateFeeAmount = viewModel::updateFeeAmount,
        onUpdateRegulationUrl = viewModel::updateRegulationUrl,
        onAction = viewModel::onAction
    )
}

/**
 * Контент экрана параметров регистрации.
 * Выделен отдельно для поддержки Preview.
 */
@Composable
private fun RegistrationCompetitionFieldContent(
    state: OrienteeringCreatorState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onUpdateMaxParticipants: (String) -> Unit,
    onUpdateFeeEnabled: (Boolean) -> Unit,
    onUpdateFeeAmount: (String) -> Unit,
    onUpdateRegulationUrl: (String) -> Unit,
    onAction: (OrienteeringCreatorAction) -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        bottomBar = {
            NavigationButtons(
                onBack = onBack,
                onNext = onNext
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
                text = "Шаг 2: Параметры регистрации",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            Text(
                text = "Начало регистрации",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "В момент создания соревнования",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = state.registrationStartOnCreate,
                    onCheckedChange = { onAction(OrienteeringCreatorAction.UpdateRegistrationStartOnCreate(it)) }
                )
            }
            FieldDescription("С какого момента участники смогут подавать заявки")

            val competitionZone = remember(state.timeZoneId) {
                runCatching { ZoneId.of(state.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
            }
            if (!state.registrationStartOnCreate) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        RegistrationDatePicker(
                            displayDate = state.registrationStart,
                            zoneId = competitionZone,
                            isError = state.errors.isEmptyRegistrationStart,
                            onDateSelected = { date ->
                                onAction(OrienteeringCreatorAction.UpdateRegistrationStartDate(date))
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        RegistrationTimePicker(
                            displayTime = state.registrationStartTimeStr,
                            isError = state.errors.isEmptyRegistrationStart,
                            onTimeSelected = { time ->
                                onAction(OrienteeringCreatorAction.UpdateRegistrationStartTime(time))
                            }
                        )
                    }
                }
                if (state.errors.isEmptyRegistrationStart) {
                    Text(
                        text = "Укажите дату и время начала регистрации",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = Dimens.SIZE_HALF.dp, top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            Text(
                text = "Окончание регистрации",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            FieldDescription("Когда приём заявок будет закрыт")
            RegistrationEndModeOption(
                label = "В момент старта соревнования",
                selected = state.registrationEndMode == RegistrationEndMode.AT_COMPETITION_START,
                onSelect = {
                    onAction(OrienteeringCreatorAction.UpdateRegistrationEndMode(RegistrationEndMode.AT_COMPETITION_START))
                }
            )
            RegistrationEndModeOption(
                label = "За сутки до старта",
                selected = state.registrationEndMode == RegistrationEndMode.DAY_BEFORE_START,
                onSelect = {
                    onAction(OrienteeringCreatorAction.UpdateRegistrationEndMode(RegistrationEndMode.DAY_BEFORE_START))
                }
            )
            // Досрочно закрытая организатором регистрация. Выбор другого режима откроет её снова.
            if (state.isRegistrationClosedByOrganizer) {
                RegistrationEndModeOption(
                    label = "Регистрация завершена организатором",
                    selected = state.registrationEndMode == RegistrationEndMode.CLOSED_BY_ORGANIZER,
                    onSelect = {
                        onAction(OrienteeringCreatorAction.UpdateRegistrationEndMode(RegistrationEndMode.CLOSED_BY_ORGANIZER))
                    }
                )
            }

            if (false) { // на данном этапе отключено
                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                DSTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    text = state.maxParticipants?.toString() ?: "",
                    label = { Text("Лимит участников") },
                    onValueChanged = onUpdateMaxParticipants
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            // Секция взноса со свитчем
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Платное участие",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = state.isFeeEnabled,
                    onCheckedChange = onUpdateFeeEnabled
                )
            }
            FieldDescription("Включите, если для участия требуется оплата стартового взноса")

            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                DSTextInput(
                    modifier = Modifier.weight(1f),
                    text = state.feeAmount?.toString() ?: "",
                    label = { Text("Взнос") },
                    supportingText = { Text("Сумма взноса с одного участника") },
                    enabled = state.isFeeEnabled, // Активно только если включен свитч
                    onValueChanged = onUpdateFeeAmount
                )
                Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))
                DSTextInput(
                    modifier = Modifier.weight(0.5f),
                    text = state.feeCurrency,
                    label = { Text("Валюта") },
                    supportingText = { Text("RUB, USD, EUR…") },
                    enabled = state.isFeeEnabled, // Активно только если включен свитч
                    onValueChanged = { /* viewModel.updateCurrency(it) */ }
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

            if (false) { //на данном этапе отключено
                DSTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    text = state.regulationUrl,
                    label = { Text("Ссылка на регламент") },
                    onValueChanged = onUpdateRegulationUrl
                )
            }
        }
    }
}

/**
 * Строка с радио‑кнопкой для выбора режима окончания регистрации.
 * Кликабельна по всей ширине: тап по тексту или по самой кнопке.
 */
@Composable
private fun RegistrationEndModeOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Пикер даты для полей регистрации. Отображает переданную дату и вызывает [onDateSelected]
 * с выбранным значением в миллисекундах.
 */
@Composable
private fun RegistrationDatePicker(
    displayDate: Long?,
    zoneId: ZoneId,
    isError: Boolean = false,
    onDateSelected: (Long) -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val focusManager = LocalFocusManager.current

    val datePickerDialog = remember(zoneId) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val dateMillis = LocalDate.of(year, month + 1, dayOfMonth)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
                onDateSelected(dateMillis)
                focusManager.clearFocus()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    datePickerDialog.setOnDismissListener { focusManager.clearFocus() }

    DSTextInput(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) datePickerDialog.show() },
        label = { Text("Дата") },
        text = DateTimeFormat.transformLongToDisplayDate(displayDate, zoneId),
        readOnly = true,
        isError = isError,
        trailingIcon = {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_date_range_24px),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}

/**
 * Пикер времени для полей регистрации. Отображает переданную строку времени и вызывает
 * [onTimeSelected] с выбранным значением в формате "HH:mm".
 */
@Composable
private fun RegistrationTimePicker(
    displayTime: String,
    isError: Boolean = false,
    onTimeSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    DSTextInput(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { showDialog = it.isFocused },
        label = { Text("Время") },
        text = displayTime,
        readOnly = true,
        isError = isError,
        trailingIcon = {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_build_24px),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )

    if (showDialog) {
        TimePickerDialog(
            onDismissRequest = {
                showDialog = false
                focusManager.clearFocus()
            },
            onConfirm = { hour, minute ->
                onTimeSelected("%02d:%02d".format(hour, minute))
                showDialog = false
                focusManager.clearFocus()
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegistrationCompetitionFieldPreview() {
    MaterialTheme {
        RegistrationCompetitionFieldContent(
            state = OrienteeringCreatorState(
                maxParticipants = 200,
                isFeeEnabled = true,
                feeAmount = 500.0,
                feeCurrency = "RUB",
                regulationUrl = "https://example.com/rules"
            ),
            onBack = {},
            onNext = {},
            onUpdateMaxParticipants = {},
            onUpdateFeeEnabled = {},
            onUpdateFeeAmount = {},
            onUpdateRegulationUrl = {},
            onAction = {}
        )
    }
}
