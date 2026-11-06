package com.competra.domain.repository.rating

import com.competra.domain.models.PagedResult
import com.competra.domain.models.rating.AddCompetitionToRatingResult
import com.competra.domain.models.rating.RatingCompetition
import com.competra.domain.models.rating.RatingGroup
import com.competra.domain.models.rating.RatingGroupMappingSuggestion
import com.competra.domain.models.rating.RatingSeries
import com.competra.domain.models.rating.RatingStanding
import com.competra.domain.models.rating.RatingStandings
import com.competra.domain.models.rating.RatingSummary

/** Рейтинги соревнований работают только online — без offline-first/`:core:sync`, ошибки сети — обычный [Result.failure]. */
interface RatingRepository {

    /** Глобальный поиск по всем рейтингам (без привязки к клубу) — для экрана "Все рейтинги". */
    suspend fun search(query: String?, page: Int, limit: Int): Result<PagedResult<RatingSummary>>

    suspend fun listForClub(clubId: String): Result<List<RatingSeries>>

    suspend fun getById(ratingId: String): Result<RatingSeries>

    suspend fun create(clubId: String, name: String, groups: List<RatingGroup>): Result<RatingSeries>

    suspend fun update(ratingId: String, name: String, groups: List<RatingGroup>): Result<RatingSeries>

    /** Только FOUNDER/ADMIN клуба-владельца. */
    suspend fun delete(ratingId: String): Result<Unit>

    suspend fun listCompetitions(ratingId: String): Result<List<RatingCompetition>>

    /** Возвращает добавленное соревнование вместе с авто-подсказкой маппинга его групп на канонические группы рейтинга. */
    suspend fun addCompetition(ratingId: String, competitionId: String): Result<AddCompetitionToRatingResult>

    suspend fun removeCompetition(ratingId: String, competitionId: String): Result<Unit>

    /** Подсказка маппинга: уже сохранённое соответствие, если есть, иначе авто-подсказка по gender+возрасту. */
    suspend fun getGroupMappingSuggestions(ratingId: String, competitionId: String): Result<List<RatingGroupMappingSuggestion>>

    /** Перезаписывает маппинг групп соревнования [competitionId] в рейтинге [ratingId] целиком. */
    suspend fun setGroupMapping(ratingId: String, competitionId: String, mappings: Map<Long, Long>): Result<Unit>

    suspend fun getStandings(ratingId: String, ratingGroupId: Long): Result<RatingStandings>

    /**
     * Объединить дубли таблицы рейтинга: участники [sourceKeys] привязываются к аккаунту строки
     * [targetKey] или переименовываются в её ФИО. Возвращает число изменённых участников.
     */
    suspend fun mergeStandings(ratingId: String, ratingGroupId: Long, targetKey: String, sourceKeys: List<String>): Result<Int>
}
