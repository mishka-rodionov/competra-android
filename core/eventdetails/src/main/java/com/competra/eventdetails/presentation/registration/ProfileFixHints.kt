package com.competra.eventdetails.presentation.registration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.competra.domain.models.cyclic_event.ProfileFix

/**
 * Подсказка в шторке регистрации, что поправить в профиле, чтобы открылись недоступные группы.
 * Незаполненные пол/дата рождения — пояснение и кнопка; только несовпадение пола — ненавязчивая
 * строка (сервер отдаёт MALE тем, кто пол не указывал, — женщине нужен путь в профиль).
 * Ничего не показывает, если профиль не поможет.
 * @param fixes Что можно сделать в профиле для каждой недоступной группы.
 * @param onOpenProfile Переход в профиль.
 */
@Composable
fun RegistrationProfileHint(
    fixes: Collection<ProfileFix>,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val missing = listOfNotNull(
        "пол".takeIf { ProfileFix.ADD_GENDER in fixes },
        "дату рождения".takeIf { ProfileFix.ADD_BIRTH_DATE in fixes }
    )
    when {
        missing.isNotEmpty() -> Column(modifier = modifier) {
            Text(
                text = "Чтобы открыть все группы, укажите в профиле ${missing.joinToString(" и ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenProfile) {
                Text(text = "Открыть профиль")
            }
        }
        ProfileFix.CHECK_GENDER in fixes -> CheckGenderHint(onOpenProfile = onOpenProfile, modifier = modifier)
    }
}

/**
 * Ненавязчивая строка «Пол в профиле указан неверно? Исправить» — когда группа не подходит по полу.
 * @param onOpenProfile Переход в профиль.
 */
@Composable
fun CheckGenderHint(onOpenProfile: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Пол в профиле указан неверно?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onOpenProfile) {
            Text(text = "Исправить")
        }
    }
}
