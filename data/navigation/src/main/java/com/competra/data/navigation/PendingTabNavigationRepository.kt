package com.competra.data.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Отложенный переход на экран другого таба (тап на push-уведомление, переход из деталей события
 * в карточку клуба) — сохраняется вместе с переключением таба (см. [Navigation.switchTab]), т.к.
 * `Navigation.navigate` эмитит в SharedFlow без буфера: если NavHost целевого таба ещё не подписан
 * на события (таб только переключается), событие будет потеряно. StateFlow здесь конфлейтит и отдаёт
 * последнее значение новому подписчику, поэтому гонка отсутствует независимо от того, что раньше —
 * set() или подписка.
 */
class PendingTabNavigationRepository {

    private val _pending = MutableStateFlow<PendingTabRoute?>(null)
    val pending: StateFlow<PendingTabRoute?> = _pending.asStateFlow()

    /**
     * @param tabRoute Таб, в NavHost-е которого зарегистрирован [route] (см. [TabRoutes]).
     * @param route Роут, на который перейти, когда таб станет активным.
     */
    fun set(tabRoute: String, route: BaseNavigation) {
        _pending.value = PendingTabRoute(tabRoute, route)
    }

    fun clear() {
        _pending.value = null
    }
}

/**
 * Отложенный переход: [route] выполняет NavHost таба [tabRoute].
 *
 * @property tabRoute Таб нижней навигации (см. [TabRoutes]).
 * @property route Целевой роут.
 */
data class PendingTabRoute(val tabRoute: String, val route: BaseNavigation)
