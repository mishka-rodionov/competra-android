package com.competra.center.presentation.orientiring_competition_create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.NO_DISTANCE_ID
import com.competra.domain.models.orienteering.StandardGroups

/**
 * Диалог «Добавить стандартные группы»: организатор отмечает группы из [StandardGroups.TITLES]
 * и дистанцию по умолчанию. Пол и возраст каждой группы выводятся из названия, дистанция —
 * по совпадению названий с дистанциями (как обычно называет их Mapper), иначе — выбранная.
 *
 * @param distances дистанции соревнования; пустой список — группы создаются без дистанции.
 * @param existingTitles названия уже созданных групп — их нельзя отметить повторно.
 * @param onConfirm выбранные названия и id дистанции по умолчанию ([NO_DISTANCE_ID] — не назначена).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StandardGroupsDialog(
    distances: List<Distance>,
    existingTitles: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (titles: List<String>, defaultDistanceId: Long) -> Unit
) {
    val existing = remember(existingTitles) { existingTitles.map { it.trim().lowercase() }.toSet() }
    val available = remember(existing) { StandardGroups.TITLES.filterNot { it.lowercase() in existing } }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var defaultDistance by remember { mutableStateOf(distances.firstOrNull()) }
    var isDistanceMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Стандартные группы") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)) {
                    TextButton(onClick = { selected = available.toSet() }) { Text("Все") }
                    TextButton(onClick = { selected = emptySet() }) { Text("Снять") }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
                ) {
                    available.forEach { title ->
                        FilterChip(
                            selected = title in selected,
                            onClick = { selected = if (title in selected) selected - title else selected + title },
                            label = { Text(title) }
                        )
                    }
                }
                FieldDescription("Пол и возраст заполнятся по названию: Ж14 — до 14 лет, М35 — 35 лет и старше, М21 — без ограничения")

                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
                if (distances.isEmpty()) {
                    FieldDescription("Дистанций пока нет — группы будут без дистанции")
                    return@Column
                }
                Text(
                    text = "Дистанция по умолчанию",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Box {
                    OutlinedButton(onClick = { isDistanceMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(defaultDistance?.let { it.name ?: "Без названия" } ?: "Не назначена")
                    }
                    DropdownMenu(expanded = isDistanceMenuExpanded, onDismissRequest = { isDistanceMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Не назначена") },
                            onClick = {
                                defaultDistance = null
                                isDistanceMenuExpanded = false
                            }
                        )
                        distances.forEach { distance ->
                            DropdownMenuItem(
                                text = { Text(distance.name ?: "Без названия") },
                                onClick = {
                                    defaultDistance = distance
                                    isDistanceMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                FieldDescription("Если дистанция называется так же, как группа (например, «М21»), группа получит её")
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(StandardGroups.TITLES.filter { it in selected }, defaultDistance?.id ?: NO_DISTANCE_ID) },
                enabled = selected.isNotEmpty()
            ) {
                Text("Добавить (${selected.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
