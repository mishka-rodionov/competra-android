package com.competra.remote.response.mappers

import com.competra.domain.models.Gender
import com.competra.domain.models.rating.AddCompetitionToRatingResult
import com.competra.domain.models.rating.RatingCompetition
import com.competra.domain.models.rating.RatingGroup
import com.competra.domain.models.rating.RatingGroupMappingSuggestion
import com.competra.domain.models.rating.RatingSeries
import com.competra.domain.models.rating.RatingStanding
import com.competra.domain.models.rating.RatingStandingBreakdownEntry
import com.competra.domain.models.rating.RatingStandings
import com.competra.domain.models.rating.RatingSummary
import com.competra.remote.response.rating.AddCompetitionToRatingResponse
import com.competra.remote.response.rating.RatingCompetitionResponse
import com.competra.remote.response.rating.RatingGroupMappingSuggestionResponse
import com.competra.remote.response.rating.RatingGroupResponse
import com.competra.remote.response.rating.RatingResponse
import com.competra.remote.response.rating.RatingSearchResponse
import com.competra.remote.response.rating.RatingStandingEntryResponse
import com.competra.remote.response.rating.RatingStandingsResponse

private fun String?.toGenderOrNull(): Gender? = this?.let {
    try {
        Gender.valueOf(it)
    } catch (e: Exception) {
        null
    }
}

fun RatingGroupResponse.toDomain() = RatingGroup(
    id = id,
    ratingId = ratingId,
    title = title,
    gender = gender.toGenderOrNull(),
    minAge = minAge,
    maxAge = maxAge,
    orderIndex = orderIndex
)

fun RatingResponse.toDomain() = RatingSeries(
    id = id,
    name = name,
    ownerClubId = ownerClubId,
    groups = groups.map { it.toDomain() },
    createdAt = createdAt
)

fun RatingCompetitionResponse.toDomain() = RatingCompetition(
    id = id,
    ratingId = ratingId,
    competitionId = competitionId,
    competitionTitle = competitionTitle,
    competitionStartDate = competitionStartDate,
    addedAt = addedAt
)

fun RatingGroupMappingSuggestionResponse.toDomain() = RatingGroupMappingSuggestion(
    participantGroupId = participantGroupId,
    participantGroupTitle = participantGroupTitle,
    suggestedRatingGroupId = suggestedRatingGroupId,
    confidence = confidence
)

fun AddCompetitionToRatingResponse.toDomain() = AddCompetitionToRatingResult(
    ratingCompetition = ratingCompetition.toDomain(),
    groupMappingSuggestions = groupMappingSuggestions.map { it.toDomain() }
)

fun RatingStandingEntryResponse.toDomain() = RatingStanding(
    participantKey = participantKey,
    displayName = displayName,
    totalPoints = totalPoints,
    rank = rank,
    breakdown = breakdown.map {
        RatingStandingBreakdownEntry(it.competitionId, it.place, it.points, it.participantId.orEmpty())
    },
    userId = userId?.takeIf { it.isNotBlank() },
    firstName = firstName.orEmpty(),
    lastName = lastName.orEmpty()
)

fun RatingStandingsResponse.toDomain() = RatingStandings(
    standings = standings.map { it.toDomain() },
    possibleDuplicates = possibleDuplicates.orEmpty()
)

fun RatingSearchResponse.toDomain() = RatingSummary(
    id = id,
    name = name,
    ownerClubId = ownerClubId,
    ownerClubName = ownerClubName,
    createdAt = createdAt
)
