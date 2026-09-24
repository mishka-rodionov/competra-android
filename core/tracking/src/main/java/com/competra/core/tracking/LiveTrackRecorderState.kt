package com.competra.core.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Живое состояние записи, которого нет в Room: работает ли сервис, есть ли GPS, как прошла
 * последняя отправка.
 *
 * @property recordingSessionId Сессия, которую сейчас пишет сервис, или `null`, если сервис не пишет.
 * @property gpsAvailable Разрешение есть и GPS включён.
 * @property lastFixAt Время последнего принятого фикса (Unix ms).
 * @property lastFixAccuracy Точность последнего фикса, м.
 * @property lastUploadAt Время последней успешной отправки (Unix ms).
 * @property lastUploadFailed Последняя попытка отправки не удалась (нет сети / сервер недоступен).
 */
data class LiveTrackRecorderSnapshot(
    val recordingSessionId: String? = null,
    val gpsAvailable: Boolean = true,
    val lastFixAt: Long? = null,
    val lastFixAccuracy: Float? = null,
    val lastUploadAt: Long? = null,
    val lastUploadFailed: Boolean = false
)

/** Держатель [LiveTrackRecorderSnapshot]: пишет сервис/отправщик, читает UI. */
class LiveTrackRecorderState {
    private val _snapshot = MutableStateFlow(LiveTrackRecorderSnapshot())
    val snapshot: StateFlow<LiveTrackRecorderSnapshot> = _snapshot.asStateFlow()

    fun onRecordingStarted(sessionId: String, gpsAvailable: Boolean) =
        _snapshot.update { it.copy(recordingSessionId = sessionId, gpsAvailable = gpsAvailable) }

    fun onRecordingStopped() = _snapshot.update { it.copy(recordingSessionId = null) }

    fun onFix(at: Long, accuracy: Float?) = _snapshot.update { it.copy(lastFixAt = at, lastFixAccuracy = accuracy, gpsAvailable = true) }

    fun onGpsUnavailable() = _snapshot.update { it.copy(gpsAvailable = false) }

    fun onUploaded(at: Long) = _snapshot.update { it.copy(lastUploadAt = at, lastUploadFailed = false) }

    fun onUploadFailed() = _snapshot.update { it.copy(lastUploadFailed = true) }
}
