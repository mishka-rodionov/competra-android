package com.competra.app.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private val toneGenerator: ToneGenerator? by lazy {
        try { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }
        catch (_: Exception) { null }
    }

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
        serviceScope.launch {
            val participants = interactor.getParticipants(id)
                .getOrNull()
                .orEmpty()
                .filter { it.startTime > 0 }
                .sortedBy { it.startTime }

            if (participants.isEmpty()) return@launch

            monitorParticipants(participants)
        }
    }

    private suspend fun CoroutineScope.monitorParticipants(
        participants: List<OrienteeringParticipant>
    ) {
        // Участники, стартующие в один и тот же момент (жеребьевка по дистанциям),
        // группируются по времени старта в единые стартовые слоты.
        val slots: List<StartSlot> = participants
            .groupBy { it.startTime }
            .toSortedMap()
            .map { (startTime, group) -> StartSlot(startTime, group) }

        val playedSounds = mutableSetOf<String>()
        var lastNotificationSecond = -1L

        while (isActive) {
            val now = System.currentTimeMillis()

            // Слоты, которые ещё не стартовали (по возрастанию времени)
            val upcomingSlots = slots.filter { it.startTime > now }

            // Слот, стартовавший в последние 2 секунды
            val justStartedSlot = slots.firstOrNull {
                it.startTime <= now && it.startTime > now - 2000
            }

            // Звук старта (длинный сигнал) и событие для UI — один раз на весь слот
            if (justStartedSlot != null) {
                val key = "${justStartedSlot.startTime}_started"
                if (playedSounds.add(key)) {
                    playLongBeep()
                    startAlertRepository.emit(
                        ParticipantStartAlert.Started(starters = justStartedSlot.toStarters())
                    )
                }
            }

            val nextSlot = upcomingSlots.firstOrNull()
            val nextNextSlot = upcomingSlots.getOrNull(1)

            if (nextSlot != null) {
                val msUntilStart = nextSlot.startTime - now
                // ceil обеспечивает равномерность: seconds=N срабатывает когда msUntilStart ≤ N*1000,
                // что гарантирует ровно ~1000ms между сигналами независимо от фазы polling
                val secondsUntilStart = ceil(msUntilStart.toDouble() / 1000).toInt()

                // Короткие звуковые сигналы за 10, 5, 4, 3, 2, 1 секунды — один раз на слот
                val soundTargets = setOf(10, 5, 4, 3, 2, 1)
                if (secondsUntilStart in soundTargets) {
                    val key = "${nextSlot.startTime}_$secondsUntilStart"
                    if (playedSounds.add(key)) playShortBeep()
                }

                // Событие для UI-баннера в диапазоне 1..10 секунд
                // (0 не используется: при msUntilStart→0 слот переходит в justStarted → GO)
                if (secondsUntilStart in 1..10) {
                    startAlertRepository.emit(
                        ParticipantStartAlert.Upcoming(
                            starters = nextSlot.toStarters(),
                            countdownSeconds = secondsUntilStart,
                            nextStarters = nextNextSlot?.toStarters().orEmpty()
                        )
                    )
                }

                // Строка для уведомления
                nextStarterText = buildNextStarterText(nextSlot, secondsUntilStart)
            } else {
                nextStarterText = null
            }

            // Обновляем уведомление раз в секунду
            val currentSecond = now / 1000
            if (currentSecond != lastNotificationSecond) {
                lastNotificationSecond = currentSecond
                val notifText = nextStarterText ?: ""
                updateNotification(notifText)
            }

            delay(200)
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

    // ─────────────────────────────────────────────── Sound helpers ──

    private fun playShortBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
    }

    private fun playLongBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 900)
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
        toneGenerator?.release()
        serviceScope.cancel()
        startTimeRepository.clear()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "competition_running"
        const val EXTRA_COMPETITION_ID = "competition_id"
        const val EXTRA_START_TIME_MS = "start_time_ms"

        fun startIntent(context: Context, competitionId: String, startTimeMs: Long): Intent =
            Intent(context, CompetitionForegroundService::class.java).apply {
                putExtra(EXTRA_COMPETITION_ID, competitionId)
                putExtra(EXTRA_START_TIME_MS, startTimeMs)
            }
    }
}
