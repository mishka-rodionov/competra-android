package com.competra.center.presentation.write_chip

import androidx.lifecycle.viewModelScope
import com.competra.center.data.write_chip.WriteChipAction
import com.competra.center.data.write_chip.WriteChipOperationResult
import com.competra.center.data.write_chip.WriteChipState
import com.competra.center.data.write_chip.WriteChipTab
import com.competra.nfchelper.Password
import com.competra.nfchelper.SportiduinoHelper
import com.competra.nfchelper.WriteChipResult
import com.competra.nfchelper.nfccard.CardType
import com.competra.nfchelper.nfccard.Config
import com.competra.nfchelper.nfccard.MasterCard
import com.competra.resources.ResourceProvider
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class WriteChipViewModel(
    private val sportiduinoHelper: SportiduinoHelper,
    private val resourceProvider: ResourceProvider
) : BaseViewModel<WriteChipState>(WriteChipState()) {

    /** Начальная вкладка из маршрута уже применена — не перебиваем выбор пользователя при возврате на экран. */
    private var initialTabApplied = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            sportiduinoHelper.subscribeToWriteCard { result ->
                onAction(WriteChipAction.NfcWriteResult(result))
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            sportiduinoHelper.subscribeToStationSetting { result ->
                onAction(WriteChipAction.NfcStationResult(result))
            }
        }
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            is WriteChipAction.SelectTab -> {
                // Смена вкладки отменяет подготовленную операцию: иначе чип, приложенный на
                // вкладке «Записать номер», очистился бы по нажатой ранее кнопке «Очистить».
                cancelPendingWrite()
                updateState { copy(selectedTab = action.tab) }
            }

            is WriteChipAction.ApplyInitialTab -> {
                if (initialTabApplied) return
                initialTabApplied = true
                updateState { copy(selectedTab = action.tab) }
            }

            WriteChipAction.ScreenHidden -> cancelPendingWrite()

            is WriteChipAction.ChipNumberChanged -> updateState { copy(chipNumberInput = action.value) }
            is WriteChipAction.FastPunchToggled -> updateState { copy(fastPunch = action.enabled) }
            is WriteChipAction.StationNumberChanged -> updateState { copy(stationNumberInput = action.value) }
            is WriteChipAction.WakeUpTimeChanged -> updateState { copy(wakeUpDateTimeMs = action.ms) }
            is WriteChipAction.NewPasswordChanged -> updateState {
                copy(newPassword = Triple(action.p0, action.p1, action.p2))
            }

            WriteChipAction.StartClearChip -> {
                sportiduinoHelper.prepareWriteCard(cardNumber = 0, fastPunch = false)
                updateState { copy(isWaitingForChip = true, lastResult = null) }
            }

            WriteChipAction.StartWriteNumber -> {
                val number = stateValue.chipNumberInput.toIntOrNull()
                if (number == null || number !in 1..65535) {
                    updateState { copy(lastResult = WriteChipOperationResult.Error("Номер должен быть от 1 до 65535")) }
                    return
                }
                sportiduinoHelper.prepareWriteCard(cardNumber = number, fastPunch = stateValue.fastPunch)
                updateState { copy(isWaitingForChip = true, lastResult = null) }
            }

            WriteChipAction.StartSetStationTime -> {
                sportiduinoHelper.prepareStationSetting(
                    type = CardType.MASTER_SET_TIME,
                    data = MasterCard.packTime(Calendar.getInstance()).filterNotNull().toTypedArray()
                )
                updateState { copy(isWaitingForChip = true, lastResult = null) }
            }

            WriteChipAction.StartSetStationNumber -> {
                val number = stateValue.stationNumberInput.toIntOrNull()
                if (number == null || number !in 1..255) {
                    updateState { copy(lastResult = WriteChipOperationResult.Error("Номер станции должен быть от 1 до 255")) }
                    return
                }
                sportiduinoHelper.prepareStationSetting(
                    type = CardType.MASTER_SET_NUMBER,
                    data = MasterCard.packStationNumber(number).filterNotNull().toTypedArray()
                )
                updateState { copy(isWaitingForChip = true, lastResult = null) }
            }

            WriteChipAction.StartSetStationConfig -> {
                val number = stateValue.stationNumberInput.toIntOrNull()
                if (number == null || number !in 1..255) {
                    updateState { copy(lastResult = WriteChipOperationResult.Error("Номер станции должен быть от 1 до 255")) }
                    return
                }
                val config = Config(resourceProvider = resourceProvider).apply { stationCode = number }
                val packed = config.pack()?.filterNotNull()?.toTypedArray() ?: emptyArray()
                sportiduinoHelper.prepareStationSetting(type = CardType.MASTER_CONFIG, data = packed)
                updateState { copy(isWaitingForChip = true, lastResult = null) }
            }

            WriteChipAction.StartSleep -> {
                val calendar = Calendar.getInstance().apply { timeInMillis = stateValue.wakeUpDateTimeMs }
                sportiduinoHelper.prepareStationSetting(
                    type = CardType.MASTER_SLEEP,
                    data = MasterCard.packTime(calendar).filterNotNull().toTypedArray()
                )
                updateState { copy(isWaitingForChip = true, lastResult = null) }
            }

            WriteChipAction.StartGetState -> {
                sportiduinoHelper.prepareStationSetting(type = CardType.MASTER_GET_STATE, data = emptyArray())
                updateState { copy(isWaitingForChip = true, lastResult = null) }
            }

            WriteChipAction.StartSetPassword -> {
                val (p0, p1, p2) = stateValue.newPassword
                sportiduinoHelper.prepareStationSetting(
                    type = CardType.MASTER_PASSWORD,
                    data = MasterCard.packNewPassword(Password(p0, p1, p2))
                )
                updateState { copy(isWaitingForChip = true, lastResult = null) }
            }

            is WriteChipAction.NfcWriteResult -> updateState {
                copy(isWaitingForChip = false, lastResult = action.result.toOperationResult())
            }

            is WriteChipAction.NfcStationResult -> updateState {
                copy(isWaitingForChip = false, lastResult = action.result.toOperationResult())
            }
        }
    }

    /** Возвращает NFC в режим чтения и снимает ожидание чипа. */
    private fun cancelPendingWrite() {
        sportiduinoHelper.resetToReadMode()
        updateState { copy(isWaitingForChip = false) }
    }

    override fun onCleared() {
        super.onCleared()
        sportiduinoHelper.resetToReadMode()
    }

    private fun WriteChipResult.toOperationResult(): WriteChipOperationResult = when (this) {
        is WriteChipResult.Success -> WriteChipOperationResult.Success(message)
        is WriteChipResult.Error -> WriteChipOperationResult.Error(message)
    }
}
