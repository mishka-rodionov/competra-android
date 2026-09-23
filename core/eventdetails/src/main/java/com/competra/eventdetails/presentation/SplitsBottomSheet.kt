package com.competra.eventdetails.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.ParticipantWithResult
import com.competra.domain.models.orienteering.SplitTime
import com.competra.utils.orienteering.toRaceTime
import com.competra.utils.orienteering.toSplitTime

/**
 * Форматирует время прохождения участника (или код статуса DSQ/DNF/DNS). Для BY_CHOICE (score-О)
 * тоже возвращает время — приоритет в определении победителя там у баллов ([formatResultScore]),
 * но время финиша всё равно показывается рядом.
 */
internal fun formatResultTime(result: OrienteeringResult?): String {
    if (result == null) return "—"
    return when (result.status) {
        ResultStatus.FINISHED -> result.totalTime?.toRaceTime() ?: "—"
        ResultStatus.OVERTIME -> "КВ"
        ResultStatus.DSQ -> "DSQ"
        ResultStatus.DNF -> "DNF"
        ResultStatus.DNS -> "DNS"
        else -> result.status.name
    }
}

/**
 * Форматирует сумму баллов участника для формата "по выбору" (BY_CHOICE), со штрафом за
 * опоздание в скобках, если он есть.
 */
internal fun formatResultScore(result: OrienteeringResult?): String {
    if (result == null) return "—"
    val score = result.totalScore ?: 0
    return if (result.scorePenalty > 0) "$score очков (-${result.scorePenalty})" else "$score очков"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SplitsBottomSheet(
    participantWithResult: ParticipantWithResult,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val participant = participantWithResult.participant
    val result = participantWithResult.result

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SIZE_BASE.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "${participant.lastName} ${participant.firstName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = participant.groupName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp, bottom = Dimens.SIZE_BASE.dp)
            )

            if (result != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Dimens.SIZE_BASE.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Статус", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = formatResultTime(result), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    val rank = result.rank
                    if (rank != null && rank > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Место", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = rank.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val splits = result.splits
                if (!splits.isNullOrEmpty()) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                    Text(
                        text = "Сплиты",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = Dimens.SIZE_HALF.dp)
                    )
                    SplitsTable(participant = participant, splits = splits)
                } else {
                    Text(
                        text = "Сплиты отсутствуют",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.SIZE_HALF.dp)
                    )
                }
            } else {
                Text(
                    text = "Результат пока не зафиксирован",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun SplitsTable(participant: OrienteeringParticipant, splits: List<SplitTime>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text = "КП", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(text = "Сплит", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(text = "Время", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
    HorizontalDivider(thickness = 0.5.dp)
    splits.forEachIndexed { index, split ->
        val splitMs = if (index == 0) split.timestamp - participant.startTime
                      else split.timestamp - splits[index - 1].timestamp
        val totalMs = split.timestamp - participant.startTime
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(text = split.controlPoint.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(text = splitMs.toSplitTime(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
            Text(text = totalMs.toSplitTime(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        }
        if (index < splits.size - 1) {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}
