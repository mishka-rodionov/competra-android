package com.competra.center.presentation.chip_inspect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.center.data.chip_inspect.ChipInspectState
import com.competra.center.data.chip_inspect.ChipScan
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.orienteering.SplitTime
import com.competra.resources.R
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

// Служебные номера станций Sportiduino (см. com.competra.nfchelper.nfccard.Config).
private const val START_STATION = 240
private const val FINISH_STATION = 245
private const val CHECK_STATION = 248
private const val CLEAR_STATION = 249

private val CleanColor = Color(0xFF2E7D32)
private val PunchesColor = Color(0xFFE65100)

/**
 * Экран «Считать / Проверить»: показывает содержимое любого приложенного чипа — номер, время
 * очистки, отметки — и вердикт, готов ли чип к старту. Работает без соревнования.
 */
@Composable
fun ChipInspectScreen(viewModel: ChipInspectViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        ChipInspectContent(state)
    }
}

@Composable
private fun ChipInspectContent(state: ChipInspectState) {
    when (val scan = state.lastScan) {
        null -> WaitingView()
        is ChipScan.Participant -> ParticipantChipView(scan, state.scanCount)
        is ChipScan.Master -> MasterCardView(scan, state.scanCount)
    }
}

@Composable
private fun WaitingView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SIZE_DOUBLE.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_check_24px),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
        Text(
            text = "Ожидание чипа",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Приложите чип участника или мастер-карту к устройству. Данные только " +
                "показываются и никуда не сохраняются.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ParticipantChipView(scan: ChipScan.Participant, scanCount: Int) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.SIZE_BASE.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
    ) {
        item {
            VerdictCard(
                title = if (scan.isClean) "Чип чистый" else "На чипе ${scan.punches.size} ${punchesWord(scan.punches.size)}",
                subtitle = if (scan.isClean) "Готов к старту" else "Перед стартом чип нужно очистить",
                color = if (scan.isClean) CleanColor else PunchesColor,
                icon = if (scan.isClean) R.drawable.ic_check_24px else R.drawable.ic_info_24px
            )
        }
        item {
            InfoCard(
                rows = listOf(
                    "Номер чипа" to scan.chipNumber.toString(),
                    "Очищен" to formatDateTime(scan.clearTime),
                    "Считан" to "${formatTime(scan.scannedAt)} (скан №$scanCount)"
                )
            )
        }
        if (!scan.isClean) {
            item {
                Text(
                    text = "Отметки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = Dimens.SIZE_HALF.dp)
                )
            }
            itemsIndexed(scan.punches) { index, punch ->
                PunchRow(index = index + 1, punch = punch)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MasterCardView(scan: ChipScan.Master, scanCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SIZE_BASE.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
    ) {
        VerdictCard(
            title = "Мастер-карта",
            subtitle = "Это служебная карта станции, а не чип участника",
            color = MaterialTheme.colorScheme.primary,
            icon = R.drawable.ic_build_24px
        )
        InfoCard(
            rows = listOf(
                "Содержимое" to scan.description,
                "Считана" to "${formatTime(scan.scannedAt)} (скан №$scanCount)"
            )
        )
    }
}

@Composable
private fun VerdictCard(title: String, subtitle: String, color: Color, icon: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SIZE_BASE.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(Dimens.SIZE_BASE.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
        ) {
            rows.forEach { (label, value) ->
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun PunchRow(index: Int, punch: SplitTime) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SIZE_HALF.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
        Text(
            text = controlPointLabel(punch.controlPoint),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(text = formatTime(punch.timestamp), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun controlPointLabel(cp: Int): String = when (cp) {
    START_STATION -> "Старт"
    FINISH_STATION -> "Финиш"
    CHECK_STATION -> "Проверка"
    CLEAR_STATION -> "Очистка"
    else -> "КП $cp"
}

private fun punchesWord(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "отметок"
        mod10 == 1 -> "отметка"
        mod10 in 2..4 -> "отметки"
        else -> "отметок"
    }
}

private fun formatTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeFormatter)

private fun formatDateTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)

// region Previews

@Preview(name = "Ожидание чипа", showBackground = true)
@Composable
private fun ChipInspectWaitingPreview() {
    MaterialTheme { ChipInspectContent(ChipInspectState()) }
}

@Preview(name = "Чистый чип", showBackground = true)
@Composable
private fun ChipInspectCleanPreview() {
    MaterialTheme {
        ChipInspectContent(
            ChipInspectState(
                lastScan = ChipScan.Participant(
                    chipNumber = 123,
                    clearTime = 1700000000000L,
                    punches = emptyList(),
                    scannedAt = 1700003600000L
                ),
                scanCount = 1
            )
        )
    }
}

@Preview(name = "Чип с отметками", showBackground = true)
@Composable
private fun ChipInspectPunchesPreview() {
    MaterialTheme {
        ChipInspectContent(
            ChipInspectState(
                lastScan = ChipScan.Participant(
                    chipNumber = 123,
                    clearTime = 1700000000000L,
                    punches = listOf(
                        SplitTime(controlPoint = START_STATION, timestamp = 1700000060000L),
                        SplitTime(controlPoint = 31, timestamp = 1700000300000L),
                        SplitTime(controlPoint = 32, timestamp = 1700000500000L),
                        SplitTime(controlPoint = FINISH_STATION, timestamp = 1700000900000L),
                    ),
                    scannedAt = 1700003600000L
                ),
                scanCount = 3
            )
        )
    }
}

// endregion
