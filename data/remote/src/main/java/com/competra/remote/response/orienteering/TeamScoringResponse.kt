package com.competra.remote.response.orienteering

import com.competra.domain.models.orienteering.TeamOverallScope
import com.competra.domain.models.orienteering.TeamScoring
import com.competra.domain.models.orienteering.TeamScoringMethod
import com.google.gson.annotations.SerializedName

/** Настройки командного зачёта в ответе соревнования; отсутствие поля — зачёта нет. */
data class TeamScoringResponse(
    @SerializedName("groupMethod") val groupMethod: String? = null,
    @SerializedName("groupCountedResults") val groupCountedResults: Int? = null,
    @SerializedName("overallScopes") val overallScopes: List<String>? = null
)

fun TeamScoringResponse.toDomain(): TeamScoring = TeamScoring(
    groupMethod = TeamScoringMethod.fromString(groupMethod),
    groupCountedResults = groupCountedResults?.takeIf { it > 0 } ?: TeamScoring.DEFAULT_COUNTED_RESULTS,
    overallScopes = overallScopes.orEmpty().mapNotNull(TeamOverallScope::fromString).toSet()
)
