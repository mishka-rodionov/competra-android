package com.competra.center.data.station_tools

import com.competra.ui.BaseAction

/**
 * Действия хаба «Станции и чипы».
 */
sealed class StationToolsAction : BaseAction {
    /** Считать чип и проверить, чистый ли он. */
    data object OpenInspect : StationToolsAction()

    /** Очистить чип участника. */
    data object OpenClear : StationToolsAction()

    /** Записать номер на чип участника. */
    data object OpenWriteNumber : StationToolsAction()

    /** Настроить станцию мастер-картой. */
    data object OpenStationSetup : StationToolsAction()
}
