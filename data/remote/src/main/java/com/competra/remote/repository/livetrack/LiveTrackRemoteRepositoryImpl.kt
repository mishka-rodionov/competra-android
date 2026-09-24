package com.competra.remote.repository.livetrack

import com.competra.domain.models.livetrack.LiveTrackAck
import com.competra.domain.models.livetrack.LiveTrackCloseReason
import com.competra.domain.models.livetrack.LiveTrackRejectedException
import com.competra.domain.models.livetrack.LiveTrackStartResult
import com.competra.domain.models.livetrack.LiveTrackStatus
import com.competra.domain.models.livetrack.RunnerTrackPoint
import com.competra.domain.repository.livetrack.LiveTrackRemoteRepository
import com.competra.remote.base.CommonModel
import com.competra.remote.datasource.livetrack.LiveTrackAckResponse
import com.competra.remote.datasource.livetrack.LiveTrackPointDto
import com.competra.remote.datasource.livetrack.LiveTrackPointsRequest
import com.competra.remote.datasource.livetrack.LiveTrackRemoteDataSource
import com.competra.remote.datasource.livetrack.LiveTrackStartRequest
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * [LiveTrackRemoteRepository] поверх [LiveTrackRemoteDataSource]. 4xx превращаются в
 * [LiveTrackRejectedException] (с текстом ошибки сервера), остальные сбои — в [IOException].
 */
class LiveTrackRemoteRepositoryImpl(
    private val dataSource: LiveTrackRemoteDataSource,
    private val gson: Gson
) : LiveTrackRemoteRepository {

    override suspend fun start(competitionId: String): Result<LiveTrackStartResult> = call {
        dataSource.start(LiveTrackStartRequest(competitionId = competitionId, consent = true))
    }.map {
        LiveTrackStartResult(
            sessionId = it.sessionId,
            status = it.status.toStatus(),
            closeReason = it.closeReason.toCloseReason(),
            lastBatchSeq = it.lastBatchSeq,
            uploadIntervalSec = it.uploadIntervalSec,
            deadlineAt = it.deadlineAt
        )
    }

    override suspend fun sendPoints(sessionId: String, batchSeq: Int, points: List<RunnerTrackPoint>): Result<LiveTrackAck> = call {
        dataSource.sendPoints(
            sessionId,
            LiveTrackPointsRequest(batchSeq, points.map { LiveTrackPointDto(it.t, it.lat, it.lon, it.accuracy) })
        )
    }.map { it.toDomain() }

    override suspend fun stop(sessionId: String): Result<LiveTrackAck> = call { dataSource.stop(sessionId) }.map { it.toDomain() }

    private suspend fun <T> call(block: suspend () -> Response<CommonModel<T>>): Result<T> = try {
        val response = block()
        val body = response.body()
        when {
            response.isSuccessful && body?.status == 1 && body.result != null -> Result.success(body.result!!)
            response.code() in 400..499 -> Result.failure(LiveTrackRejectedException(response.code(), response.errorMessage()))
            else -> Result.failure(IOException("Live track HTTP ${response.code()}"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Текст первой ошибки из тела `CommonModel` ответа с ошибкой, если он есть. */
    private fun Response<*>.errorMessage(): String? = runCatching {
        val raw = errorBody()?.string() ?: return null
        gson.fromJson(raw, CommonModel::class.java)?.getFirstErrorMessage()?.message
    }.getOrNull()
}

private fun LiveTrackAckResponse.toDomain() = LiveTrackAck(
    status = status.toStatus(),
    closeReason = closeReason.toCloseReason(),
    ackedBatchSeq = ackedBatchSeq
)

private fun String.toStatus(): LiveTrackStatus = runCatching { LiveTrackStatus.valueOf(this) }.getOrDefault(LiveTrackStatus.ACTIVE)

private fun String?.toCloseReason(): LiveTrackCloseReason? = this?.let { runCatching { LiveTrackCloseReason.valueOf(it) }.getOrNull() }
