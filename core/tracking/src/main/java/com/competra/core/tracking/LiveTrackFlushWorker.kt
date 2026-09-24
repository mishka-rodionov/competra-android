package com.competra.core.tracking

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.competra.domain.repository.livetrack.LiveTrackLocalRepository
import java.util.concurrent.TimeUnit

/** Сколько воркер досылает за один запуск, прежде чем уступить WorkManager. */
private val FLUSH_BUDGET_MS = TimeUnit.MINUTES.toMillis(2)

/**
 * Досылает буфер онлайн-трека и остановку, когда foreground-сервис уже не работает: бегун нажал
 * «Стоп» без сети, сервер закрыл сессию, а хвост точек ещё в телефоне, или систему убила сервис.
 * GPS не пишет — только отправка того, что уже в Room.
 */
class LiveTrackFlushWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val local: LiveTrackLocalRepository,
    private val engine: LiveTrackEngine
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val session = local.findUnfinishedSession() ?: return Result.success()
        val deadline = System.currentTimeMillis() + FLUSH_BUDGET_MS
        return try {
            when (engine.runUploads(session.sessionId) { System.currentTimeMillis() < deadline && !isStopped }) {
                UploadStep.DONE -> Result.success()
                // Сессия ещё пишет (сервис запустят снова из UI) — досылку продолжит он; иначе повтор.
                else -> if (local.getSession(session.sessionId)?.isRecording == true) Result.success() else Result.retry()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Live track flush failed, will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "LiveTrackFlushWorker"
        private const val UNIQUE_WORK_NAME = "LiveTrackFlushWorker"
        private const val BACKOFF_BASE_SECONDS = 30L

        /** Ставит досылку в очередь (при появлении сети); повторная постановка не дублирует. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<LiveTrackFlushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_BASE_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
