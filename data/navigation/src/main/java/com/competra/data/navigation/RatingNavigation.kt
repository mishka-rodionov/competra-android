package com.competra.data.navigation

import androidx.navigation.NavOptionsBuilder
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Навигация для модуля рейтингов соревнований. Точка входа — из карточки клуба-владельца.
 */
@Serializable
sealed class RatingNavigation : BaseNavigation {

    @Transient
    @Contextual
    override var navOptionsBuilder: (NavOptionsBuilder.() -> Unit)? = null

    /** Глобальный поиск по всем рейтингам, без привязки к клубу. */
    @Serializable
    data object RatingsSearchRoute : RatingNavigation()

    /** Список рейтингов клуба [clubId]. */
    @Serializable
    data class RatingListRoute(val clubId: String) : RatingNavigation()

    /** Создание нового рейтинга для клуба [clubId]. */
    @Serializable
    data class CreateRatingRoute(val clubId: String) : RatingNavigation()

    /** Детали рейтинга: таблица результатов по группам, список соревнований. */
    @Serializable
    data class RatingDetailRoute(val ratingId: String) : RatingNavigation()

    /** Поиск и добавление соревнования в состав рейтинга. */
    @Serializable
    data class AddCompetitionRoute(val ratingId: String) : RatingNavigation()

    /** Подтверждение/правка маппинга групп соревнования [competitionId] на канонические группы рейтинга [ratingId]. */
    @Serializable
    data class GroupMappingRoute(val ratingId: String, val competitionId: String) : RatingNavigation()

    /** Старты спортсмена [participantKey] в группе [groupId] рейтинга [ratingId] (соревнование + занятое место). */
    @Serializable
    data class AthleteStartsRoute(val ratingId: String, val groupId: Long, val participantKey: String) : RatingNavigation()
}
