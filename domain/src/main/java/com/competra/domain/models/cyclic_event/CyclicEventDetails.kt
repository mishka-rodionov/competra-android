package com.competra.domain.models.cyclic_event

import com.competra.domain.models.Coordinates
import com.competra.domain.models.CropRect
import com.competra.domain.models.events.EventStatus
import com.competra.domain.models.events.EventType
import com.competra.domain.models.orienteering.ResultsStatus

data class CyclicEventDetails(
    val eventId: String,
    val organizationId: String,
    val organizingClubId: String? = null,
    val organizerFirstName: String? = null,
    val organizerLastName: String? = null,
    val organizerMiddleName: String? = null,
    val title: String,
    val description: String,
    val startDate: Long,
    val startTime: Long? = null,
    val endDate: Long,
    val registrationStartDate: Long = 0L,
    val endRegistrationDate: Long,
    val maxParticipants: Int,
    val city: String,
    val coordinates: Coordinates? = null,
    val feeAmount: Double? = null,
    val feeCurrency: String? = null,
    val regulationUrl: String? = null,
    val mapUrl: String? = null,
    val resultsUrl: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    val website: String? = null,
    val timeZoneId: String? = null,
    val participantGroups: List<EventParticipantGroup>,
    val status: EventStatus,
    val eventType: EventType,
    val resultsStatus: ResultsStatus = ResultsStatus.NOT_PUBLISHED,
    val isUserRegistered: Boolean = false,
    /**
     * Соревнование уже стартовало: серверный статус IN_PROGRESS/FINISHED/ARCHIVED. Отдельно от
     * [status], потому что ARCHIVED в [EventStatus] не различается и попадает в CREATED.
     */
    val hasStarted: Boolean = false,
    /** Текущий пользователь — организатор соревнования (любая роль); считает сервер по токену. */
    val viewerIsOrganizer: Boolean = false,
    val imageUrl: String? = null,
    val imageCropRect: CropRect? = null
)
