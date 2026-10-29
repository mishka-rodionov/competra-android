package com.competra.events.data.main

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.competra.domain.models.Competition
import com.competra.domain.models.events.EventsFilter
import com.competra.domain.repository.events.EventsRepository

/** Размер страницы публичного списка событий. Должен совпадать с limit, который уходит на бэкенд. */
const val EVENTS_PAGE_SIZE = 20

/**
 * Источник страниц публичного списка событий. Пересоздаётся при смене [filter] (см. EventsViewModel).
 *
 * Индикатор загрузки ([onRefreshLoading]) и глобальный диалог сетевой ошибки ([onRefreshError])
 * задействуются только на первой странице (refresh) — ошибка догрузки следующей страницы
 * во время скролла показывается локально в футере списка, без модального диалога. Какой
 * индикатор показать (глобальный лоадер или pull-to-refresh), решает ViewModel.
 */
class EventsPagingSource(
    private val eventsRepository: EventsRepository,
    private val filter: EventsFilter,
    private val onRefreshLoading: suspend (Boolean) -> Unit,
    private val onRefreshError: (Throwable) -> Unit
) : PagingSource<Int, Competition>() {

    override fun getRefreshKey(state: PagingState<Int, Competition>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val anchorPage = state.closestPageToPosition(anchorPosition) ?: return null
        return anchorPage.prevKey?.plus(1) ?: anchorPage.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Competition> {
        val page = params.key ?: 0
        val isRefresh = params is LoadParams.Refresh
        if (isRefresh) onRefreshLoading(true)

        val result = eventsRepository.getEvents(filter = filter, page = page, limit = EVENTS_PAGE_SIZE)

        if (isRefresh) onRefreshLoading(false)

        return result.fold(
            onSuccess = { paged ->
                LoadResult.Page(
                    data = paged.items,
                    prevKey = if (page == 0) null else page - 1,
                    nextKey = if (paged.hasMore) page + 1 else null
                )
            },
            onFailure = { throwable ->
                if (isRefresh) onRefreshError(throwable)
                LoadResult.Error(throwable)
            }
        )
    }
}
