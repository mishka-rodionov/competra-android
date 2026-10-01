package com.competra.center.presentation.station_tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.center.data.station_tools.StationToolsAction
import com.competra.designsystem.components.clickRipple
import com.competra.designsystem.theme.Dimens
import com.competra.resources.R
import org.koin.compose.viewmodel.koinViewModel

/**
 * Хаб «Станции и чипы»: работа с оборудованием Sportiduino без привязки к соревнованию —
 * чтение/проверка, очистка и запись чипов, настройка станций мастер-картами.
 */
@Composable
fun StationToolsScreen(viewModel: StationToolsViewModel = koinViewModel()) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        StationToolsContent(onAction = viewModel::onAction)
    }
}

@Composable
private fun StationToolsContent(onAction: (StationToolsAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.SIZE_BASE.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
    ) {
        Text(
            text = "Станции и чипы",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Инструменты для работы с оборудованием Sportiduino. Не требуют соревнования " +
                "и не меняют его результаты.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

        ToolItem(
            title = "Считать / Проверить",
            description = "Номер чипа, время очистки и отметки. Показывает, готов ли чип к старту.",
            icon = R.drawable.ic_check_24px,
            color = Color(0xFF2196F3),
            onClick = { onAction(StationToolsAction.OpenInspect) }
        )
        ToolItem(
            title = "Очистить чип",
            description = "Стереть отметки с чипа участника, сохранив его номер.",
            icon = R.drawable.delete,
            color = Color(0xFF4CAF50),
            onClick = { onAction(StationToolsAction.OpenClear) }
        )
        ToolItem(
            title = "Записать номер",
            description = "Записать новый номер на чип участника.",
            icon = R.drawable.edit,
            color = Color(0xFFF44336),
            onClick = { onAction(StationToolsAction.OpenWriteNumber) }
        )
        ToolItem(
            title = "Настроить станцию",
            description = "Мастер-карты: время, номер, конфигурация, сон, пароль, состояние станции.",
            icon = R.drawable.ic_build_24px,
            color = Color(0xFFFFC107),
            onClick = { onAction(StationToolsAction.OpenStationSetup) }
        )
    }
}

@Composable
private fun ToolItem(
    title: String,
    description: String,
    icon: Int,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickRipple(onClick = onClick),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SIZE_BASE.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(icon),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_chevron_forward_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "Станции и чипы", showBackground = true)
@Composable
private fun StationToolsPreview() {
    MaterialTheme {
        StationToolsContent(onAction = {})
    }
}
