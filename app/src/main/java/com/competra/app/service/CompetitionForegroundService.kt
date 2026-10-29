package com.competra.app.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.app.R
import com.competra.ui.CompetitionStartTimeRepository
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Foreground Service для управления соревнованием.
 * Запускает секундомер в уведомлении, отслеживает стартовые времена участников
 * и воспроизводит звуковые сигналы.
 *
 * NFC-чипы сервис намеренно не слушает: ридер работает только пока приложение на экране,
 * а результат сохраняет экран «Сканировать» соревнования. Параллельная подписка здесь
 * лишь дублировала бы сканы (и перехватывала бы их у инструментов «Станции и чипы»).
 */
class CompetitionForegroundService : Service() {

    private val startAlertRepository: CompetitionStartAlertRepository by inject()
    private val startTimeRepository: CompetitionStartTimeRepository by inject()
    private val interactor: OrienteeringCompetitionInteractor by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var competitionId: String? = null
    private var startTimeMs: Long = 0L

    /** Следующий стартующий участник — обновляется мониторингом. */
    @Volatile private var nextStarterText: String? = null

    private var monitoringJob: Job? = null

    /**
     * Отдельный поток с аудиоприоритетом для цикла сигналов: общий пул [Dispatchers.IO]
     * могут занять синхронизация и БД, и тогда сигнал запоздает.
     */
    private val signalThreadLazy = lazy {
        HandlerThread("competition-start-signals").apply { start() }
    }
    private val signalThread by signalThreadLazy
    private val signalHandler by lazy {
        Handler(signalThread.looper).apply {
            // Повышенный приоритет — по возможности: на части прошивок он недоступен приложениям
            post { runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) } }
        }
    }
    private val signalDispatcher by lazy { signalHandler.asCoroutineDispatcher() }

    /** Используется только из [signalThread]. */
    private val signalPlayerLazy = lazy { StartSignalPlayer() }
    private val signalPlayer by signalPlayerLazy

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newCompetitionId = intent?.getStringExtra(EXTRA_COMPETITION_ID)
        val newStartTimeMs = intent?.getLongExtra(EXTRA_START_TIME_MS, 0L) ?: 0L

        // Обновляем поля только при наличии валидных значений в Intent.
        // Если Intent == null (edge-case при START_REDELIVER_INTENT), не загрязняем
        // startTimeRepository некорректным System.currentTimeMillis().
        if (!newCompetitionId.isNullOrEmpty()) competitionId = newCompetitionId
        if (newStartTimeMs > 0L) {
            startTimeMs = newStartTimeMs
            startTimeRepository.set(startTimeMs)
        }

        startForeground(NOTIFICATION_ID, buildNotification(""))

        if (!competitionId.isNullOrEmpty()) {
            launchParticipantMonitoring()
        }

        return START_REDELIVER_INTENT
    }

    // ─────────────────────────────────────── Monitoring participants ──

    private fun launchParticipantMonitoring() {
        val id = competitionId ?: return
        // Повторный onStartCommand не должен запускать второй цикл — иначе сигналы задваиваются.
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            val participants = interactor.getParticipants(id)
                .getOrNull()
                .orEmpty()
                .filter { it.startTime > 0 }
                .sortedBy { it.startTime }

            if (participants.isEmpty()) return@launch

            withContext(signalDispatcher) { monitorParticipants(participants) }
        }
    }

    /**
     * Цикл предстартовых сигналов. Не опрашивает время с фиксированным шагом, а просыпается
     * ровно на границах секунд обратного отсчёта до ближайшего слота — в эти моменты
     * звучат сигналы, меняется цифра в баннере и обновляется уведомление.
     */
    private suspend fun monitorParticipants(participants: List<OrienteeringParticipant>) {
        // Участники, стартующие в один и тот же момент (жеребьевка по дистанциям),
        // группируются по времени старта в единые стартовые слоты.
        val slots: List<StartSlot> = participants
            .groupBy { it.startTime }
            .toSortedMap()
            .map { (startTime, group) -> StartSlot(startTime, group) }

        // Синтез фрагментов занимает десятки мс — делаем сразу, а не в момент первого сигнала
        val player = signalPlayer

        // Слот, стартовавший в последние 2 секунды, ещё показываем как «GO».
        val serviceStartMs = System.currentTimeMillis()
        var nextIndex = slots.indexOfFirst { it.startTime > serviceStartMs - STARTED_ALERT_WINDOW_MS }
            .takeIf { it >= 0 } ?: slots.size
        var chimePlayedFor = -1L
        var countdownPlayedFor = -1L

        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()

            // Старт наступивших слотов: событие «GO» для UI — один раз на слот
            while (nextIndex < slots.size && slots[nextIndex].startTime <= now) {
                val slot = slots[nextIndex]
                if (now - slot.startTime < STARTED_ALERT_WINDOW_MS) {
                    // Сервис поднялся уже после «−3 с» — догоняем остаток отсчёта со стартовым сигналом
                    if (countdownPlayedFor != slot.startTime) {
                        countdownPlayedFor = slot.startTime
                        player.playCountdown(COUNTDOWN_SECONDS * 1000L + (now - slot.startTime))
                    }
                    startAlertRepository.emit(
                        ParticipantStartAlert.Started(starters = slot.toStarters())
                    )
                }
                nextIndex++
            }

            val nextSlot = slots.getOrNull(nextIndex)
            if (nextSlot == null) {
                nextStarterText = null
                updateNotification("")
                return
            }
            val nextNextSlot = slots.getOrNull(nextIndex + 1)

            val msUntilStart = nextSlot.startTime - now
            // ceil: на границе секунды N (msUntilStart == N*1000) значение уже N
            val secondsUntilStart = ceil(msUntilStart.toDouble() / 1000).toInt()

            // Звонок за 10 секунд — если проснулись вовремя (не при запуске сервиса посреди отсчёта)
            if (secondsUntilStart <= CHIME_SECONDS && chimePlayedFor != nextSlot.startTime) {
                chimePlayedFor = nextSlot.startTime
                if (CHIME_SECONDS * 1000L - msUntilStart <= MAX_CHIME_LATENESS_MS) {
                    player.playChime()
                }
            }

            // «3‑2‑1‑старт» одним фрагментом; опоздание компенсируется сдвигом внутри фрагмента
            if (secondsUntilStart <= COUNTDOWN_SECONDS && countdownPlayedFor != nextSlot.startTime) {
                countdownPlayedFor = nextSlot.startTime
                player.playCountdown(COUNTDOWN_SECONDS * 1000L - msUntilStart)
            }

            // Событие для UI-баннера в диапазоне 1..10 секунд
            // (0 не используется: при msUntilStart→0 слот переходит в стартовавшие → GO)
            if (secondsUntilStart in 1..CHIME_SECONDS) {
                startAlertRepository.emit(
                    ParticipantStartAlert.Upcoming(
                        starters = nextSlot.toStarters(),
                        countdownSeconds = secondsUntilStart,
                        nextStarters = nextNextSlot?.toStarters().orEmpty()
                    )
                )
            }

            // Уведомление — после звука и баннера, чтобы IPC не задерживал сигналы
            nextStarterText = buildNextStarterText(nextSlot, secondsUntilStart)
            updateNotification(nextStarterText.orEmpty())

            // Спим до следующей границы секунды отсчёта до ближайшего слота
            val untilBoundary = Math.floorMod(msUntilStart, 1000L).takeIf { it > 0L } ?: 1000L
            delay(untilBoundary + BOUNDARY_MARGIN_MS)
        }
    }

    /** Стартовый слот — участники с одинаковым временем старта. */
    private data class StartSlot(
        val startTime: Long,
        val participants: List<OrienteeringParticipant>
    ) {
        fun toStarters(): List<ParticipantStartAlert.Starter> = participants.map {
            ParticipantStartAlert.Starter(
                participantName = "${it.lastName} ${it.firstName}",
                startNumber = it.startNumber
            )
        }
    }

    /** Формирует строку уведомления для предстоящего стартового слота. */
    private fun buildNextStarterText(slot: StartSlot, secondsUntilStart: Int): String {
        val first = slot.participants.first()
        val extra = slot.participants.size - 1
        val who = if (extra > 0) {
            "№${first.startNumber} ${first.lastName} +$extra"
        } else {
            "№${first.startNumber} ${first.lastName}"
        }
        return if (secondsUntilStart in 0..10) {
            "СТАРТ: $who • ${secondsUntilStart}с"
        } else {
            val minutes = secondsUntilStart / 60
            val seconds = secondsUntilStart % 60
            "→ $who • %02d:%02d".format(minutes, seconds)
        }
    }

    // ─────────────────────────────────────────── Notification helpers ──

    private fun buildNotification(contentText: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Соревнование идёт")
            .setContentText(contentText)
            .setWhen(startTimeMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun updateNotification(contentText: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    // ──────────────────────────────────────────────────── Lifecycle ──

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        // Освобождаем плеер в его же потоке — после текущего шага цикла сигналов
        if (signalThreadLazy.isInitialized()) {
            signalHandler.post {
                if (signalPlayerLazy.isInitialized()) signalPlayer.release()
                signalThread.quitSafely()
            }
        }
        startTimeRepository.clear()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "competition_running"
        const val EXTRA_COMPETITION_ID = "competition_id"
        const val EXTRA_START_TIME_MS = "start_time_ms"

        /** За сколько секунд до старта звучит звонок и появляется баннер. */
        private const val CHIME_SECONDS = 10

        /** С какой секунды до старта идут короткие сигналы отсчёта. */
        private const val COUNTDOWN_SECONDS = 3

        /** Звонок, запоздавший больше чем на это время (запуск посреди отсчёта), не играется. */
        private const val MAX_CHIME_LATENESS_MS = 500L

        /** Сколько после старта слот ещё считается «только что стартовавшим». */
        private const val STARTED_ALERT_WINDOW_MS = 2000L

        /** Запас после границы секунды, чтобы округление уже дало новое значение. */
        private const val BOUNDARY_MARGIN_MS = 2L

        fun startIntent(context: Context, competitionId: String, startTimeMs: Long): Intent =
            Intent(context, CompetitionForegroundService::class.java).apply {
                putExtra(EXTRA_COMPETITION_ID, competitionId)
                putExtra(EXTRA_START_TIME_MS, startTimeMs)
            }
    }
}
