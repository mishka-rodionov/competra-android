package com.competra.domain.models.participant_link

import com.competra.domain.models.Gender

/**
 * Статус заявки на привязку вручную внесённого участника к аккаунту.
 * [UNLINKED] — заявка была одобрена, потом участника отвязали.
 */
enum class LinkRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    UNLINKED;

    companion object {
        /** Незнакомый статус (новый на бэкенде) не роняет разбор — считаем заявку обработанной. */
        fun from(raw: String): LinkRequestStatus = entries.firstOrNull { it.name == raw } ?: CANCELLED
    }
}

/** Откуда пользователь взял участника: подсказка по имени или сам выбрал в протоколе. */
enum class LinkRequestSource {
    SUGGESTION,
    MANUAL;

    companion object {
        fun from(raw: String): LinkRequestSource = if (raw == SUGGESTION.name) SUGGESTION else MANUAL
    }
}

/** Коротко о результате участника — чтобы человек узнал свой старт. [totalTime] — в секундах. */
data class LinkResultSummary(
    val rank: Int?,
    val totalTime: Long?,
    val totalScore: Int?,
    val status: String,
)

/** Непривязанный участник протокола, похожий по имени на текущего пользователя. */
data class LinkSuggestion(
    val participantId: String,
    val competitionId: String,
    val competitionTitle: String,
    val competitionStartDate: Long,
    val firstName: String,
    val lastName: String,
    val groupName: String,
    val commandName: String?,
    val result: LinkResultSummary?,
)

/** Заявка на привязку глазами заявителя. */
data class LinkRequest(
    val id: String,
    val participantId: String,
    val competitionId: String,
    val competitionTitle: String,
    val competitionStartDate: Long,
    val participantFirstName: String,
    val participantLastName: String,
    val groupName: String,
    val status: LinkRequestStatus,
    val source: LinkRequestSource,
    val comment: String?,
    val createdAt: Long,
)

/** Заявка на привязку глазами организатора — с подсказками для проверки. */
data class CompetitionLinkRequest(
    val id: String,
    val status: LinkRequestStatus,
    val source: LinkRequestSource,
    val comment: String?,
    val createdAt: Long,
    val participantId: String,
    val participantFirstName: String,
    val participantLastName: String,
    val groupName: String,
    val commandName: String?,
    val startNumber: Int,
    val result: LinkResultSummary?,
    val userId: String,
    val userFirstName: String,
    val userLastName: String,
    val userBirthYear: Int?,
    val userGender: Gender?,
    /** Имя в протоколе совпадает с профилем заявителя. */
    val nameMatches: Boolean,
    /** Пол/возраст заявителя не подходят к группе участника (или их не проверить); null — подходят. */
    val eligibilityWarning: String?,
    /** Сколько ещё заявок на рассмотрении на этого же участника от других пользователей. */
    val competingRequests: Int,
    /** У заявителя уже есть свой участник в этом соревновании — одобрить нельзя, нужно удалить дубль. */
    val userAlreadyInCompetition: Boolean,
)
