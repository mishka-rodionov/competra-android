package com.competra.center.presentation.station_tools

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.center.data.station_tools.ChipScan
import com.competra.center.data.station_tools.StationToolsAction
import com.competra.center.data.station_tools.StationToolsResult
import com.competra.center.data.station_tools.StationToolsState
import com.competra.center.data.station_tools.StationToolsTab
import com.competra.domain.models.orienteering.ReadChipData
import com.competra.nfchelper.Password
import com.competra.nfchelper.SportiduinoHelper
import com.competra.nfchelper.WriteChipResult
import com.competra.nfchelper.nfccard.CardType
import com.competra.nfchelper.nfccard.Config
import com.competra.nfchelper.nfccard.MasterCard
import com.competra.resources.ResourceProvider
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

private const val MAX_CHIP_NUMBER = 65535

/**
 * ViewModel экрана «Станции и чипы»: чтение/проверка, очистка и запись чипов, настройка станций
 * мастер-картами — без привязки к соревнованию, ничего не сохраняет.
 *
 * Режим NFC в [SportiduinoHelper] общий для всего приложения (его же читает экран «Сканировать»
 * в управлении соревнованием), поэтому запись взводится только пока экран на виду и сбрасывается
 * в режим чтения после каждой операции, при смене вкладки и при уходе с экрана. Все подписки
 * собираются на главном потоке — флаги ниже меняются только оттуда.
 */
