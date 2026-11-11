package com.competra.core.sync

import android.util.Log
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.competra.domain.repository.orienteering.OrienteeringCompetitionLocalRepository
import com.competra.remote.response.mappers.toDomain
import com.competra.remote.response.orienteering.DistanceResponse
import com.competra.remote.response.orienteering.OrienteeringCompetitionResponse
import com.competra.remote.response.orienteering.OrienteeringParticipantResponse
import com.competra.remote.response.orienteering.OrienteeringResultResponse
import com.competra.remote.response.orienteering.ParticipantGroupResponse

/**
 * Применяет server-wins при HTTP 409: парсит тело ответа сервера (CommonModel.result —
 * актуальная серверная запись) в соответствующий response-DTO, мапит в доменную модель,
 * перезаписывает локальную запись с сохранением локального PK и связей.
 *
 * Записывается с `markUnsynced=false` и `isSynced=true`, чтобы Worker больше не пытался
 * выгрузить эти данные. Локальные изменения теряются — это явное условие server-wins
 * для сценария «один организатор на одно соревнование».
 */
class ConflictResolver(
    private val localRepository: OrienteeringCompetitionLocalRepository
) {

    private val gson = Gson()

    suspend fun applyCompetitionConflict(competitionId: String, payload: String?) {
        val response = parseResult(payload, OrienteeringCompetitionResponse::class.java) ?: return
        val domain = response.toDomain().copy(competitionId = competitionId)
        localRepository.updateCompetition(domain, markUnsynced = false)
    }

    suspend fun applyGroupConflict(localGroupId: Long, competitionId: String, payload: String?) {
        val response = parseResult(payload, ParticipantGroupResponse::class.java) ?: return
        val domain = response.toDomain().copy(
            groupId = localGroupId,
            competitionId = competitionId
        )
        localRepository.updateParticipantGroup(domain, markUnsynced = false)
    }

    suspend fun applyDistanceConflict(localDistanceId: Long, competitionId: String, payload: String?) {
        val response = parseResult(payload, DistanceResponse::class.java) ?: return
        val domain = response.toDomain(competitionId).copy(id = localDistanceId)
        localRepository.updateDistance(domain, markUnsynced = false)
    }

    /**
     * Server-wins для участника из пачки [batch]: в payload — серверная версия того участника, на
     * котором случился конфликт (не обязательно первого в пачке). Его серверная группа переводится в
     * локальную; если такой группы на телефоне нет — остаётся локальная группа участника.
     */
    suspend fun applyParticipantConflict(batch: List<OrienteeringParticipant>, payload: String?) {
        val response = parseResult(payload, OrienteeringParticipantResponse::class.java) ?: return
        val server = response.toDomain()
        val local = batch.firstOrNull { it.id == server.id } ?: return
        val localGroupId = localRepository.getCompetitionWithDetails(local.competitionId).getOrNull()
            ?.groupsWithParticipants
            ?.map { it.group }
            ?.firstOrNull { it.remoteId != null && it.remoteId == server.groupId }
            ?.groupId
            ?: local.groupId
        val domain = server.copy(
            competitionId = local.competitionId,
            groupId = localGroupId,
            // Сервер не источник истины для выдачи чипа — как и при обычной загрузке с сервера.
            isChipGiven = local.isChipGiven,
            remoteId = local.remoteId ?: local.id
        )
        localRepository.updateParticipants(listOf(domain), markUnsynced = false)
    }

    suspend fun applyResultConflict(localResultId: Long, competitionId: String, localGroupId: Long, payload: String?) {
        val response = parseResult(payload, OrienteeringResultResponse::class.java) ?: return
        val domain = response.toDomain().copy(
            id = localResultId,
            competitionId = competitionId,
            groupId = localGroupId
        )
        localRepository.updateResults(listOf(domain), markUnsynced = false)
    }

    /**
     * Извлекает поле `result` из CommonModel-обёртки и десериализует его в [clazz].
     * Возвращает null, если payload пустой или не содержит result.
     */
    private fun <T> parseResult(payload: String?, clazz: Class<T>): T? {
        if (payload.isNullOrBlank()) return null
        return try {
            val envelope = gson.fromJson(payload, JsonObject::class.java) ?: return null
            val resultJson = envelope.get("result")?.takeIf { !it.isJsonNull } ?: return null
            gson.fromJson(resultJson, clazz)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse server-wins payload: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "ConflictResolver"
    }
}
