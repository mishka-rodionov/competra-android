package com.competra.remote.repository.events

import com.competra.domain.models.Coordinates
import com.competra.domain.models.CropRect
import com.competra.domain.models.cyclic_event.CyclicEventDetails
import com.competra.domain.models.cyclic_event.EventParticipantGroup
import com.competra.domain.models.events.EventStatus
import com.competra.domain.models.events.EventType
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.ResultsStatus
import com.competra.domain.repository.events.CyclicEventDetailsRepository
import com.competra.remote.datasource.events.CyclicEventDetailsRemoteDataSource
import com.competra.remote.request.events.RegisterEventRequest
import com.competra.remote.response.events.ParticipantPublicResponse

/**
 * Реализация репозитория для получения деталей соревнования.
 *
 * @param dataSource Источник данных для работы с API.
 */
class CyclicEventDetailsRepositoryImpl(
    private val dataSource: CyclicEventDetailsRemoteDataSource
) : CyclicEventDetailsRepository {

    override suspend fun getEventDetails(eventId: String, userId: String?): Result<CyclicEventDetails?> {
        return dataSource.getEventDetails(eventId, userId)
            .map { response ->
                response.result?.let { dto ->
                    CyclicEventDetails(
                        eventId = dto.id,
                        organizationId = dto.mainOrganizerId ?: "",
                        organizingClubId = dto.organizingClubId,
                        organizerFirstName = dto.organizerFirstName,
                        organizerLastName = dto.organizerLastName,
                        organizerMiddleName = dto.organizerMiddleName,
                        title = dto.title,
                        description = dto.description ?: "",
                        startDate = dto.startDate,
                        startTime = dto.startTime,
                        endDate = dto.endDate ?: dto.startDate,
                        registrationStartDate = dto.registrationStart ?: 0L,
                        endRegistrationDate = dto.registrationEnd ?: dto.startDate,
                        maxParticipants = dto.maxParticipants ?: 0,
                        city = dto.address ?: "",
                        coordinates = dto.coordinates?.let { Coordinates(it.latitude, it.longitude) },
                        feeAmount = dto.feeAmount,
                        feeCurrency = dto.feeCurrency,
                        regulationUrl = dto.regulationUrl,
                        mapUrl = dto.mapUrl,
                        resultsUrl = dto.resultsUrl,
                        contactPhone = dto.contactPhone,
                        contactEmail = dto.contactEmail,
                        website = dto.website,
                        timeZoneId = dto.timeZoneId,
                        participantGroups = dto.participantGroups.map { group ->
                            EventParticipantGroup(
                                groupId = group.groupId,
                                title = group.title,
                                description = null,
                                maxParticipant = group.maxParticipants ?: 0,
                                registeredParticipant = group.registeredCount,
                                distanceName = group.distanceName,
                                distanceLengthMeters = group.distanceLengthMeters,
                                distanceClimbMeters = group.distanceClimbMeters,
                                distanceControlsCount = group.distanceControlsCount,
                                distanceDescription = group.distanceDescription,
                                controlTimeMinutes = group.controlTimeMinutes,
                                overtimePolicy = dto.overtimePolicy
                            )
                        },
                        status = mapStatus(dto.status),
                        eventType = mapEventType(dto.kindOfSport),
                        resultsStatus = mapResultsStatus(dto.resultsStatus),
                        isUserRegistered = dto.isUserRegistered,
                        imageUrl = dto.imageUrl,
                        imageCropRect = toCropRect(dto.coverCropX, dto.coverCropY, dto.coverCropWidth, dto.coverCropHeight)
                    )
                }
            }
    }

    override suspend fun registerToEvent(
        eventId: String,
        groupId: String,
        firstName: String,
        lastName: String,
        commandName: String?
    ): Result<Unit> {
        return dataSource.registerToEvent(
            RegisterEventRequest(
                competitionId = eventId,
                groupId = groupId,
                firstName = firstName,
                lastName = lastName,
                commandName = commandName
            )
        ).mapCatching { }
    }

    override suspend fun cancelRegistration(eventId: String): Result<Unit> {
        return dataSource.cancelRegistration(eventId)
            .mapCatching { }
    }

    override suspend fun getParticipants(
        eventId: String,
        groupId: String
    ): Result<List<OrienteeringParticipant>> {
        return dataSource.getParticipantsByGroup(groupId)
            .map { response ->
                response.result?.map { it.toDomain() } ?: emptyList()
            }
    }

    private fun toCropRect(x: Double?, y: Double?, width: Double?, height: Double?): CropRect? {
        if (x == null || y == null || width == null || height == null) return null
        return CropRect(x, y, width, height)
    }

    private fun ParticipantPublicResponse.toDomain(): OrienteeringParticipant {
        return OrienteeringParticipant(
            id = id,
            userId = userId ?: "",
            firstName = firstName,
            lastName = lastName,
            groupId = 0L,
            groupName = groupName,
            competitionId = "",
            commandName = commandName ?: "",
            startNumber = startNumber.toString(),
            startTime = startTime,
            chipNumber = chipNumber.toString(),
            comment = comment ?: "",
            isChipGiven = isChipGiven
        )
    }

    private fun mapStatus(status: String): EventStatus = when (status) {
        "REGISTRATION_OPEN" -> EventStatus.REGISTRATION
        "IN_PROGRESS" -> EventStatus.STARTED
        "FINISHED" -> EventStatus.FINISHED
        "CANCELLED" -> EventStatus.CANCELLED
        else -> EventStatus.CREATED
    }

    private fun mapEventType(kindOfSport: String): EventType = when (kindOfSport) {
        "Orienteering" -> EventType.CyclicEvent.Orienteering
        "CrossCountrySki" -> EventType.CyclicEvent.CrossCountry
        "TrailRunning" -> EventType.CyclicEvent.TrailRunning
        else -> EventType.CyclicEvent.Orienteering
    }

    private fun mapResultsStatus(resultsStatus: String): ResultsStatus = try {
        ResultsStatus.valueOf(resultsStatus)
    } catch (e: IllegalArgumentException) {
        ResultsStatus.NOT_PUBLISHED
    }
}
