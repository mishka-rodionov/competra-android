package com.competra.center.data.refresh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Предельное время одной фоновой загрузки с сервера. Экран при этом не заблокирован, поэтому
 * лимит щедрый — на старте связь бывает медленной, но рабочей; по истечении показываем статус «Офлайн».
 */
const val BACKGROUND_REFRESH_TIMEOUT_MS = 45_000L

/**
 * Минимальный интервал между автоматическими обновлениями (вход на экран, возврат в приложение),
 * чтобы переходы между экранами не дёргали сервер каждый раз.
 */
const val AUTO_REFRESH_MIN_INTERVAL_MS = 30_000L

/**
 * Состояние фонового обновления данных экрана с сервера.
 *
 * @property isRefreshing Идёт загрузка с сервера.
 * @property isPullRefresh Загрузку запустил жест pull-to-refresh — индикатор показывает сам жест.
 * @property lastUpdatedAt Время последней успешной загрузки (Unix ms); null — в этой сессии экрана ещё не было.
 * @property isFailed Последняя попытка не удалась (нет связи, ошибка сервера или таймаут) —
 *   на экране сохранённые локально данные, статус «Офлайн».
 */
data class RemoteRefreshState(
    val isRefreshing: Boolean = false,
    val isPullRefresh: Boolean = false,
    val lastUpdatedAt: Long? = null,
    val isFailed: Boolean = false
)

/**
 * Фоновое обновление данных экрана с сервера без блокирующего лоудера: экран сразу показывает
 * локальные данные, а [load] дотягивает серверные в БД. Одновременно выполняется не больше одной
 * загрузки; каждая ограничена [timeoutMs].
 *
 * @param scope Скоуп загрузок (обычно viewModelScope).
 * @param timeoutMs Лимит одной загрузки.
 * @param minAutoIntervalMs Минимальный интервал между автоматическими обновлениями.
 * @param clock Источник текущего времени (подменяется в тестах).
 * @param load Загрузка: true — успех, false — сервер недоступен или ответил ошибкой,
 *   null — обновлять нечего (например, соревнование ещё не выгружено на сервер), статус не меняется.
 */
class BackgroundRefresher(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = BACKGROUND_REFRESH_TIMEOUT_MS,
    private val minAutoIntervalMs: Long = AUTO_REFRESH_MIN_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val load: suspend () -> Boolean?
) {
    private val _state = MutableStateFlow(RemoteRefreshState())

    /** Текущее состояние обновления для отображения на экране. */
    val state: StateFlow<RemoteRefreshState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastAttemptAt: Long? = null

    /**
     * Обновление по запросу пользователя (pull-to-refresh) — без троттлинга.
     *
     * @param isPull true — запущено жестом pull-to-refresh.
     */
    fun refresh(isPull: Boolean = false) {
        if (job?.isActive == true) {
            // Загрузка уже идёт — жест просто показывает её индикатор до завершения.
            if (isPull) _state.update { it.copy(isPullRefresh = true) }
            return
        }
        lastAttemptAt = clock()
        _state.update { it.copy(isRefreshing = true, isPullRefresh = isPull) }
        job = scope.launch {
            val loaded = try {
                withTimeoutOrNull(timeoutMs) { Loaded(load()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Loaded(false)
            }
            // null от withTimeoutOrNull — таймаут: его отличаем от «обновлять нечего» (Loaded(null)).
            val success = if (loaded == null) false else loaded.value
            _state.update {
                val done = it.copy(isRefreshing = false, isPullRefresh = false)
                when (success) {
                    true -> done.copy(lastUpdatedAt = clock(), isFailed = false)
                    false -> done.copy(isFailed = true)
                    null -> done
                }
            }
        }
    }

    /**
     * Сбрасывает состояние, как будто обновлений ещё не было, и отменяет идущую загрузку — например,
     * при смене аккаунта: статус и троттлинг прошлого пользователя к новому не относятся.
     */
    fun reset() {
        job?.cancel()
        job = null
        lastAttemptAt = null
        _state.value = RemoteRefreshState()
    }

    /** Обёртка результата [load], чтобы отличить его null от таймаута. */
    private class Loaded(val value: Boolean?)

    /**
     * Автоматическое обновление (показ экрана, возврат в приложение, появление сети): не чаще
     * [minAutoIntervalMs]; после неудачной попытки — сразу, чтобы быстрее дотянуть данные.
     */
    fun refreshIfStale() {
        val last = lastAttemptAt
        if (last != null && !_state.value.isFailed && clock() - last < minAutoIntervalMs) return
        refresh()
    }
}
