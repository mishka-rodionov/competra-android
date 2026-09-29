package com.competra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.SplitsTable
import com.competra.domain.models.orienteering.SplitsTableCell
import com.competra.domain.models.orienteering.SplitsTableRow
import com.competra.domain.models.orienteering.displayPlace
import com.competra.utils.orienteering.toPace
import com.competra.utils.orienteering.toRaceTime

private val NAME_COLUMN_WIDTH = 140.dp
private val SPLIT_COLUMN_WIDTH = 76.dp

/**
 * Таблица сплитов группы: участники — строки, КП — колонки со sticky первой колонкой
 * (участник) и sticky-заголовком. Переиспользуется экранами сплитов и в `:feature:center`,
 * и в `:core:eventdetails` — источники данных у них разные, рендер общий.
 */
@Composable
fun GroupSplitsTableContent(
    groupTitle: String,
    table: SplitsTable,
    direction: OrienteeringDirection = OrienteeringDirection.FORWARD,
) {
    val isByChoice = direction == OrienteeringDirection.BY_CHOICE
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = groupTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(Dimens.SIZE_BASE.dp),
        )

        val horizontalScrollState = rememberScrollState()

        // Sticky-заголовок: не находится внутри LazyColumn, поэтому не скроллится вертикально.
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderCell(
                text = "Участник",
                modifier = Modifier.width(NAME_COLUMN_WIDTH),
                textAlign = TextAlign.Start,
            )
            Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                table.columns.forEach { column ->
                    HeaderCell(
                        text = if (isByChoice) "#${column.positionIndex}" else "#${column.positionIndex} (КП${column.controlPoint})",
                        modifier = Modifier.width(SPLIT_COLUMN_WIDTH),
                    )
                }
                if (isByChoice) {
                    HeaderCell(text = "Дистанция", modifier = Modifier.width(SPLIT_COLUMN_WIDTH))
                }
            }
        }
        HorizontalDivider()

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(table.rows) { index, row ->
                val rowBackground = if (index % 2 == 0) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBackground),
                ) {
                    // Sticky первая колонка: находится вне горизонтально скроллящегося Row.
                    ParticipantCell(row = row, isByChoice = isByChoice, modifier = Modifier.width(NAME_COLUMN_WIDTH))
                    Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                        row.cells.forEach { cell ->
                            SplitCell(cell = cell, isByChoice = isByChoice, modifier = Modifier.width(SPLIT_COLUMN_WIDTH))
                        }
                        if (isByChoice) {
                            DistanceCell(row = row, modifier = Modifier.width(SPLIT_COLUMN_WIDTH))
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Center) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = Dimens.SIZE_QUARTER.dp, vertical = Dimens.SIZE_HALF.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
    )
}

@Composable
private fun ParticipantCell(row: SplitsTableRow, isByChoice: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = Dimens.SIZE_HALF.dp, vertical = Dimens.SIZE_QUARTER.dp)) {
        Text(
            text = "${row.participant.lastName} ${row.participant.firstName}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
        )
        if (isByChoice) {
            Text(
                text = scoreLabel(row),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.result?.displayPlace?.let { rank ->
            Text(
                text = "Место $rank",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun scoreLabel(row: SplitsTableRow): String {
    val netScore = row.result?.totalScore ?: 0
    val penalty = row.result?.scorePenalty ?: 0
    val rawScore = row.rawScore ?: (netScore + penalty)
    return if (penalty > 0) "$rawScore - $penalty (штраф) = $netScore" else "$netScore очков"
}

@Composable
private fun DistanceCell(row: SplitsTableRow, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Dimens.SIZE_QUARTER.dp, vertical = Dimens.SIZE_QUARTER.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = row.totalDistanceMeters?.let { formatDistanceKm(it) } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatDistanceKm(meters: Double): String = "%.1f км".format(meters / 1000.0)

@Composable
private fun SplitCell(cell: SplitsTableCell, isByChoice: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Dimens.SIZE_QUARTER.dp, vertical = Dimens.SIZE_QUARTER.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isByChoice) {
            cell.controlPoint?.let {
                Text(
                    text = "КП$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = cell.cumulativeSeconds?.toRaceTime() ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = cell.deltaSeconds?.toRaceTime() ?: "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (cell.isBestLeg) FontWeight.Bold else FontWeight.Normal,
            color = if (cell.isBestLeg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (!isByChoice) {
            cell.paceMinPerKm?.let { pace ->
                Text(
                    text = "${pace.toPace()} /км",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
