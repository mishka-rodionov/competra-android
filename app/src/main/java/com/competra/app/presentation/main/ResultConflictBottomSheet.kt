package com.competra.app.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.ResultConflictEvent

/**
 * Bottom sheet, отображаемый поверх всех экранов при повторном сканировании чипа участника,
 * для которого уже есть результат в БД.
 *
 * @param event   Событие конфликта с данными существующего и нового результата.
 * @param onApply Вызывается при нажатии «Применить».
 * @param onCancel Вызывается при нажатии «Отмена» или закрытии жестом.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultConflictBottomSheet(
    event: ResultConflictEvent,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            // Заголовок
            Text(
                text = "Повторное сканирование",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.participantName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Таблица изменений
            val diffs = buildDiffList(event.existingResult, event.newResult)

            if (diffs.none { it.hasChanged }) {
                Text(
                    text = "Данные не изменились",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                // Заголовок таблицы
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Поле",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = "Было",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Стало",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                diffs.filter { it.hasChanged }.forEach { diff ->
                    DiffRow(diff)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text("Отмена")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onApply) {
                    Text("Применить")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DiffRow(diff: ResultFieldDiff) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = diff.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.2f)
        )
        Text(
            text = diff.oldValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = diff.newValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class ResultFieldDiff(
    val label: String,
    val oldValue: String,
    val newValue: String,
    val hasChanged: Boolean
)

private fun buildDiffList(
    existing: OrienteeringResult,
    new: OrienteeringResult
): List<ResultFieldDiff> {
    return listOf(
        ResultFieldDiff(
            label = "Статус",
            oldValue = existing.status.label(),
            newValue = new.status.label(),
            hasChanged = existing.status != new.status
        ),
        ResultFieldDiff(
            label = "Общее время",
            oldValue = existing.totalTime?.formatRaceTime() ?: "—",
            newValue = new.totalTime?.formatRaceTime() ?: "—",
            hasChanged = existing.totalTime != new.totalTime
        ),
        ResultFieldDiff(
            label = "Финиш",
            oldValue = existing.finishTime?.formatRaceTime() ?: "—",
            newValue = new.finishTime?.formatRaceTime() ?: "—",
            hasChanged = existing.finishTime != new.finishTime
        ),
        ResultFieldDiff(
            label = "Штраф",
            oldValue = existing.penaltyTime.formatRaceTime(),
            newValue = new.penaltyTime.formatRaceTime(),
            hasChanged = existing.penaltyTime != new.penaltyTime
        ),
        ResultFieldDiff(
            label = "КП (сплиты)",
            oldValue = "${existing.splits?.size ?: 0} шт.",
            newValue = "${new.splits?.size ?: 0} шт.",
            hasChanged = existing.splits?.size != new.splits?.size
        )
    )
}

private fun ResultStatus.label(): String = when (this) {
    ResultStatus.FINISHED -> "Финиш"
    ResultStatus.OVERTIME -> "Превышено КВ"
    ResultStatus.DSQ -> "Снят"
    ResultStatus.DNS -> "Не стартовал"
    ResultStatus.DNF -> "Сошёл"
    ResultStatus.REGISTERED -> "Зарегистрирован"
    ResultStatus.STARTED -> "На дистанции"
}

/** Форматирует миллисекунды в строку "HH:MM:SS". */
private fun Long.formatRaceTime(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultConflictBottomSheetPreview() {
    val existing = OrienteeringResult(
        id = 1,
        competitionId = "1",
        groupId = 1,
        participantId = "id1",
        status = ResultStatus.FINISHED,
        totalTime = 3600000L, // 1 hour
        finishTime = 10000000L,
        penaltyTime = 0L,
        splits = emptyList()
    )
    val new = existing.copy(
        status = ResultStatus.FINISHED,
        totalTime = 3540000L, // 59 minutes
        penaltyTime = 60000L  // 1 minute penalty
    )
    val event = ResultConflictEvent(
        participantName = "Иван Иванов",
        existingResult = existing,
        newResult = new,
        onApply = {}
    )

    MaterialTheme {
        Surface {
            ResultConflictBottomSheet(
                event = event,
                onApply = {},
                onCancel = {}
            )
        }
    }
}
