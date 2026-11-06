package com.competra.remote.request.rating

import com.google.gson.annotations.SerializedName

/** Объединить дубли в таблице рейтинга (eSport: POST /ratings/{id}/standings/merge). */
data class MergeStandingsRequest(
    @SerializedName("ratingGroupId") val ratingGroupId: Long,
    @SerializedName("targetKey") val targetKey: String,
    @SerializedName("sourceKeys") val sourceKeys: List<String>
)
