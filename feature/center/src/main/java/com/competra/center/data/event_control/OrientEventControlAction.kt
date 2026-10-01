package com.competra.center.data.event_control

import com.competra.ui.BaseAction

/**
 * Действия на экране управления соревнованием по ориентированию.
 */
sealed class OrientEventControlAction: BaseAction {

    data object OpenOrientReadCard: OrientEventControlAction()
    data object OpenParticipantLists: OrientEventControlAction()
    data object OpenDrawParticipants: OrientEventControlAction()
    data object OpenStartGrid: OrientEventControlAction()
    data object OpenResults: OrientEventControlAction()
    data object OpenGetOrienteeringChip: OrientEventControlAction()
    data object OpenWriteChip: OrientEventControlAction()
    /** Очистка чипа — тот же экран, что в «Станции и чипы», на вкладке «Очистить». */
    data object OpenClearChip: OrientEventControlAction()
    /** Проверка чипа без поиска участника — экран «Считать / Проверить» из «Станции и чипы». */
    data object OpenChipInspect: OrientEventControlAction()

    data object ShowStartConfirmDialog: OrientEventControlAction()
    data object HideStartConfirmDialog: OrientEventControlAction()
    data object StartCompetition: OrientEventControlAction()

    data object ShowStopConfirmDialog: OrientEventControlAction()
    data object HideStopConfirmDialog: OrientEventControlAction()
    data object StopCompetition: OrientEventControlAction()

    data object ShowCloseRegistrationDialog: OrientEventControlAction()
    data object HideCloseRegistrationDialog: OrientEventControlAction()
    data object CloseRegistration: OrientEventControlAction()

    data object CancelCountdown: OrientEventControlAction()

    data class UpdateCountdownTimerInput(val value: String): OrientEventControlAction()

    data object Reload: OrientEventControlAction()

    data object StopService: OrientEventControlAction()

}