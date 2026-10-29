package com.competra.domain.repository.livetrack

import com.competra.domain.models.livetrack.MyStart
import kotlinx.coroutines.flow.Flow

/**
 * Соревнования, на которые зарегистрирован пользователь: копия с сервера в Room, чтобы карточка
 * «Сегодня старт» и запуск онлайн-трека работали без интернета.
 */
interface MyStartsRepository {

    /** Все закэшированные соревнования пользователя. */
    fun observe(): Flow<List<MyStart>>

    /** Соревнование из кэша. */
    suspend fun get(competitionId: String): MyStart?

    /** Перезагружает список с сервера; при ошибке (нет сети, не авторизован) кэш не трогается. */
    suspend fun refresh(): Result<Unit>

    /** Очищает кэш (выход из аккаунта). */
    suspend fun clear()
}
