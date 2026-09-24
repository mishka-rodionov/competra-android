package com.competra.app.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.competra.app.R
import com.competra.core.tracking.GpsLocationSource
import com.competra.diary.data.interactors.WorkoutInteractor
import com.competra.domain.diary.TrackCodec
import com.competra.domain.diary.TrackPoint
import com.competra.domain.models.diary.SportType
import com.competra.domain.models.diary.Workout
import com.competra.domain.models.diary.WorkoutStatus
import com.competra.domain.models.diary.WorkoutWithDetails
import com.competra.domain.repository.diary.WorkoutLocalRepository
import com.competra.ui.WorkoutTrackingRepository
import com.competra.ui.WorkoutTrackingSnapshot
import com.competra.ui.WorkoutTrackingStatus
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
 * Foreground service live-трекинга тренировки. Пишет GPS-точки через
 * [android.location.LocationManager] (не Google Play Services — см. Context в плане: приложение
 * распространяется во флейворах gplay/rustore/huawei, на Huawei без HMS play-services ненадёжны).
 *
 * Точки чек-пойнтятся в Room каждые [CHECKPOINT_INTERVAL_MS] — если сервис убьёт система
 * посреди тренировки, теряются только последние секунды, а не весь трек.
 */
class WorkoutTrackingService : Service() {

    private val workoutInteractor: WorkoutInteractor by inject()
    private val localRepository: WorkoutLocalRepository by inject()
    private val trackingRepository: WorkoutTrackingRepository by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var workoutId: Long = 0L
    private var sportType: SportType = SportType.RUNNING
    private var startedAtMs: Long = 0L
    private var pausedAccumulatedMs: Long = 0L
    private var lastResumeMs: Long = 0L
    private var status: WorkoutTrackingStatus = WorkoutTrackingStatus.RUNNING

    private val points = mutableListOf<TrackPoint>()
    private var distanceMeters = 0.0
    private var elevationGainMeters = 0.0
    private var lastLocation: Location? = null
    private var lastLocationAtMs: Long = 0L
    private var lastCheckpointAtMs: Long = 0L
    private var tickerJob: Job? = null

    private data class SpeedSample(val distanceMeters: Double, val durationMs: Long)
    private val recentSpeedSamples = ArrayDeque<SpeedSample>()
    private var currentSpeedMps = 0.0

    private val gps: GpsLocationSource by inject()
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking(discard = intent.getBooleanExtra(EXTRA_DISCARD, false))
            else -> {
                val sport = intent?.getStringExtra(EXTRA_SPORT_TYPE)?.let { runCatching { SportType.valueOf(it) }.getOrNull() }
                startTracking(sport ?: SportType.RUNNING)
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun startTracking(sport: SportType) {
        sportType = sport
        startedAtMs = System.currentTimeMillis()
        lastResumeMs = startedAtMs
        pausedAccumulatedMs = 0L
        status = WorkoutTrackingStatus.RUNNING
        points.clear()
        distanceMeters = 0.0
        elevationGainMeters = 0.0
        lastLocation = null
        lastLocationAtMs = 0L
        recentSpeedSamples.clear()
        currentSpeedMps = 0.0
        lastCheckpointAtMs = startedAtMs

        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            val toSave = WorkoutWithDetails(
                workout = Workout(sportType = sportType, status = WorkoutStatus.IN_PROGRESS, startedAt = startedAtMs)
            )
            localRepository.saveWorkout(toSave, markUnsynced = false).onSuccess { saved ->
                workoutId = saved.workout.id
            }
        }

        requestLocationUpdates()
        pushSnapshot()
        startTicker()
    }

