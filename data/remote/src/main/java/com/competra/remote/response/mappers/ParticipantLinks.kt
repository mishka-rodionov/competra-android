package com.competra.remote.response.mappers

import com.competra.domain.models.participant_link.CompetitionLinkRequest
import com.competra.domain.models.participant_link.LinkRequest
import com.competra.domain.models.participant_link.LinkRequestSource
import com.competra.domain.models.participant_link.LinkRequestStatus
import com.competra.domain.models.participant_link.LinkResultSummary
import com.competra.domain.models.participant_link.LinkSuggestion
import com.competra.remote.response.orienteering.CompetitionLinkRequestResponse
import com.competra.remote.response.orienteering.LinkRequestResponse
import com.competra.remote.response.orienteering.LinkResultSummaryResponse
import com.competra.remote.response.orienteering.LinkSuggestionResponse

fun LinkResultSummaryResponse.toDomain() = LinkResultSummary(
    rank = rank,
    totalTime = totalTime,
    totalScore = totalScore,
    status = status
)

fun LinkSuggestionResponse.toDomain() = LinkSuggestion(
    participantId = participantId,
    competitionId = competitionId,
    competitionTitle = competitionTitle,
    competitionStartDate = competitionStartDate,
    firstName = firstName,
    lastName = lastName,
    groupName = groupName,
    commandName = commandName,
    result = result?.toDomain()
)

fun LinkRequestResponse.toDomain() = LinkRequest(
    id = id,
    participantId = participantId,
    competitionId = competitionId,
    competitionTitle = competitionTitle,
    competitionStartDate = competitionStartDate,
    participantFirstName = participantFirstName,
    participantLastName = participantLastName,
    groupName = groupName,
    status = LinkRequestStatus.from(status),
    source = LinkRequestSource.from(source),
    comment = comment,
    createdAt = createdAt
)

fun CompetitionLinkRequestResponse.toDomain() = CompetitionLinkRequest(
    id = id,
    status = LinkRequestStatus.from(status),
    source = LinkRequestSource.from(source),
    comment = comment,
    createdAt = createdAt,
    participantId = participantId,
    participantFirstName = participantFirstName,
    participantLastName = participantLastName,
    groupName = groupName,
    commandName = commandName,
    startNumber = startNumber,
    result = result?.toDomain(),
    userId = userId,
    userFirstName = userFirstName,
    userLastName = userLastName,
    userBirthYear = userBirthYear,
    userGender = userGender,
    nameMatches = nameMatches,
    eligibilityWarning = eligibilityWarning,
    competingRequests = competingRequests,
    userAlreadyInCompetition = userAlreadyInCompetition
)
