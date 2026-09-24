package com.competra.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.competra.app.R
import com.competra.core.tracking.GpsLocationSource
import com.competra.core.tracking.LiveTrackEngine
import com.competra.core.tracking.LiveTrackFlushWorker
import com.competra.core.tracking.LiveTrackRecorderState
import com.competra.core.tracking.UploadStep
import com.competra.domain.models.livetrack.RunnerTrackPoint
import com.competra.domain.models.livetrack.RunnerTrackSession
import com.competra.domain.repository.livetrack.LiveTrackLocalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Foreground-сервис онлайн-трекинга бегуна на соревновании: пишет GPS в буфер (Room) и
 * последовательно отправляет батчи через [LiveTrackEngine].
 *
 * Бегуну не показывается ни карта, ни позиция — только статус записи в уведомлении и на экране.
 *
 * Жизненный цикл: пишет, пока сессия активна и бегун не нажал «Стоп»; когда запись закончилась
 * (стоп, закрытие сервером по результату/таймауту), прекращает GPS, досылает буфер не дольше
 * [DRAIN_BUDGET_MS] и останавливается — недосланное уходит через [LiveTrackFlushWorker].
 * После убийства системой перезапускается (`START_STICKY`) и продолжает незаконченную сессию из Room.
 */
class CompetitionTrackingService : Service() {

    private val engine: LiveTrackEngine by inject()
    private val local: LiveTrackLocalRepository by inject()
    private val recorderState: LiveTrackRecorderState by inject()
    private val gps: GpsLocationSource by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private var sessionId: String? = null
    private var workJob: Job? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var recordingEndedAt: Long? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground — первым делом: после startForegroundService у сервиса всего несколько секунд.
        if (!promoteToForeground()) {
            LiveTrackFlushWorker.enqueue(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val requestedId = intent?.getStringExtra(EXTRA_SESSION_ID)
        serviceScope.launch {
            if (intent?.action == ACTION_STOP && requestedId != null) {
                engine.requestStop(requestedId)
            }
            val id = requestedId ?: local.findUnfinishedSession()?.sessionId
            if (id == null) finish() else begin(id)
        }
        return START_STICKY
    }

    private fun promoteToForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(recording = true), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        true
    } catch (e: Exception) {
        // Нет разрешения на геолокацию или запрет старта foreground-сервиса из фона.
        Log.w(TAG, "Cannot start foreground location service", e)
        false
    }

    /** Запускает (или продолжает) запись и отправку для сессии; повтор для той же сессии — no-op. */
    private suspend fun begin(id: String) {
        if (sessionId == id && workJob?.isActive == true) {
            refreshRecording(local.getSession(id))
            return
        }
        workJob?.cancel()
        sessionId = id
        recordingEndedAt = null
        refreshRecording(local.getSession(id))
        // Сессия уже не пишет (перезапуск ради досылки) — отсчёт времени на досылку идёт сразу.
        if (!isRecording) recordingEndedAt = System.currentTimeMillis()
        workJob = serviceScope.launch {
            val result = engine.runUploads(id) {
                // Пока пишем — отправляем всегда; после окончания записи — не дольше DRAIN_BUDGET_MS.
                val endedAt = recordingEndedAt ?: return@runUploads true
                System.currentTimeMillis() - endedAt < DRAIN_BUDGET_MS
            }
            if (result != UploadStep.DONE) LiveTrackFlushWorker.enqueue(this@CompetitionTrackingService)
            finish()
        }
        serviceScope.launch { watchSession(id) }
    }

    /** Раз в несколько секунд сверяется с Room: запись могла закончиться (стоп, сервер закрыл сессию). */
    private suspend fun watchSession(id: String) {
        while (workJob?.isActive == true && sessionId == id) {
            refreshRecording(local.getSession(id))
            delay(WATCH_INTERVAL_MS)
        }
    }

    private suspend fun refreshRecording(session: RunnerTrackSession?) {
        val shouldRecord = session?.isRecording == true
        if (shouldRecord == isRecording) return
        isRecording = shouldRecord
        withContext(Dispatchers.Main) {
            if (shouldRecord && session != null) {
                val started = gps.start(MIN_TIME_MS, MIN_DISTANCE_M) { location -> onLocation(session.sessionId, location) }
                recorderState.onRecordingStarted(session.sessionId, gpsAvailable = started)
            } else {
                gps.stop()
                recorderState.onRecordingStopped()
                recordingEndedAt = System.currentTimeMillis()
            }
            notificationManager.notify(NOTIFICATION_ID, buildNotification(recording = shouldRecord))
        }
    }

    private fun onLocation(id: String, location: Location) {
        if (location.accuracy > MAX_ACCURACY_M) return
        val point = RunnerTrackPoint(
            t = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            lat = location.latitude,
            lon = location.longitude,
            accuracy = location.accuracy
        )
        serviceScope.launch { engine.recordPoint(id, point) }
    }

    private suspend fun finish() {
        withContext(Dispatchers.Main) {
            gps.stop()
            isRecording = false
            recorderState.onRecordingStopped()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(recording: Boolean) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(if (recording) "Онлайн-трек: идёт запись" else "Онлайн-трек: досылаем данные")
        .setContentText(if (recording) "Позиция передаётся зрителям соревнования" else "Запись завершена")
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun openAppIntent(): PendingIntent? = packageManager.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onDestroy() {
        super.onDestroy()
        gps.stop()
        recorderState.onRecordingStopped()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 1003
        const val CHANNEL_ID = "live_track"

        private const val TAG = "CompetitionTracking"

        /** Фикс не реже раза в 2 с / 3 м. */
        private const val MIN_TIME_MS = 2_000L
        private const val MIN_DISTANCE_M = 3f

        /** Фиксы грубее этого (лес, старт в помещении) отбрасываются. */
        private const val MAX_ACCURACY_M = 50f

        /** Сколько сервис досылает буфер после окончания записи; дальше — воркер. */
        private const val DRAIN_BUDGET_MS = 30_000L
        private const val WATCH_INTERVAL_MS = 3_000L

        private const val ACTION_STOP = "com.competra.app.action.STOP_LIVE_TRACK"
        private const val EXTRA_SESSION_ID = "session_id"

        fun startIntent(context: Context, sessionId: String): Intent =
            Intent(context, CompetitionTrackingService::class.java).putExtra(EXTRA_SESSION_ID, sessionId)

        fun stopIntent(context: Context, sessionId: String): Intent =
            Intent(context, CompetitionTrackingService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_SESSION_ID, sessionId)
    }
}
