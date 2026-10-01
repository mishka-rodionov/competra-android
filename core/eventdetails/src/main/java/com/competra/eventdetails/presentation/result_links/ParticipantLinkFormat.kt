package com.competra.eventdetails.presentation.result_links

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.competra.domain.models.participant_link.LinkRequestStatus
import com.competra.domain.models.participant_link.LinkResultSummary
import com.competra.utils.orienteering.toRaceTime

/** Подпись статуса заявки на привязку — те же тексты, что в веб-клиенте. */
internal fun LinkRequestStatus.label(): String = when (this) {
    LinkRequestStatus.PENDING -> "На рассмотрении"
    LinkRequestStatus.APPROVED -> "Привязан"
    LinkRequestStatus.REJECTED -> "Отклонена"
    LinkRequestStatus.CANCELLED -> "Отозвана"
    LinkRequestStatus.UNLINKED -> "Отвязан"
}

@Composable
internal fun LinkRequestStatus.color(): Color = when (this) {
    LinkRequestStatus.APPROVED -> MaterialTheme.colorScheme.primary
    LinkRequestStatus.REJECTED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** «5 место · 0:45:12», «120 оч. · 0:58:30» или статус схода — чтобы человек узнал свой старт. */
internal fun LinkResultSummary?.label(): String? {
    if (this == null) return null
    if (status != "FINISHED") return statusShortLabel(status)
    val parts = buildList {
        rank?.takeIf { it > 0 }?.let { add("$it место") }
        totalScore?.let { add("$it оч.") }
        totalTime?.let { add(it.toRaceTime()) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: statusShortLabel(status)
}

private fun statusShortLabel(status: String): String = when (status) {
    "FINISHED" -> "Финиш"
    "DNF" -> "НФ"
    "DNS" -> "НС"
    "OVERTIME" -> "Прев. КВ"
    "DSQ" -> "Дискв."
    else -> status
}

/** Строка описания участника: «Семенов Петр · М21 · 5 место · 0:45:12». */
internal fun linkDetails(vararg parts: String?): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

/**
 * Подтверждение перед отправкой заявки: организатор проверит, отмечать только свои результаты.
 *
 * @param lines Описания заявляемых результатов, по одному на строку.
 */
@Composable
internal fun LinkRequestConfirmDialog(
    lines: List<String>,
    isSending: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = {
            Text(
                if (lines.size == 1) "Привязать результат к профилю?"
                else "Привязать результаты (${lines.size}) к профилю?"
            )
        },
        text = {
            Text(
                lines.joinToString("\n") +
                    "\n\nОрганизатор соревнования проверит заявку. Отмечайте только свои результаты."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSending) {
                Text(if (isSending) "Отправка…" else "Отправить заявку")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("Отмена") }
        },
    )
}
