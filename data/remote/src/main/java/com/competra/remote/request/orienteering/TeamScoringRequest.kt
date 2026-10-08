package com.competra.remote.request.orienteering

import com.google.gson.annotations.SerializedName

/**
 * Командный зачёт в запросе соревнования: `enabled = false` — выключить. Android шлёт поле всегда
 * (для сервера `null` значит «не менять» — так ведут себя старые версии приложения).
 */
data class TeamScoringRequest(
    @SerializedName("enabled") val enabled: Boolean,
    /** POINTS / TIME — способ подсчёта в группе. */
    @SerializedName("groupMethod") val groupMethod: String? = null,
    /** N — сколько лучших результатов команды в группе идёт в зачёт. */
    @SerializedName("groupCountedResults") val groupCountedResults: Int? = null,
    /** MEN / WOMEN / ALL — общие зачёты. */
    @SerializedName("overallScopes") val overallScopes: List<String>? = null
)
