package com.competra.remote.response.mappers

import com.competra.domain.models.Gender
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.ControlPoint
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.SplitTime
import com.competra.remote.response.orienteering.ControlPointResponse
import com.competra.remote.response.orienteering.DistanceResponse
import com.competra.remote.response.orienteering.OrienteeringCompetitionResponse
import com.competra.remote.response.orienteering.OrienteeringParticipantResponse
import com.competra.remote.response.orienteering.OrienteeringResultResponse
import com.competra.remote.response.orienteering.ParticipantGroupResponse

/**
 * Преобразует ответ сервера по соревнованию по ориентированию в доменную модель.
 */
fun OrienteeringCompetitionResponse.toDomain(): OrienteeringCompetition {
    return OrienteeringCompetition(
        competitionId = competitionId,
        competition = competition.toDomain(),
        direction = OrienteeringDirection.valueOf(direction),
        punchingSystem = punchingSystem,
        startTimeMode = startTimeMode,
        countdownTimer = countdownTimer,
        startTime = startTime,
        startIntervalSeconds = startIntervalSeconds,
        serverUpdatedAt = updatedAt.takeIf { it > 0L }
    )
}

/**
 * Преобразует ответ сервера по группе участников в доменную модель.
 * groupId из ответа — UUID строка, сохраняется в remoteId.
 * Локальный groupId неизвестен на этом уровне, устанавливается при zip-сопоставлении.
 */
fun ParticipantGroupResponse.toDomain() : ParticipantGroup {
    return ParticipantGroup(
        groupId = 0L,
        competitionId = "",
        title = title,
        gender = gender?.let {
            try {
                Gender.valueOf(it)
            } catch (e: Exception) {
                null
            }
        },
        minAge = minAge,
        maxAge = maxAge,
        distanceId = distanceId,
        maxParticipants = maxParticipants,
        timeLimitMinutes = timeLimitMinutes,
        scorePenaltyPerMinute = scorePenaltyPerMinute,
        maxLatenessMinutes = maxLatenessMinutes,
        remoteId = groupId,
        isSynced = true,
        lastModified = System.currentTimeMillis(),
        serverUpdatedAt = updatedAt.takeIf { it > 0L }
    )
}

/**
 * Преобразует ответ сервера по дистанции в доменную модель.
 * competitionId — UUID соревнования (единый id на клиенте и сервере).
 */
fun DistanceResponse.toDomain(competitionId: String): Distance {
    return Distance(
        remoteId = id,
        competitionId = competitionId,
        name = name,
        lengthMeters = lengthMeters,
        climbMeters = climbMeters,
        controlsCount = controlsCount,
        description = description,
        isSynced = true,
        serverUpdatedAt = updatedAt.takeIf { it > 0L },
        controlPoints = controlPoints.map { it.toDomain() },
        finishControlPoint = finishControlPoint,
        startControlPoint = startControlPoint
    )
}

/**
 * Преобразует ответ сервера по контрольному пункту в доменную модель.
 */
fun ControlPointResponse.toDomain() : ControlPoint {
    return ControlPoint(
        number = number,
        role = role,
        score = score,
        latitude = latitude,
        longitude = longitude
    )
}

/**
 * Преобразует ответ сервера по участнику в доменную модель.
 */
fun OrienteeringParticipantResponse.toDomain(): OrienteeringParticipant {
    return OrienteeringParticipant(
        id = id.orEmpty(),
        userId = userId.orEmpty(),
        firstName = firstName.orEmpty(),
        lastName = lastName.orEmpty(),
        groupId = groupId ?: 0L,
        groupName = groupName.orEmpty(),
        competitionId = competitionId.orEmpty(),
        commandName = commandName.orEmpty(),
        startNumber = startNumber.orEmpty(),
        startTime = startTime ?: 0L,
        chipNumber = chipNumber.orEmpty(),
        comment = comment.orEmpty(),
        isChipGiven = isChipGiven ?: false,
        isSynced = true,
        remoteId = id,    // server id сохраняем для последующего upsert
        serverUpdatedAt = updatedAt.takeIf { it > 0L }
    )
}

/**
 * Преобразует ответ сервера по результату в доменную модель.
 */
fun OrienteeringResultResponse.toDomain(): OrienteeringResult {
    return OrienteeringResult(
        remoteId = id,
        competitionId = competitionId,
        groupId = groupId,
        participantId = participantId,
        startTime = startTime,
        finishTime = finishTime,
        totalTime = totalTime,
        status = try { ResultStatus.valueOf(status) } catch (e: Exception) { ResultStatus.DNS },
        penaltyTime = penaltyTime,
        totalScore = totalScore,
        scorePenalty = scorePenalty,
        rank = rank,
        splits = splits?.map { SplitTime(it.controlPoint, it.timestamp) },
        isEditable = isEditable,
        isEdited = isEdited,
        isSynced = true,
        serverUpdatedAt = updatedAt.takeIf { it > 0L }
    )
}
