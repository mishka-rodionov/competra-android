package com.competra.remote.request.mappers

import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.ControlPoint
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.remote.request.orienteering.ControlPointRequest
import com.competra.remote.request.orienteering.DistanceRequest
import com.competra.remote.request.orienteering.OrienteeringCompetitionRequest
import com.competra.remote.request.orienteering.OrienteeringParticipantRequest
import com.competra.remote.request.orienteering.OrienteeringResultRequest
import com.competra.remote.request.orienteering.ParticipantGroupRequest
import com.competra.remote.request.orienteering.SplitTimeRequest

fun OrienteeringCompetition.toRequest(): OrienteeringCompetitionRequest {
    return OrienteeringCompetitionRequest(
        competitionId = competitionId,
        competition = competition.toRequest(),
        direction = direction.name,
        punchingSystem = punchingSystem.name,
        startTimeMode = startTimeMode.name,
        countdownTimer = countdownTimer,
        startIntervalSeconds = startIntervalSeconds,
        serverUpdatedAt = serverUpdatedAt
    )
}

/**
 * Преобразует доменную модель группы участников в модель запроса для сервера.
 */
fun ParticipantGroup.toRequest(): ParticipantGroupRequest {
    return ParticipantGroupRequest(
        groupId = groupId,
        competitionId = competitionId,
        title = title,
        gender = gender?.name,
        minAge = minAge,
        maxAge = maxAge,
        distanceId = distanceId,
        maxParticipants = maxParticipants,
        timeLimitMinutes = timeLimitMinutes,
        scorePenaltyPerMinute = scorePenaltyPerMinute,
        maxLatenessMinutes = maxLatenessMinutes,
        serverUpdatedAt = serverUpdatedAt
    )
}

fun Distance.toRequest(): DistanceRequest {
    return DistanceRequest(
        distanceId = remoteId,
        competitionId = competitionId,
        name = name,
        lengthMeters = lengthMeters,
        climbMeters = climbMeters,
        controlsCount = controlsCount,
        description = description,
        controlPoints = controlPoints.map { it.toRequest() },
        finishControlPoint = finishControlPoint,
        startControlPoint = startControlPoint,
        serverUpdatedAt = serverUpdatedAt
    )
}

fun ControlPoint.toRequest(): ControlPointRequest {
    return ControlPointRequest(
        number = number,
        role = role,
        score = score,
        latitude = latitude,
        longitude = longitude
    )
}

fun OrienteeringParticipant.toRequest(): OrienteeringParticipantRequest {
    return OrienteeringParticipantRequest(
        id = id,
        userId = userId.ifEmpty { null },
        firstName = firstName,
        lastName = lastName,
        groupId = groupId,
        groupName = groupName,
        competitionId = competitionId,
        commandName = commandName.ifEmpty { null },
        startNumber = startNumber.toIntOrNull() ?: 0,
        startTime = startTime,
        chipNumber = chipNumber.toLongOrNull() ?: 0L,
        comment = comment.ifEmpty { null },
        isChipGiven = isChipGiven,
        serverUpdatedAt = serverUpdatedAt
    )
}

fun OrienteeringResult.toRequest(): OrienteeringResultRequest {
    return OrienteeringResultRequest(
        id = requireNotNull(remoteId) { "OrienteeringResult.remoteId must be assigned before syncing to server" },
        competitionId = competitionId,
        groupId = groupId,
        participantId = participantId,
        startTime = startTime,
        finishTime = finishTime,
        totalTime = totalTime,
        rank = rank,
        status = status.name,
        penaltyTime = penaltyTime,
        totalScore = totalScore,
        scorePenalty = scorePenalty,
        splits = splits?.map { SplitTimeRequest(it.controlPoint, it.timestamp) },
        isEditable = isEditable,
        isEdited = isEdited,
        serverUpdatedAt = serverUpdatedAt
    )
}