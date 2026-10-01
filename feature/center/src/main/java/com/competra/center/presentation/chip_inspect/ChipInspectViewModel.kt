package com.competra.center.presentation.chip_inspect

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.center.data.chip_inspect.ChipInspectState
import com.competra.center.data.chip_inspect.ChipScan
import com.competra.domain.models.orienteering.ReadChipData
import com.competra.nfchelper.SportiduinoHelper
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ViewModel экрана «Считать / Проверить»: читает любой приложенный чип и показывает его
 * содержимое без привязки к соревнованию. Ничего не сохраняет.
 */
class ChipInspectViewModel(
    private val sportiduinoHelper: SportiduinoHelper,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<ChipInspectState>(ChipInspectState()) {

    init {
        viewModelScope.launch(Dispatchers.IO) {
            sportiduinoHelper.subscribeToReadCard(::onChipRead)
        }
    }

    override fun onAction(action: BaseAction) = Unit

    private fun onChipRead(chipData: ReadChipData) {
        val now = System.currentTimeMillis()
        val scan = when (chipData) {
            is ReadChipData.RawResult -> ChipScan.Participant(
                chipNumber = chipData.chipNumber,
                clearTime = chipData.clearTime,
                punches = chipData.splits,
                scannedAt = now,
            )
            is ReadChipData.MasterChipData -> ChipScan.Master(
                description = chipData.data,
                scannedAt = now,
            )
        }
        analytics.trackEvent(
            when (scan) {
                is ChipScan.Participant ->
                    AnalyticsEvent.NfcChipInspected(AnalyticsEvent.NfcCardType.PARTICIPANT, scan.isClean)
                is ChipScan.Master ->
                    AnalyticsEvent.NfcChipInspected(AnalyticsEvent.NfcCardType.MASTER, isClean = null)
            }
        )
        updateState { copy(lastScan = scan, scanCount = scanCount + 1) }
    }
}
