package com.competra.center.presentation.write_chip

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.competra.center.data.write_chip.WriteChipAction
import com.competra.center.data.write_chip.WriteChipOperationResult
import com.competra.center.data.write_chip.WriteChipState
import com.competra.center.data.write_chip.WriteChipTab
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.theme.Dimens
import com.competra.resources.R
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран записи/очистки чипов участников и настройки станций мастер-картами.
 *
 * @param initialTab вкладка, открываемая первой; `null` — вкладка по умолчанию.
 */
@Composable
fun WriteChipScreen(
    initialTab: WriteChipTab? = null,
    viewModel: WriteChipViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(initialTab) {
        initialTab?.let { viewModel.onAction(WriteChipAction.ApplyInitialTab(it)) }
    }
    // Режим записи живёт, только пока экран на виду: при уходе с него (назад, другая вкладка
    // нижней навигации) NFC возвращается в режим чтения.
    DisposableEffect(viewModel) {
        onDispose { viewModel.onAction(WriteChipAction.ScreenHidden) }
    }
    WriteChipContent(state = state, onAction = viewModel::onAction)
}

@Composable
private fun WriteChipContent(
    state: WriteChipState,
    onAction: (WriteChipAction) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            Tab(
                selected = state.selectedTab == WriteChipTab.CLEAR,
                onClick = { onAction(WriteChipAction.SelectTab(WriteChipTab.CLEAR)) },
                text = { Text("Очистить") }
            )
            Tab(
                selected = state.selectedTab == WriteChipTab.WRITE_NUMBER,
                onClick = { onAction(WriteChipAction.SelectTab(WriteChipTab.WRITE_NUMBER)) },
                text = { Text("Записать номер") }
            )
            Tab(
                selected = state.selectedTab == WriteChipTab.STATION,
                onClick = { onAction(WriteChipAction.SelectTab(WriteChipTab.STATION)) },
                text = { Text("Станции") }
            )
        }

        NfcStatusCard(
            isWaiting = state.isWaitingForChip,
            result = state.lastResult,
            modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp, vertical = 8.dp)
        )

        when (state.selectedTab) {
            WriteChipTab.CLEAR -> ClearChipTab(
                isWaiting = state.isWaitingForChip,
                onClear = { onAction(WriteChipAction.StartClearChip) }
            )
            WriteChipTab.WRITE_NUMBER -> WriteNumberTab(
                state = state,
                onAction = onAction
            )
            WriteChipTab.STATION -> StationTab(
                state = state,
                onAction = onAction
            )
        }
    }
}

@Composable
private fun NfcStatusCard(
    isWaiting: Boolean,
    result: WriteChipOperationResult?,
    modifier: Modifier = Modifier
) {
    if (!isWaiting && result == null) return

    val (containerColor, contentColor, icon, text) = when {
        isWaiting -> listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            null,
            "Приложите чип к устройству"
        )
        result is WriteChipOperationResult.Success -> listOf(
            Color(0xFF4CAF50).copy(alpha = 0.15f),
            Color(0xFF2E7D32),
            R.drawable.ic_check_24px,
            result.message
        )
        else -> listOf(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            R.drawable.close_24px,
            (result as? WriteChipOperationResult.Error)?.message ?: "Ошибка"
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor as Color)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SIZE_BASE.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isWaiting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = contentColor as Color
                )
            } else if (icon != null) {
                Icon(
                    imageVector = ImageVector.vectorResource(icon as Int),
                    contentDescription = null,
                    tint = contentColor as Color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = text as String,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor as Color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ClearChipTab(
    isWaiting: Boolean,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SIZE_BASE.dp),
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

        Button(
            onClick = onClear,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isWaiting,
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Очистить чип", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WriteNumberTab(
    state: WriteChipState,
    onAction: (WriteChipAction) -> Unit
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
            onValueChanged = { onAction(WriteChipAction.ChipNumberChanged(it)) },
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
                onCheckedChange = { onAction(WriteChipAction.FastPunchToggled(it)) }
            )
        }

        Button(
            onClick = { onAction(WriteChipAction.StartWriteNumber) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !state.isWaitingForChip && state.chipNumberInput.isNotEmpty(),
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
        ) {
            Text("Записать на чип", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StationTab(
    state: WriteChipState,
    onAction: (WriteChipAction) -> Unit
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
                    onClick = { onAction(WriteChipAction.StartSetStationTime) }
                )
            }
        }

        item {
            StationCard(title = "Установить номер станции") {
                DSTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    text = state.stationNumberInput,
                    onValueChanged = { onAction(WriteChipAction.StationNumberChanged(it)) },
                    label = { Text("Номер станции (1–255)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                StationButton(
                    text = "Установить номер",
                    enabled = !state.isWaitingForChip && state.stationNumberInput.isNotEmpty(),
                    onClick = { onAction(WriteChipAction.StartSetStationNumber) }
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
                    onClick = { onAction(WriteChipAction.StartSetStationConfig) }
                )
            }
        }

        item {
            StationCard(title = "Режим сна") {
                SleepTimePicker(
                    wakeUpMs = state.wakeUpDateTimeMs,
                    onTimeChanged = { onAction(WriteChipAction.WakeUpTimeChanged(it)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                StationButton(
                    text = "Отправить в сон",
                    enabled = !state.isWaitingForChip,
                    onClick = { onAction(WriteChipAction.StartSleep) }
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
                    onClick = { onAction(WriteChipAction.StartGetState) }
                )
            }
        }

        item {
            StationCard(title = "Сменить пароль") {
                PasswordInputRow(
                    password = state.newPassword,
                    onChanged = { p0, p1, p2 -> onAction(WriteChipAction.NewPasswordChanged(p0, p1, p2)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                StationButton(
                    text = "Записать пароль",
                    enabled = !state.isWaitingForChip,
                    onClick = { onAction(WriteChipAction.StartSetPassword) }
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
