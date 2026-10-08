package com.competra.center.presentation.orientiring_competition_create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.components.ExposedDropdownMenuOutlined
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.orienteering.TeamOverallScope
import com.competra.domain.models.orienteering.TeamScoring
import com.competra.domain.models.orienteering.TeamScoringMethod

/** Подпись общего командного зачёта. */
internal fun TeamOverallScope.label(): String = when (this) {
    TeamOverallScope.MEN -> "Мужчины"
    TeamOverallScope.WOMEN -> "Женщины"
    TeamOverallScope.ALL -> "Общий"
}

/**
 * Настройки командного зачёта соревнования (docs/specs/team-scoring.md): переключатель, способ
 * подсчёта в группе, N и общие зачёты. Зачёт задаётся без ссылок на группы, поэтому есть уже в
 * мастере создания. `null` — зачёта нет; включение подставляет умолчания (очки, N = 3).
 *
 * @param isScoreO score-О («по выбору» по баллам) — зачёт по времени там недоступен.
 */
@Composable
internal fun TeamScoringBlock(
    teamScoring: TeamScoring?,
    isScoreO: Boolean,
    onChange: (TeamScoring?) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Командный зачёт", style = MaterialTheme.typography.bodyLarge)
                FieldDescription("Команда — подпись участника в протоколе")
            }
            Switch(
                checked = teamScoring != null,
                onCheckedChange = { enabled -> onChange(if (enabled) TeamScoring() else null) }
            )
        }

        if (teamScoring == null) return@Column

        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
        val methods = if (isScoreO) listOf(TeamScoringMethod.POINTS) else TeamScoringMethod.entries
        ExposedDropdownMenuOutlined(
            label = "Зачёт в каждой группе",
            items = methods,
            selectedItem = teamScoring.groupMethod,
            onItemSelected = { onChange(teamScoring.copy(groupMethod = it)) },
            itemToString = {
                when (it) {
                    TeamScoringMethod.POINTS -> "По очкам за места"
                    TeamScoringMethod.TIME -> "По сумме времени"
                }
            }
        )
        FieldDescription(
            when (teamScoring.groupMethod) {
                TeamScoringMethod.POINTS ->
                    "Очки за место по таблице рейтинга (1-е — 100, 2-е — 80, 3-е — 60…), у команды складываются лучшие"
                TeamScoringMethod.TIME ->
                    "Складывается время лучших участников команды; если финишировало меньше — команда вне зачёта"
            }
        )

        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
        // Локальный текст: поле можно очистить и ввести новое число; в настройки уходит только N > 0.
        var countedText by remember { mutableStateOf(teamScoring.groupCountedResults.toString()) }
        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Сколько участников в зачёт") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            text = countedText,
            onValueChanged = { raw ->
                countedText = raw.filter { it.isDigit() }
                countedText.toIntOrNull()?.takeIf { it > 0 }?.let {
                    onChange(teamScoring.copy(groupCountedResults = it))
                }
            }
        )
        FieldDescription("Сколько лучших результатов команды в группе идёт в зачёт")

        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
        Text(text = "Общие зачёты", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)) {
            TeamOverallScope.entries.forEach { scope ->
                val selected = scope in teamScoring.overallScopes
                FilterChip(
                    selected = selected,
                    onClick = {
                        val scopes = if (selected) teamScoring.overallScopes - scope else teamScoring.overallScopes + scope
                        onChange(teamScoring.copy(overallScopes = scopes))
                    },
                    label = { Text(scope.label()) }
                )
            }
        }
        FieldDescription("Сумма баллов за места команды в группах: 1-е место в группе — 100, 2-е — 80…")
    }
}
