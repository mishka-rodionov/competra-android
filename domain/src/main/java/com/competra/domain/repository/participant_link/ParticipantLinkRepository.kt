package com.competra.domain.repository.participant_link

import com.competra.domain.models.participant_link.CompetitionLinkRequest
import com.competra.domain.models.participant_link.LinkRequest
import com.competra.domain.models.participant_link.LinkRequestSource
import com.competra.domain.models.participant_link.LinkSuggestion

/**
 * Заявки на привязку вручную внесённых участников к аккаунтам. Только online, без offline-first:
 * ошибки сети — обычный [Result.failure].
 */
interface ParticipantLinkRepository {

    /** Непривязанные участники, похожие по имени на текущего пользователя. */
    suspend fun getSuggestions(): Result<List<LinkSuggestion>>

    /** Подать заявки на привязку участников [participantIds] к текущему пользователю. */
    suspend fun createRequests(participantIds: List<String>, source: LinkRequestSource): Result<List<LinkRequest>>

    /** Заявки текущего пользователя во всех соревнованиях. */
    suspend fun getMyRequests(): Result<List<LinkRequest>>

    /** Отозвать свою заявку, пока она на рассмотрении. */
    suspend fun cancelRequest(requestId: String): Result<Unit>

    /** Заявки по соревнованию — для тех, кто управляет его участниками. */
    suspend fun getCompetitionRequests(competitionId: String): Result<List<CompetitionLinkRequest>>

    /** competitionId → число заявок на рассмотрении по соревнованиям, где пользователь управляет участниками. */
    suspend fun getPendingCounts(): Result<Map<String, Int>>

    /** Одобрить или отклонить заявку; [comment] — причина отклонения, её увидит заявитель. */
    suspend fun reviewRequest(requestId: String, approve: Boolean, comment: String?): Result<Unit>

    /** Отвязать участника от аккаунта: сам привязанный пользователь или организатор. */
    suspend fun unlinkParticipant(participantId: String): Result<Unit>
}
