package com.competra.eventdetails.presentation.registration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.domain.models.cyclic_event.TeamSuggestion
import com.competra.eventdetails.data.registration.RegistrationTeamState

/**
 * Поле «Клуб/команда» в шторке регистрации: свободный текст с выпадающими подсказками
 * (свои клубные команды, подписи из протокола) и подсказкой вступить в клуб с таким же названием.
 * @param state Состояние поля.
 * @param onCommandNameChange Пользователь изменил текст.
 * @param onSuggestionSelect Выбрана подсказка из выпадающего списка.
 * @param onOpenClub Открыть карточку клуба (идентификатор клуба).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationTeamField(
    state: RegistrationTeamState,
    onCommandNameChange: (String) -> Unit,
    onSuggestionSelect: (TeamSuggestion) -> Unit,
    onOpenClub: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = state.teamSuggestions
    val selectedOption = state.selectedTeamOption

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = state.commandName,
            onValueChange = {
                expanded = true
                onCommandNameChange(it)
            },
            label = { Text("Клуб/команда (необязательно)") },
            supportingText = when {
                selectedOption?.teamId != null -> {
                    { Text("Команда вашего клуба") }
                }
                selectedOption != null -> {
                    { Text("Ваш клуб") }
                }
                else -> null
            },
            trailingIcon = {
                if (suggestions.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(text = suggestion.label)
                            Text(
                                text = when (suggestion) {
                                    is TeamSuggestion.Own ->
                                        if (suggestion.option.teamId != null) "Ваша команда" else "Ваш клуб"
                                    is TeamSuggestion.Protocol -> "Уже есть в протоколе"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSuggestionSelect(suggestion)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }

    state.clubMatches.firstOrNull()?.let { club ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (club.allowJoinRequests) {
                        "Клуб «${club.name}» есть в Competra — можно подать заявку на вступление"
                    } else {
                        "Клуб «${club.name}» есть в Competra"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onOpenClub(club.id) }) {
                    Text("Открыть")
                }
            }
        }
    }
}
