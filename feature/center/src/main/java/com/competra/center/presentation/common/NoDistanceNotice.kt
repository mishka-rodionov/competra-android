package com.competra.center.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.designsystem.theme.Dimens

/** Метка группы, которой ещё не назначена дистанция. */
@Composable
internal fun NoDistanceBadge(modifier: Modifier = Modifier) {
    Text(
        text = "без дистанции",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/**
 * Предупреждение о группах без дистанции. [message] — что именно из-за этого недоступно; по
 * умолчанию — общее напоминание организатору назначить дистанции до старта.
 */
@Composable
internal fun GroupsWithoutDistanceBanner(
    titles: List<String>,
    modifier: Modifier = Modifier,
    message: String = "Регистрация работает, но до старта назначьте дистанции — без них не провести " +
        "жеребьёвку по дистанциям, чтение чипов и публикацию результатов."
) {
    if (titles.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
    ) {
        Text(
            text = "${if (titles.size == 1) "У группы" else "У групп"} ${titles.joinToString()} не назначена дистанция. $message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(Dimens.SIZE_BASE.dp)
        )
    }
}
