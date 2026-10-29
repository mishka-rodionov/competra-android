package com.competra.center.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.center.data.refresh.RemoteRefreshState
import com.competra.resources.R
import com.competra.utils.DateTimeFormat

/**
 * Неблокирующий статус фонового обновления данных с сервера: тонкая полоса прогресса во время
 * загрузки и время последнего обновления. Без связи — спокойный серый статус «Офлайн» с иконкой,
 * а не ошибка: в офлайне можно полноценно работать, изменения уйдут на сервер при появлении сети,
 * а данные с сервера приложение дотянет само (или по жесту pull-to-refresh).
 * Ничего не показывает, пока обновлений ещё не было.
 *
 * @param state Состояние фонового обновления.
 */
@Composable
fun RefreshStatusBar(
    state: RemoteRefreshState,
    modifier: Modifier = Modifier
) {
    val updatedAt = state.lastUpdatedAt?.let { DateTimeFormat.transformLongToTime(it) }
    val isOffline = state.isFailed && !state.isRefreshing
    val text = when {
        state.isRefreshing -> "Обновляем данные…"
        state.isFailed -> "Офлайн · " + (updatedAt?.let { "данные от $it" } ?: "данные из телефона")
        updatedAt != null -> "Обновлено в $updatedAt"
        else -> return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (state.isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOffline) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_cloud_off_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RefreshStatusBarOfflinePreview() {
    RefreshStatusBar(
        state = RemoteRefreshState(lastUpdatedAt = System.currentTimeMillis(), isFailed = true)
    )
}
