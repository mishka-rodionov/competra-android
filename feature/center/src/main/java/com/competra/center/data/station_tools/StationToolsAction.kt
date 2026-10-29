package com.competra.center.data.station_tools

import com.competra.nfchelper.WriteChipResult
import com.competra.ui.BaseAction

/**
 * Действия экрана «Станции и чипы».
 */
sealed class StationToolsAction : BaseAction {
    /** Переключить вкладку; отменяет подготовленную запись и останавливает серию. */
    data class SelectTab(val tab: StationToolsTab) : StationToolsAction()

    data class ChipNumberChanged(val value: String) : StationToolsAction()
    data class FastPunchToggled(val enabled: Boolean) : StationToolsAction()

    /** Переключатель «подряд» на вкладках «Очистить» и «Номер». */
    data class BatchModeToggled(val enabled: Boolean) : StationToolsAction()
    data class StationNumberChanged(val value: String) : StationToolsAction()
    data class WakeUpTimeChanged(val ms: Long) : StationToolsAction()
    data class NewPasswordChanged(val p0: Int, val p1: Int, val p2: Int) : StationToolsAction()

    data object StartClearChip : StationToolsAction()
    data object StartWriteNumber : StationToolsAction()

    /** Остановить ожидание чипа (в том числе серию пакетного режима). */
    data object StopWaiting : StationToolsAction()
    data object StartSetStationTime : StationToolsAction()
    data object StartSetStationNumber : StationToolsAction()
    data object StartSetStationConfig : StationToolsAction()
    data object StartSleep : StationToolsAction()
    data object StartGetState : StationToolsAction()
    data object StartSetPassword : StationToolsAction()

    /** Экран показан. */
    data object ScreenShown : StationToolsAction()

    /** Экран скрыт (уход назад, другая вкладка нижней навигации) — отменить подготовленную запись. */
    data object ScreenHidden : StationToolsAction()

    data class NfcWriteResult(val result: WriteChipResult) : StationToolsAction()
    data class NfcStationResult(val result: WriteChipResult) : StationToolsAction()
}
