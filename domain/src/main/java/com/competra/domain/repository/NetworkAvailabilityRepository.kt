package com.competra.domain.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Репозиторий событий «появилась сеть» для всего приложения.
 * Источник — системный колбэк ConnectivityManager (регистрируется в `CompetraApp`); экраны
 * подписываются, чтобы сразу дотянуть с сервера данные, которые не удалось загрузить офлайн.
 */
class NetworkAvailabilityRepository {
    private val _availableEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Поток событий появления сети. Подписчики получают только события после подписки.
     */
    val availableEvents: SharedFlow<Unit> = _availableEvents.asSharedFlow()

    /**
     * Сообщает о появлении сети. Безопасно вызывать из системного колбэка (не suspend).
     */
    fun notifyAvailable() {
        _availableEvents.tryEmit(Unit)
    }
}
