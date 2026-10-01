package com.competra.domain.models.push

/**
 * Значения `data["kind"]` в FCM-payload — должны совпадать с константами на backend
 * (см. eSport: ReminderNotificationScheduler.kt, OrienteeringCompetitionService.kt).
 */
object PushKind {
    const val COMPETITION_START_REMINDER = "competition_start_reminder"
    const val DAY_BEFORE_REMINDER = "day_before_reminder"
    const val RESULTS_PUBLISHED = "results_published"
    /** Организатору: спортсмен просит привязать вручную внесённый результат (eSport: ParticipantLinkRequestService.kt). */
    const val PARTICIPANT_LINK_REQUESTED = "participant_link_requested"
    /** Заявителю: организатор одобрил или отклонил заявку на привязку. */
    const val PARTICIPANT_LINK_REVIEWED = "participant_link_reviewed"

    fun toPushCategory(kind: String?): PushCategory? = when (kind) {
        COMPETITION_START_REMINDER -> PushCategory.COMPETITION_START
        DAY_BEFORE_REMINDER -> PushCategory.DAY_BEFORE_REMINDER
        RESULTS_PUBLISHED -> PushCategory.RESULTS_PUBLISHED
        else -> null
    }
}
