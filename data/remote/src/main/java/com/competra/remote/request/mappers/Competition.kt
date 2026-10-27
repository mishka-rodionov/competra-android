package com.competra.remote.request.mappers

import com.competra.domain.models.Competition
import com.competra.domain.models.Coordinates
import com.competra.remote.request.competition.CompetitionRequest
import com.competra.remote.request.competition.CoordinatesRequest

/**
 * Преобразует доменную модель Competition в модель запроса CompetitionRequest.
 */
fun Competition.toRequest(): CompetitionRequest {
    return CompetitionRequest(
        title = title,
        startDate = startDate,
        endDate = endDate,
        kindOfSport = kindOfSport.name,
        description = description,
        address = address,
        mainOrganizerId = mainOrganizerId,
        coordinates = coordinates?.toRequest(),
        status = status.name,
        registrationStart = registrationStart,
        registrationEnd = registrationEnd,
        maxParticipants = maxParticipants,
        feeAmount = feeAmount,
        feeCurrency = feeCurrency,
        regulationUrl = regulationUrl,
        mapUrl = mapUrl,
        resultsUrl = resultsUrl,
        contactPhone = contactPhone,
        contactEmail = contactEmail,
        website = website,
        resultsStatus = resultsStatus.name,
        timeZoneId = timeZoneId,
        imageUrl = imageUrl,
        coverCropX = imageCropRect?.x,
        coverCropY = imageCropRect?.y,
        coverCropWidth = imageCropRect?.width,
        coverCropHeight = imageCropRect?.height,
        isTest = isTest,
        serverUpdatedAt = serverUpdatedAt,
        organizingClubId = organizingClubId,
        organizerName = organizerName
    )
}

/**
 * Преобразует доменную модель Coordinates в модель запроса CoordinatesRequest.
 */
fun Coordinates.toRequest(): CoordinatesRequest {
    return CoordinatesRequest(
        latitude = latitude,
        longitude = longitude
    )
}
