package com.competra.core.tracking

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.competra.analytics.AnalyticsEvent
import com.competra.domain.models.livetrack.MyStart
import com.competra.domain.models.livetrack.RunnerTrackSession
import com.competra.domain.repository.livetrack.MyStartsRepository

/**
 * Включение онлайн-трека из UI (детали соревнования, карточка «Сегодня старт»): сессия создаётся на
 * телефоне и сразу запускается сервис записи — интернет не нужен, трек уйдёт на сервер при связи.
 * Согласие и разрешения UI получает до вызова.
 */
class LiveTrackStarter(
    private val engine: LiveTrackEngine,
    private val myStarts: MyStartsRepository,
    private val controller: CompetitionTrackingController,
    private val context: Context
) {

    /** Начинает (или продолжает идущую) запись трека в соревновании [competitionId]. */
    suspend fun start(competitionId: String, source: AnalyticsEvent.LiveTrackStartSource): RunnerTrackSession {
        val now = System.currentTimeMillis()
        val deadlineAt = myStarts.get(competitionId)?.localDeadline(now) ?: MyStart.defaultLocalDeadline(now)
        val session = engine.startOrResume(competitionId, deadlineAt, source, offline = !isOnline())
        controller.startRecording(session.sessionId)
        return session
    }

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
