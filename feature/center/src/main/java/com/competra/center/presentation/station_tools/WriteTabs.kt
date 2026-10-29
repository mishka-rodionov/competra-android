package com.competra.center.presentation.station_tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.competra.center.data.station_tools.StationToolsAction
import com.competra.center.data.station_tools.StationToolsResult
import com.competra.center.data.station_tools.StationToolsState
import com.competra.center.data.station_tools.StationToolsTab
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.theme.Dimens
import com.competra.resources.R

private val SuccessContainer = Color(0xFF4CAF50).copy(alpha = 0.15f)
private val SuccessContent = Color(0xFF2E7D32)

/**
 * Строка статуса NFC на вкладках записи: ожидание чипа (в том числе серия пакетного режима
 * со счётчиком), итог последней операции или подсказка о только что считанном чипе.
 */
@Composable
internal fun NfcStatusCard(
    state: StationToolsState,
    modifier: Modifier = Modifier
) {
    val result = state.lastResult
    if (!state.isWaitingForChip && result == null) return

    val containerColor: Color
    val contentColor: Color
    val title: String
    var details: List<String> = emptyList()
    var icon: Int? = null
    when {
        state.isWaitingForChip -> {
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            title = when {
                state.selectedTab == StationToolsTab.WRITE_NUMBER ->
                    "Приложите чип — будет записан №${state.chipNumberInput}"
                state.isBatchRunning -> "Очистка подряд: прикладывайте чипы по одному"
                else -> "Приложите чип к устройству"
            }
            if (state.isBatchRunning) {
                details = listOfNotNull("Обработано: ${state.batchProcessedCount}", result?.message)
            }
        }
        result is StationToolsResult.Success -> {
            containerColor = SuccessContainer
            contentColor = SuccessContent
            icon = R.drawable.ic_check_24px
            title = result.message
            if (state.batchProcessedCount > 0) details = listOf("Обработано подряд: ${state.batchProcessedCount}")
        }
        result is StationToolsResult.Info -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            icon = R.drawable.ic_info_24px
            title = result.message
        }
        else -> {
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
            icon = R.drawable.close_24px
            title = result?.message ?: "Ошибка"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SIZE_BASE.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.isWaitingForChip) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else if (icon != null) {
                Icon(
                    imageVector = ImageVector.vectorResource(icon),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    fontWeight = FontWeight.Medium
                )
                details.forEach {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = contentColor)
                }
            }
        }
    }
}

/** Вкладка «Очистить»: разовая очистка или очистка подряд. */
@Composable
internal fun ClearChipTab(
    state: StationToolsState,
    onAction: (StationToolsAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SIZE_BASE.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_BASE.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(Dimens.SIZE_BASE.dp)) {
                Text(
                    text = "Очистка чипа",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Сбрасывает все отметки участника. Timestamp очистки будет записан на чип. Номер участника обнуляется.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        BatchSwitchRow(
            title = "Очищать подряд",
            subtitle = "Каждый приложенный чип очищается, пока не нажмёте «Остановить»",
            checked = state.isBatchMode,
            onCheckedChange = { onAction(StationToolsAction.BatchModeToggled(it)) }
        )

        StartStopButton(
            isWaiting = state.isWaitingForChip,
            startText = if (state.isBatchMode) "Начать очистку" else "Очистить чип",
            startEnabled = true,
            onStart = { onAction(StationToolsAction.StartClearChip) },
            onStop = { onAction(StationToolsAction.StopWaiting) }
        )
    }
}

/** Вкладка «Номер»: запись номера на чип, подряд — с автоувеличением номера. */
@Composable
internal fun WriteNumberTab(
    state: StationToolsState,
    onAction: (StationToolsAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SIZE_BASE.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_BASE.dp)
    ) {
        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            text = state.chipNumberInput,
            onValueChanged = { onAction(StationToolsAction.ChipNumberChanged(it)) },
            enabled = !state.isWaitingForChip,
            label = { Text("Номер участника (1–65535)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Fast Punch", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Режим быстрой отметки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.fastPunch,
                enabled = !state.isWaitingForChip,
                onCheckedChange = { onAction(StationToolsAction.FastPunchToggled(it)) }
            )
        }

        BatchSwitchRow(
            title = "Записывать подряд",
            subtitle = "После каждого чипа номер увеличивается на 1",
            checked = state.isBatchMode,
            onCheckedChange = { onAction(StationToolsAction.BatchModeToggled(it)) }
        )

        StartStopButton(
            isWaiting = state.isWaitingForChip,
            startText = if (state.isBatchMode) "Начать запись" else "Записать на чип",
            startEnabled = state.chipNumberInput.isNotEmpty(),
            onStart = { onAction(StationToolsAction.StartWriteNumber) },
            onStop = { onAction(StationToolsAction.StopWaiting) }
        )
    }
}

