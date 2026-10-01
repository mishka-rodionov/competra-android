package com.competra.center.presentation.station_tools

import androidx.lifecycle.viewModelScope
import com.competra.center.data.station_tools.StationToolsAction
import com.competra.center.data.write_chip.WriteChipTab
import com.competra.data.navigation.CenterNavigation
import com.competra.data.navigation.Navigation
import com.competra.ui.BaseAction
import com.competra.ui.BaseState
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel хаба «Станции и чипы»: только навигация к инструментам, своего состояния нет.
 */
class StationToolsViewModel(
    private val navigation: Navigation
) : BaseViewModel<BaseState>(object : BaseState {}) {

    override fun onAction(action: BaseAction) {
        if (action !is StationToolsAction) return
        val route = when (action) {
            StationToolsAction.OpenInspect -> CenterNavigation.ChipInspectRoute
            StationToolsAction.OpenClear -> CenterNavigation.WriteChipRoute(WriteChipTab.CLEAR.name)
            StationToolsAction.OpenWriteNumber -> CenterNavigation.WriteChipRoute(WriteChipTab.WRITE_NUMBER.name)
            StationToolsAction.OpenStationSetup -> CenterNavigation.WriteChipRoute(WriteChipTab.STATION.name)
        }
        viewModelScope.launch { navigation.navigate(route) }
    }
}
