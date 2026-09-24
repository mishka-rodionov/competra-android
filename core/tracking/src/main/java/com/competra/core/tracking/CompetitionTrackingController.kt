package com.competra.core.tracking

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Управление foreground-сервисом онлайн-трекинга из UI-модулей без зависимости на `:app`
 * (по образцу `WorkoutTrackingController`): команды слушает `MainActivity` и стартует сервис.
 */
interface CompetitionTrackingController {
    val commands: SharedFlow<CompetitionTrackingCommand>

    /** Запустить (или возобновить) запись GPS для сессии. */
    suspend fun startRecording(sessionId: String)

    /** Бегун остановил трекинг: сервис прекращает GPS и досылает буфер. */
    suspend fun stopRecording(sessionId: String)
}

/** Команда сервису онлайн-трекинга. */
sealed class CompetitionTrackingCommand {
    abstract val sessionId: String

    data class StartRecording(override val sessionId: String) : CompetitionTrackingCommand()
    data class StopRecording(override val sessionId: String) : CompetitionTrackingCommand()
}

/** Реализация на [MutableSharedFlow]: команда дойдёт до `MainActivity`, пока она на экране. */
class CompetitionTrackingControllerImpl : CompetitionTrackingController {
    private val _commands = MutableSharedFlow<CompetitionTrackingCommand>(extraBufferCapacity = 4)
    override val commands: SharedFlow<CompetitionTrackingCommand> = _commands.asSharedFlow()

    override suspend fun startRecording(sessionId: String) = _commands.emit(CompetitionTrackingCommand.StartRecording(sessionId))

    override suspend fun stopRecording(sessionId: String) = _commands.emit(CompetitionTrackingCommand.StopRecording(sessionId))
}
