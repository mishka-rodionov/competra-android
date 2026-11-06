package com.competra.rating.data.detail

import com.competra.domain.models.rating.RatingCompetition
import com.competra.domain.models.rating.RatingSeries
import com.competra.domain.models.rating.RatingStanding
import com.competra.domain.models.rating.RatingStandings
import com.competra.ui.BaseState

data class RatingDetailState(
    val ratingId: String = "",
    val rating: RatingSeries? = null,
    val selectedGroupId: Long? = null,
    val standings: List<RatingStanding> = emptyList(),
    /** Подсказанные сервером дубли текущей группы — строки, похожие на одного человека. */
    val duplicateGroups: List<List<RatingStanding>> = emptyList(),
    /** Кэш standings по группе на время жизни экрана — переключение вкладок не бьёт по сети повторно. */
    val standingsByGroup: Map<Long, RatingStandings> = emptyMap(),
    val competitions: List<RatingCompetition> = emptyList(),
    val isAdmin: Boolean = false,
    val isLoading: Boolean = false,
    val isStandingsLoading: Boolean = false,
    val isDeleteConfirmOpen: Boolean = false,
    val isDeleting: Boolean = false,
    /** Ручной выбор строк для объединения (дубль не подсказан — например, опечатка в фамилии). */
    val isSelectingForMerge: Boolean = false,
    val selectedKeys: Set<String> = emptySet(),
    /** Строки в открытом диалоге объединения; null — диалог закрыт. */
    val mergeEntries: List<RatingStanding>? = null,
    val mergeTargetKey: String? = null,
    val isMerging: Boolean = false,
    val mergeError: String? = null
) : BaseState

/** Основная запись по умолчанию: с аккаунтом, иначе с самым полным именем. */
fun defaultMergeTarget(entries: List<RatingStanding>): RatingStanding? =
    entries.firstOrNull { it.userId != null }
        ?: entries.sortedWith(compareByDescending<RatingStanding> { it.firstName.length }.thenByDescending { it.breakdown.size })
            .firstOrNull()

/** Почему основную запись [target] нельзя объединить с остальными; null — можно. */
fun mergeProblem(target: RatingStanding, entries: List<RatingStanding>): String? {
    val sources = entries.filter { it.participantKey != target.participantKey }
    return when {
        sources.isEmpty() -> "Выберите хотя бы две строки"
        target.userId != null && sources.any { it.userId != null } -> "Нельзя объединить два аккаунта"
        target.userId == null && sources.any { it.userId != null } ->
            "Основной выберите строку с аккаунтом — к ней привяжутся остальные"
        else -> null
    }
}