@Composable
private fun BatchSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Главная кнопка вкладки записи: «старт», а пока ждём чип — «Остановить». */
@Composable
private fun StartStopButton(
    isWaiting: Boolean,
    startText: String,
    startEnabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
    if (isWaiting) {
        OutlinedButton(onClick = onStop, modifier = modifier, shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)) {
            Text("Остановить", fontWeight = FontWeight.Bold)
        }
    } else {
        Button(
            onClick = onStart,
            modifier = modifier,
            enabled = startEnabled,
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
        ) {
            Text(startText, fontWeight = FontWeight.Bold)
        }
    }
}

/** Вкладка «Станция»: запись мастер-карт для настройки станций Sportiduino. */
@Composable
internal fun StationTab(
    state: StationToolsState,
    onAction: (StationToolsAction) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.SIZE_BASE.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_BASE.dp)
    ) {
        item {
            StationCard(title = "Синхронизировать время") {
                Text(
                    text = "Записывает текущее системное время на карту управления.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                StationButton(
                    text = "Синхронизировать время",
                    enabled = !state.isWaitingForChip,
                    onClick = { onAction(StationToolsAction.StartSetStationTime) }
                )
            }
        }

        item {
            StationCard(title = "Установить номер станции") {
                DSTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    text = state.stationNumberInput,
                    onValueChanged = { onAction(StationToolsAction.StationNumberChanged(it)) },
                    label = { Text("Номер станции (1–255)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                StationButton(
                    text = "Установить номер",
                    enabled = !state.isWaitingForChip && state.stationNumberInput.isNotEmpty(),
                    onClick = { onAction(StationToolsAction.StartSetStationNumber) }
                )
            }
        }

        item {
            StationCard(title = "Конфигурация") {
                Text(
                    text = "Записывает конфигурацию с номером станции из поля выше. Режим активности: 2 ч, усиление антенны: 33 дБ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                StationButton(
                    text = "Записать конфигурацию",
                    enabled = !state.isWaitingForChip,
                    onClick = { onAction(StationToolsAction.StartSetStationConfig) }
                )
            }
        }

        item {
            StationCard(title = "Режим сна") {
                SleepTimePicker(
                    wakeUpMs = state.wakeUpDateTimeMs,
                    onTimeChanged = { onAction(StationToolsAction.WakeUpTimeChanged(it)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                StationButton(
                    text = "Отправить в сон",
                    enabled = !state.isWaitingForChip,
                    onClick = { onAction(StationToolsAction.StartSleep) }
                )
            }
        }

        item {
            StationCard(title = "Получить состояние") {
                Text(
                    text = "Записывает карту GET_STATE. Поднесите карту к станции — станция запишет своё состояние, затем считайте карту.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                StationButton(
                    text = "Получить состояние",
                    enabled = !state.isWaitingForChip,
                    onClick = { onAction(StationToolsAction.StartGetState) }
                )
            }
        }

        item {
            StationCard(title = "Сменить пароль") {
                PasswordInputRow(
                    password = state.newPassword,
                    onChanged = { p0, p1, p2 -> onAction(StationToolsAction.NewPasswordChanged(p0, p1, p2)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                StationButton(
                    text = "Записать пароль",
                    enabled = !state.isWaitingForChip,
                    onClick = { onAction(StationToolsAction.StartSetPassword) }
                )
            }
        }
    }
}

@Composable
private fun StationCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(Dimens.SIZE_BASE.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun StationButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
    ) {
        Text(text, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SleepTimePicker(
    wakeUpMs: Long,
    onTimeChanged: (Long) -> Unit
) {
    val calendar = remember(wakeUpMs) {
        java.util.Calendar.getInstance().apply { timeInMillis = wakeUpMs }
    }
    val dateText = remember(wakeUpMs) {
        String.format(
            "%02d.%02d.%04d %02d:%02d",
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Разбудить в", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dateText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        Row {
            OutlinedButton(
                onClick = { onTimeChanged(wakeUpMs - 3_600_000L) },
                modifier = Modifier.size(40.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("−1ч", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedButton(
                onClick = { onTimeChanged(wakeUpMs + 3_600_000L) },
                modifier = Modifier.size(40.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("+1ч", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PasswordInputRow(
    password: Triple<Int, Int, Int>,
    onChanged: (Int, Int, Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PasswordByteField(
            label = "Байт 0",
            value = password.first,
            modifier = Modifier.weight(1f)
        ) { onChanged(it, password.second, password.third) }

        PasswordByteField(
            label = "Байт 1",
            value = password.second,
            modifier = Modifier.weight(1f)
        ) { onChanged(password.first, it, password.third) }

        PasswordByteField(
            label = "Байт 2",
            value = password.third,
            modifier = Modifier.weight(1f)
        ) { onChanged(password.first, password.second, it) }
    }
}

@Composable
private fun PasswordByteField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onChanged: (Int) -> Unit
) {
    DSTextInput(
        modifier = modifier,
        text = value.toString(),
        onValueChanged = { text ->
            val v = text.toIntOrNull()
            if (v != null && v in 0..255) onChanged(v)
            else if (text.isEmpty()) onChanged(0)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
