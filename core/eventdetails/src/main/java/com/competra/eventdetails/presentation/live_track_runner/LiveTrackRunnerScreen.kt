package com.competra.eventdetails.presentation.live_track_runner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.core.tracking.LiveTrackRecorderSnapshot
import com.competra.domain.models.livetrack.LiveTrackCloseReason
import com.competra.domain.models.livetrack.LiveTrackStatus
import com.competra.domain.models.livetrack.RunnerTrackSession
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.TimeUnit

/** Фикс старше этого считается потерей сигнала GPS. */
private val GPS_STALE_MS = TimeUnit.SECONDS.toMillis(30)

/**
 * Экран онлайн-трека бегуна: идёт ли запись, есть ли GPS и связь, сколько точек ждёт отправки.
 * Карты и позиции нет намеренно — по правилам ориентирования телефон не должен быть навигатором.
 *
 * @param eventId Идентификатор соревнования.
 */
@Composable
fun LiveTrackRunnerScreen(
    eventId: String,
    viewModel: LiveTrackRunnerViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(eventId) {
        viewModel.initialize(eventId)
    }

    LiveTrackRunnerContent(state = state, onAction = viewModel::onAction)

    if (state.isStopConfirmVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(LiveTrackRunnerAction.DismissStop) },
            title = { Text("Остановить трек?") },
            text = { Text("Запись прекратится, уже записанные точки будут отправлены. Включить трек заново можно на странице соревнования.") },
            confirmButton = { TextButton(onClick = { viewModel.onAction(LiveTrackRunnerAction.ConfirmStop) }) { Text("Остановить") } },
            dismissButton = { TextButton(onClick = { viewModel.onAction(LiveTrackRunnerAction.DismissStop) }) { Text("Продолжить запись") } }
        )
    }
}

@Composable
private fun LiveTrackRunnerContent(
    state: LiveTrackRunnerState,
    onAction: (LiveTrackRunnerAction) -> Unit
) {
    val session = state.session
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Онлайн-трек", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

            if (session == null) {
                Text("Трек для этого соревнования не включён.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }

            StatusCard(state, session)

            if (session.isRecording && !state.isInterrupted) {
                InfoRow("Запись идёт", formatDuration(state.now - session.startedAt))
                InfoRow("GPS", gpsText(state.recorder, state.now))
            }
            InfoRow("Передача", uploadText(state, session))

            Spacer(Modifier.height(8.dp))

            when {
                state.isInterrupted -> Button(
                    onClick = { onAction(LiveTrackRunnerAction.ResumeRecording) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Продолжить запись") }

                session.isRecording -> OutlinedButton(
                    onClick = { onAction(LiveTrackRunnerAction.StopClick) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Остановить трек") }
            }

            Text(
                "Ваш трек видят зрители соревнования. Карту и своё положение во время забега вы не видите — таковы правила.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusCard(state: LiveTrackRunnerState, session: RunnerTrackSession) {
    val (title, subtitle) = statusTexts(state, session)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Заголовок и пояснение карточки статуса. */
private fun statusTexts(state: LiveTrackRunnerState, session: RunnerTrackSession): Pair<String, String?> = when {
    state.isInterrupted -> "Запись прервана" to "Телефон остановил приложение. Нажмите «Продолжить запись», если вы ещё на дистанции."
    session.isRecording -> "Идёт запись трека" to "Можно убрать телефон — запись продолжится в фоне."
    session.stopRequested && !session.stopDelivered -> "Трек остановлен" to "Досылаем записанные точки на сервер."
    else -> when (session.closeReason) {
        LiveTrackCloseReason.RESULT_SAVED -> "Финиш зафиксирован" to "Трек сохранён в архиве соревнования."
        LiveTrackCloseReason.MANUAL -> "Трек остановлен" to "Трек сохранён в архиве соревнования."
        LiveTrackCloseReason.CONTROL_TIME -> "Трек закрыт" to "Истекло время на прохождение дистанции."
        LiveTrackCloseReason.INACTIVITY -> "Трек закрыт" to "Долго не поступали данные."
        LiveTrackCloseReason.SESSION_LOST -> "Трек недоступен" to "Сервер не нашёл эту запись. Включите трек заново на странице соревнования."
        null -> if (session.status == LiveTrackStatus.ACTIVE) "Трек остановлен" to null else "Трек закрыт" to null
    }
}

private fun gpsText(recorder: LiveTrackRecorderSnapshot, now: Long): String {
    if (!recorder.gpsAvailable) return "Выключен или нет разрешения — включите геолокацию"
    val fixAt = recorder.lastFixAt ?: return "Поиск спутников…"
    val age = now - fixAt
    if (age > GPS_STALE_MS) return "Нет сигнала ${formatAgo(age)}"
    val accuracy = recorder.lastFixAccuracy?.let { " • точность ${it.toInt()} м" }.orEmpty()
    return "Сигнал есть$accuracy"
}

private fun uploadText(state: LiveTrackRunnerState, session: RunnerTrackSession): String {
    val queued = if (state.pendingPoints > 0) " • в очереди ${state.pendingPoints} точек" else ""
    val lastUploadAt = state.recorder.lastUploadAt
    // После окончания записи «Отправлено N назад» читается как продолжающаяся передача — показываем итог.
    val isDraining = state.pendingPoints > 0 || (session.stopRequested && !session.stopDelivered)
    return when {
        session.isRecording -> when {
            state.recorder.lastUploadFailed -> "Нет связи — точки сохраняются в телефоне$queued"
            lastUploadAt != null -> "Отправлено ${formatAgo(state.now - lastUploadAt)} назад$queued"
            else -> "Ожидание первой отправки$queued"
        }
        !isDraining -> "Все данные отправлены"
        state.recorder.lastUploadFailed -> "Досылка при появлении сети$queued"
        else -> "Досылаем записанные точки$queued"
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatAgo(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return if (sec < 60) "$sec с" else "${sec / 60} мин"
}

@Preview(showBackground = true)
@Composable
private fun LiveTrackRunnerRecordingPreview() {
    val now = System.currentTimeMillis()
    MaterialTheme {
        LiveTrackRunnerContent(
            state = LiveTrackRunnerState(
                session = RunnerTrackSession(
                    sessionId = "s", competitionId = "c", status = LiveTrackStatus.ACTIVE, closeReason = null,
                    startedAt = now - 754_000, deadlineAt = now + 3_600_000, uploadIntervalSec = 10,
                    lastAckedBatchSeq = 12, stopRequested = false, stopDelivered = false
                ),
                pendingPoints = 14,
                recorder = LiveTrackRecorderSnapshot(
                    recordingSessionId = "s", lastFixAt = now - 2_000, lastFixAccuracy = 6f,
                    lastUploadAt = now - 45_000, lastUploadFailed = true
                ),
                now = now
            ),
            onAction = {}
        )
    }
}
