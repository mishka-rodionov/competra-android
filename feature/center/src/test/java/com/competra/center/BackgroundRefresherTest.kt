package com.competra.center

import com.competra.center.data.refresh.AUTO_REFRESH_MIN_INTERVAL_MS
import com.competra.center.data.refresh.BACKGROUND_REFRESH_TIMEOUT_MS
import com.competra.center.data.refresh.BackgroundRefresher
import com.competra.center.data.refresh.RemoteRefreshState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Фоновое обновление экрана без блокирующего лоадера [BackgroundRefresher].
 */
class BackgroundRefresherTest {

    private fun TestScope.refresher(load: suspend () -> Boolean?) =
        BackgroundRefresher(scope = this, clock = { testScheduler.currentTime }, load = load)

    @Test
    fun `успешная загрузка проставляет время обновления`() = runTest {
        val refresher = refresher { true }
        refresher.refresh()
        assertTrue(refresher.state.value.isRefreshing)

        advanceUntilIdle()

        val state = refresher.state.value
        assertFalse(state.isRefreshing)
        assertFalse(state.isFailed)
        assertEquals(testScheduler.currentTime, state.lastUpdatedAt)
    }

    @Test
    fun `сервер не ответил — статус нет связи`() = runTest {
        val refresher = refresher { false }
        refresher.refresh()
        advanceUntilIdle()

        assertTrue(refresher.state.value.isFailed)
        assertNull(refresher.state.value.lastUpdatedAt)
    }

    @Test
    fun `исключение в загрузке — статус нет связи`() = runTest {
        val refresher = refresher { error("boom") }
        refresher.refresh()
        advanceUntilIdle()

        assertTrue(refresher.state.value.isFailed)
        assertFalse(refresher.state.value.isRefreshing)
    }

    @Test
    fun `зависшая загрузка обрывается по таймауту`() = runTest {
        val refresher = refresher { awaitCancellation() }
        refresher.refresh()

        advanceTimeBy(BACKGROUND_REFRESH_TIMEOUT_MS - 1)
        runCurrent()
        assertTrue(refresher.state.value.isRefreshing)

        advanceTimeBy(2)
        runCurrent()
        assertFalse(refresher.state.value.isRefreshing)
        assertTrue(refresher.state.value.isFailed)
    }

    @Test
    fun `обновлять нечего — статус не меняется`() = runTest {
        val refresher = refresher { null }
        refresher.refresh()
        advanceUntilIdle()

        val state = refresher.state.value
        assertFalse(state.isRefreshing)
        assertFalse(state.isFailed)
        assertNull(state.lastUpdatedAt)
    }

    @Test
    fun `автообновление не чаще минимального интервала`() = runTest {
        var calls = 0
        val refresher = refresher { calls++; true }

        refresher.refreshIfStale()
        advanceUntilIdle()
        refresher.refreshIfStale()
        advanceUntilIdle()
        assertEquals(1, calls)

        advanceTimeBy(AUTO_REFRESH_MIN_INTERVAL_MS)
        refresher.refreshIfStale()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `после неудачи автообновление запускается сразу`() = runTest {
        var calls = 0
        val refresher = refresher { calls++; false }

        refresher.refreshIfStale()
        advanceUntilIdle()
        refresher.refreshIfStale()
        advanceUntilIdle()

        assertEquals(2, calls)
    }

    @Test
    fun `повторный запрос во время загрузки не запускает вторую`() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Boolean>()
        val refresher = refresher { calls++; gate.await() }

        refresher.refresh()
        runCurrent()
        refresher.refresh(isPull = true)
        runCurrent()
        assertEquals(1, calls)
        // Жест во время фоновой загрузки показывает её индикатор до завершения.
        assertTrue(refresher.state.value.isPullRefresh)

        gate.complete(true)
        advanceUntilIdle()
        assertFalse(refresher.state.value.isPullRefresh)
        assertEquals(1, calls)
    }

    @Test
    fun `сброс отменяет загрузку и снимает троттлинг`() = runTest {
        var calls = 0
        val refresher = refresher { calls++; if (calls == 1) true else awaitCancellation() }
        refresher.refresh()
        advanceUntilIdle()
        refresher.refresh()
        runCurrent()

        refresher.reset()
        // Статус прошлого аккаунта не показывается, зависшая загрузка отменена.
        assertEquals(RemoteRefreshState(), refresher.state.value)

        // Новый пользователь получает загрузку сразу, без ожидания интервала автообновления.
        refresher.refreshIfStale()
        runCurrent()
        assertEquals(3, calls)
        assertTrue(refresher.state.value.isRefreshing)
        refresher.reset()
    }
}