class StationToolsViewModel(
    private val sportiduinoHelper: SportiduinoHelper,
    private val resourceProvider: ResourceProvider,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<StationToolsState>(StationToolsState()) {

    /** Экран на виду. Пока он скрыт, чтения игнорируются, а запись не взводится. */
    private var isScreenVisible = false

    /** Номер, взведённый для записи на чип участника (0 — очистка); `null` — запись не взведена. */
    private var pendingChipNumber: Int? = null

    init {
        viewModelScope.launch {
            sportiduinoHelper.subscribeToReadCard(::onChipRead)
        }
        viewModelScope.launch {
            sportiduinoHelper.subscribeToWriteCard { result ->
                onAction(StationToolsAction.NfcWriteResult(result))
            }
        }
        viewModelScope.launch {
            sportiduinoHelper.subscribeToStationSetting { result ->
                onAction(StationToolsAction.NfcStationResult(result))
            }
        }
    }

    override fun onAction(action: BaseAction) {
        if (action !is StationToolsAction) return
        when (action) {
            is StationToolsAction.SelectTab -> {
                // Смена вкладки отменяет подготовленную операцию: иначе чип, приложенный на
                // вкладке «Номер», очистился бы по нажатой ранее кнопке «Очистить».
                cancelPendingWrite()
                updateState { copy(selectedTab = action.tab, lastResult = null) }
            }

            StationToolsAction.ScreenShown -> isScreenVisible = true

            StationToolsAction.ScreenHidden -> {
                isScreenVisible = false
                cancelPendingWrite()
            }

            is StationToolsAction.ChipNumberChanged -> updateState { copy(chipNumberInput = action.value) }
            is StationToolsAction.FastPunchToggled -> updateState { copy(fastPunch = action.enabled) }
            is StationToolsAction.BatchModeToggled -> {
                if (!action.enabled && stateValue.isWaitingForChip) cancelPendingWrite()
                updateState { copy(isBatchMode = action.enabled) }
            }
            is StationToolsAction.StationNumberChanged -> updateState { copy(stationNumberInput = action.value) }
            is StationToolsAction.WakeUpTimeChanged -> updateState { copy(wakeUpDateTimeMs = action.ms) }
            is StationToolsAction.NewPasswordChanged -> updateState {
                copy(newPassword = Triple(action.p0, action.p1, action.p2))
            }

            StationToolsAction.StartClearChip -> startChipWrite(number = 0)

            StationToolsAction.StartWriteNumber -> {
                val number = stateValue.chipNumberInput.toIntOrNull()
                if (number == null || number !in 1..MAX_CHIP_NUMBER) {
                    showError("Номер должен быть от 1 до $MAX_CHIP_NUMBER")
                    return
                }
                startChipWrite(number)
            }

            StationToolsAction.StopWaiting -> cancelPendingWrite()

            StationToolsAction.StartSetStationTime -> startStationSetting(
                type = CardType.MASTER_SET_TIME,
                data = MasterCard.packTime(Calendar.getInstance()).filterNotNull().toTypedArray()
            )

            StationToolsAction.StartSetStationNumber -> {
                val number = validStationNumber() ?: return
                startStationSetting(
                    type = CardType.MASTER_SET_NUMBER,
                    data = MasterCard.packStationNumber(number).filterNotNull().toTypedArray()
                )
            }

            StationToolsAction.StartSetStationConfig -> {
                val number = validStationNumber() ?: return
                val config = Config(resourceProvider = resourceProvider).apply { stationCode = number }
                val packed = config.pack()?.filterNotNull()?.toTypedArray() ?: emptyArray()
                startStationSetting(type = CardType.MASTER_CONFIG, data = packed)
            }

            StationToolsAction.StartSleep -> {
                val calendar = Calendar.getInstance().apply { timeInMillis = stateValue.wakeUpDateTimeMs }
                startStationSetting(
                    type = CardType.MASTER_SLEEP,
                    data = MasterCard.packTime(calendar).filterNotNull().toTypedArray()
                )
            }

            StationToolsAction.StartGetState ->
                startStationSetting(type = CardType.MASTER_GET_STATE, data = emptyArray())

            StationToolsAction.StartSetPassword -> {
                val (p0, p1, p2) = stateValue.newPassword
                startStationSetting(
                    type = CardType.MASTER_PASSWORD,
                    data = MasterCard.packNewPassword(Password(p0, p1, p2))
                )
            }

            is StationToolsAction.NfcWriteResult -> onChipWriteResult(action.result)
            is StationToolsAction.NfcStationResult -> onStationSettingResult(action.result)
        }
    }

    /** Взводит запись чипа участника; новая серия пакетного режима начинает счёт заново. */
    private fun startChipWrite(number: Int) {
        if (!isScreenVisible) return
        armChipWrite(number)
        updateState { copy(isWaitingForChip = true, batchProcessedCount = 0, lastResult = null) }
    }

    private fun armChipWrite(number: Int) {
        pendingChipNumber = number
        sportiduinoHelper.prepareWriteCard(cardNumber = number, fastPunch = number > 0 && stateValue.fastPunch)
    }

    private fun startStationSetting(type: CardType, data: Array<ByteArray>) {
        if (!isScreenVisible) return
        sportiduinoHelper.prepareStationSetting(type = type, data = data)
        updateState { copy(isWaitingForChip = true, lastResult = null) }
    }

    /**
     * Итог записи чипа участника. В пакетном режиме после успеха запись взводится снова (для номера —
     * со следующим), после ошибки — с тем же номером, чтобы повторить чип. Иначе NFC возвращается
     * в режим чтения: без этого режим записи остался бы взведённым и изменил бы следующий чип.
     */
    private fun onChipWriteResult(result: WriteChipResult) {
        val number = pendingChipNumber
        val isSuccess = result is WriteChipResult.Success
        val operationResult = result.toResult()
        if (isSuccess && number != null) {
            analytics.trackEvent(
                AnalyticsEvent.NfcChipWritten(
                    operation = if (number == 0) {
                        AnalyticsEvent.NfcChipWriteOperation.CLEAR
                    } else {
                        AnalyticsEvent.NfcChipWriteOperation.WRITE_NUMBER
                    },
                    batch = stateValue.isBatchMode,
                )
            )
        }

        val continueBatch = isScreenVisible && stateValue.isBatchMode && stateValue.selectedTab in BATCH_TABS
        if (number == null || !continueBatch) {
            cancelPendingWrite()
            updateState { copy(lastResult = operationResult) }
            return
        }

        val processed = stateValue.batchProcessedCount + if (isSuccess) 1 else 0
        val nextNumber = if (isSuccess && number > 0) number + 1 else number
        if (nextNumber > MAX_CHIP_NUMBER) {
            cancelPendingWrite()
            updateState {
                copy(
                    batchProcessedCount = processed,
                    lastResult = StationToolsResult.Info(
                        "${operationResult.message}. Достигнут максимальный номер $MAX_CHIP_NUMBER — серия остановлена"
                    )
                )
            }
            return
        }
        armChipWrite(nextNumber)
        updateState {
            copy(
                isWaitingForChip = true,
                batchProcessedCount = processed,
                chipNumberInput = if (nextNumber > 0) nextNumber.toString() else chipNumberInput,
                lastResult = operationResult
            )
        }
    }

    /** Итог записи мастер-карты: операция разовая, NFC возвращается в режим чтения (для «Получить состояние» — чтобы затем считать карту). */
    private fun onStationSettingResult(result: WriteChipResult) {
        cancelPendingWrite()
        updateState { copy(lastResult = result.toResult()) }
    }

    private fun onChipRead(chipData: ReadChipData) {
        if (!isScreenVisible) return
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
        // На вкладках записи чип без нажатой кнопки только считывается — подскажем, что приложено.
        val hint = if (stateValue.selectedTab == StationToolsTab.INSPECT) null else scan.toHint()
        updateState {
            copy(lastScan = scan, scanCount = scanCount + 1, lastResult = hint ?: lastResult)
        }
    }

    /** Возвращает NFC в режим чтения и снимает ожидание чипа. */
    private fun cancelPendingWrite() {
        pendingChipNumber = null
        sportiduinoHelper.resetToReadMode()
        updateState { copy(isWaitingForChip = false) }
    }

    private fun validStationNumber(): Int? {
        val number = stateValue.stationNumberInput.toIntOrNull()
        if (number == null || number !in 1..255) {
            showError("Номер станции должен быть от 1 до 255")
            return null
        }
        return number
    }

    private fun showError(message: String) {
        updateState { copy(lastResult = StationToolsResult.Error(message)) }
    }

    override fun onCleared() {
        super.onCleared()
        sportiduinoHelper.resetToReadMode()
    }

    private fun WriteChipResult.toResult(): StationToolsResult = when (this) {
        is WriteChipResult.Success -> StationToolsResult.Success(message)
        is WriteChipResult.Error -> StationToolsResult.Error(message)
    }

    private fun ChipScan.toHint(): StationToolsResult.Info = when (this) {
        is ChipScan.Participant -> StationToolsResult.Info(
            if (isClean) {
                "Считан чип №$chipNumber: чистый. Ничего не изменено"
            } else {
                "Считан чип №$chipNumber: ${punches.size} ${punchesWord(punches.size)}. Ничего не изменено"
            }
        )
        is ChipScan.Master -> StationToolsResult.Info("Считана мастер-карта: $description")
    }

    private companion object {
        /** Вкладки, где работает пакетный режим. */
        val BATCH_TABS = setOf(StationToolsTab.CLEAR, StationToolsTab.WRITE_NUMBER)
    }
}