    /**
     * Секундный тикер времени — независим от GPS-фиксов, которые могут не приходить
     * (нет сигнала, стоим на месте до срабатывания minDistance) и раньше оставляли
     * "Время" на экране замороженным между фиксами.
     */
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                pushSnapshot()
                updateNotification()
            }
        }
    }

    /** Без разрешения или с выключенным GPS подписки нет — время тикает, точки не пишутся. */
    private fun requestLocationUpdates() {
        gps.start(MIN_TIME_MS, MIN_DISTANCE_M) { location -> handleNewLocation(location) }
    }

    private fun handleNewLocation(location: Location) {
        if (status != WorkoutTrackingStatus.RUNNING) return
        if (location.accuracy > MAX_ACCURACY_M) return

        val now = System.currentTimeMillis()
        lastLocation?.let { previous ->
            val segmentDistanceMeters = previous.distanceTo(location).toDouble()
            distanceMeters += segmentDistanceMeters
            if (previous.hasAltitude() && location.hasAltitude()) {
                val delta = location.altitude - previous.altitude
                if (delta > ALTITUDE_NOISE_THRESHOLD_M) elevationGainMeters += delta
            }
            updateSpeed(segmentDistanceMeters, now - lastLocationAtMs)
        }
        lastLocation = location
        lastLocationAtMs = now
        points.add(TrackPoint(location.latitude, location.longitude, now))

        pushSnapshot()
        updateNotification()

        if (now - lastCheckpointAtMs >= CHECKPOINT_INTERVAL_MS) {
            lastCheckpointAtMs = now
            checkpoint()
        }
    }

    /**
     * Сглаживает мгновенную скорость средним по последним [SPEED_SAMPLE_WINDOW] отрезкам трека —
     * скорость из одиночного GPS-фикса слишком дёргается. Отрезки длиннее [MAX_SPEED_SEGMENT_MS]
     * (пауза, потеря сигнала) в расчёт не берутся и сбрасывают накопленное среднее, чтобы после
     * паузы/провала сигнала не подмешивался этот большой разрыв по времени.
     */
    private fun updateSpeed(segmentDistanceMeters: Double, segmentDurationMs: Long) {
        if (segmentDurationMs <= 0 || segmentDurationMs > MAX_SPEED_SEGMENT_MS) {
            recentSpeedSamples.clear()
            currentSpeedMps = 0.0
            return
        }

        recentSpeedSamples.addLast(SpeedSample(segmentDistanceMeters, segmentDurationMs))
        while (recentSpeedSamples.size > SPEED_SAMPLE_WINDOW) recentSpeedSamples.removeFirst()

        val totalDistanceMeters = recentSpeedSamples.sumOf { it.distanceMeters }
        val totalDurationMs = recentSpeedSamples.sumOf { it.durationMs }
        currentSpeedMps = totalDistanceMeters / (totalDurationMs / 1000.0)
    }

    private fun elapsedSeconds(): Int {
        val runningMs = if (status == WorkoutTrackingStatus.RUNNING) System.currentTimeMillis() - lastResumeMs else 0L
        return ((pausedAccumulatedMs + runningMs) / 1000L).toInt()
    }

    private fun pushSnapshot() {
        trackingRepository.update(
            WorkoutTrackingSnapshot(
                status = status,
                sportType = sportType,
                elapsedSeconds = elapsedSeconds(),
                distanceMeters = distanceMeters.toInt(),
                elevationGainMeters = elevationGainMeters.toInt(),
                points = points.toList(),
                speedMetersPerSecond = currentSpeedMps
            )
        )
    }

    /** Промежуточная запись трека в Room — страховка от потери данных при убийстве сервиса системой. */
    private fun checkpoint() {
        val id = workoutId
        if (id == 0L) return
        serviceScope.launch {
            val current = localRepository.getWorkoutById(id).getOrNull() ?: return@launch
            localRepository.updateWorkout(
                current.copy(
                    workout = current.workout.copy(
                        trackEncoded = TrackCodec.encode(startedAtMs, points.toList()),
                        distanceMeters = distanceMeters.toInt(),
                        elevationGainMeters = elevationGainMeters.toInt(),
                        durationSeconds = elapsedSeconds()
                    )
                ),
                markUnsynced = false
            )
        }
    }

    private fun pauseTracking() {
        if (status != WorkoutTrackingStatus.RUNNING) return
        pausedAccumulatedMs += System.currentTimeMillis() - lastResumeMs
        status = WorkoutTrackingStatus.PAUSED
        gps.stop()
        recentSpeedSamples.clear()
        currentSpeedMps = 0.0
        pushSnapshot()
        updateNotification()
    }

    private fun resumeTracking() {
        if (status != WorkoutTrackingStatus.PAUSED) return
        lastResumeMs = System.currentTimeMillis()
        status = WorkoutTrackingStatus.RUNNING
        requestLocationUpdates()
        pushSnapshot()
        updateNotification()
    }

    private fun stopTracking(discard: Boolean) {
        gps.stop()
        val id = workoutId

        if (id == 0L) {
            finishService()
            return
        }

        serviceScope.launch {
            if (discard) {
                localRepository.purgeWorkoutLocally(id)
            } else {
                localRepository.getWorkoutById(id).getOrNull()?.let { current ->
                    workoutInteractor.updateWorkout(
                        current.copy(
                            workout = current.workout.copy(
                                status = WorkoutStatus.COMPLETED,
                                trackEncoded = TrackCodec.encode(startedAtMs, points.toList()),
                                distanceMeters = distanceMeters.toInt(),
                                elevationGainMeters = elevationGainMeters.toInt(),
                                durationSeconds = elapsedSeconds()
                            )
                        )
                    )
                }
            }
            finishService()
        }
    }

    private fun finishService() {
        tickerJob?.cancel()
        trackingRepository.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(if (status == WorkoutTrackingStatus.PAUSED) "Тренировка на паузе" else "Тренировка идёт")
        .setContentText(notificationText())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun notificationText(): String {
        val km = distanceMeters / 1000.0
        val minutes = elapsedSeconds() / 60
        return "%.1f км • %d мин".format(km, minutes)
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        gps.stop()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "workout_tracking"

        private const val MIN_TIME_MS = 3000L
        private const val MIN_DISTANCE_M = 5f
        private const val MAX_ACCURACY_M = 30f
        private const val ALTITUDE_NOISE_THRESHOLD_M = 2.0
        private const val CHECKPOINT_INTERVAL_MS = 30_000L
        private const val SPEED_SAMPLE_WINDOW = 3
        private const val MAX_SPEED_SEGMENT_MS = 15_000L

        private const val ACTION_PAUSE = "com.competra.app.action.PAUSE_TRACKING"
        private const val ACTION_RESUME = "com.competra.app.action.RESUME_TRACKING"
        private const val ACTION_STOP = "com.competra.app.action.STOP_TRACKING"
        private const val EXTRA_SPORT_TYPE = "sport_type"
        private const val EXTRA_DISCARD = "discard"

        fun startIntent(context: Context, sportType: SportType): Intent =
            Intent(context, WorkoutTrackingService::class.java).apply {
                putExtra(EXTRA_SPORT_TYPE, sportType.name)
            }

        fun pauseIntent(context: Context): Intent =
            Intent(context, WorkoutTrackingService::class.java).apply { action = ACTION_PAUSE }

        fun resumeIntent(context: Context): Intent =
            Intent(context, WorkoutTrackingService::class.java).apply { action = ACTION_RESUME }

        fun stopIntent(context: Context, discard: Boolean): Intent =
            Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_DISCARD, discard)
            }
    }
}
