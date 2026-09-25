package com.competra.center.data.event_control

import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.ui.BaseState

/**
 * Состояние экрана управления соревнованием по ориентированию.
 *
 * @property participantGroups Список групп участников соревнования.
 * @property competitionTitle Название соревнования.
 * @property competition Данные соревнования.
 * @property countdownMillis Оставшееся время до старта в миллисекундах.
 * @property stopwatchMillis Прошедшее время с момента старта соревнования в миллисекундах, обновляется каждые 16 мс.
 * @property isTimerRunning Флаг, запущен ли таймер отсчета.
 * @property isCompetitionRunning Флаг, запущено ли соревнование (foreground service активен).
 * @property allChipsDistributed Флаг, выданы ли чипы всем участникам.
 * @property isDrawConducted Флаг, проведена ли жеребьёвка. Вычисляется из данных участников как fallback,
 *   поскольку [OrienteeringCompetition.isDrawConducted] может быть сброшен при синхронизации с сервером.
 * @property isShowStartConfirmDialog Флаг отображения диалога подтверждения старта.
 * @property isShowStopConfirmDialog Флаг отображения диалога подтверждения завершения.
 * @property isShowCloseRegistrationDialog Флаг отображения диалога подтверждения завершения регистрации.
 */
data class OrienteeringEventControlState(
    val participantGroups: List<ParticipantGroup> = emptyList(),
    val competitionTitle: String = "",
    val competition: OrienteeringCompetition? = null,
    val countdownMillis: Long = 0L,
    val stopwatchMillis: Long = 0L,
    val isTimerRunning: Boolean = false,
    val isCompetitionRunning: Boolean = false,
    val countdownTimerInput: String = "",
    val allChipsDistributed: Boolean = true,
    val allParticipantsFinished: Boolean = false,
    val isDrawConducted: Boolean = false,
    val isFinished: Boolean = false,
    val isShowStartConfirmDialog: Boolean = false,
    val isShowStopConfirmDialog: Boolean = false,
    val isShowCloseRegistrationDialog: Boolean = false
) : BaseState {

    /**
     * Регистрация на соревнование закрыта: наступил `registrationEnd` (в т.ч. досрочное завершение
     * организатором) либо статус уже дальше открытой регистрации. После этого участники не могут
     * ни зарегистрироваться, ни отменить регистрацию — стартовый протокол меняет только организатор.
     */
    val isRegistrationClosed: Boolean
        get() {
            val base = competition?.competition ?: return false
            val regEnd = base.registrationEnd
            return (regEnd != null && regEnd <= System.currentTimeMillis()) ||
                base.status in setOf(
                    CompetitionStatus.REGISTRATION_CLOSED,
                    CompetitionStatus.IN_PROGRESS,
                    CompetitionStatus.FINISHED,
                    CompetitionStatus.ARCHIVED
                )
        }
}