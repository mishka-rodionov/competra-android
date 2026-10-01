package com.competra.nfchelper

import android.app.PendingIntent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import androidx.activity.ComponentActivity
import com.competra.domain.models.orienteering.ReadChipData
import com.competra.nfchelper.nfccard.Card
import com.competra.nfchelper.nfccard.CardAdapter
import com.competra.nfchelper.nfccard.CardMifareClassic
import com.competra.nfchelper.nfccard.CardMifareUltralight
import com.competra.nfchelper.nfccard.CardType
import com.competra.nfchelper.nfccard.MasterCard
import com.competra.nfchelper.nfccard.ParticipantCard
import com.competra.nfchelper.nfccard.ReadWriteCardException
import com.competra.resources.ResourceProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Реализация интерфейса [SportiduinoHelper], обеспечивающая взаимодействие с NFC-метками в системе Sportiduino.
 *
 * Данный класс отвечает за:
 * - Управление жизненным циклом NFC-адаптера (активация и деактивация режимов Reader Mode и Foreground Dispatch).
 * - Обработку обнаруженных тегов различных типов (Mifare Classic и Mifare Ultralight).
 * - Переключение между режимами работы: чтение карты, запись карты или настройка станции.
 * - Считывание данных с чипов и передачу результатов через поток данных.
 *
 * @property resourceProvider Поставщик строковых ресурсов для формирования сообщений или обработки данных.
 */
class SportiduinoHelperImpl(
    private val resourceProvider: ResourceProvider
) : SportiduinoHelper {

    private var nfcAdapter: NfcAdapter? = null
    private val techList by lazy {
        arrayOf(
            arrayOf(MifareClassic::class.simpleName),
            arrayOf(MifareUltralight::class.simpleName)
        )
    }

    private val _readCardFlow = MutableSharedFlow<ReadChipData>()
    private val _writeCardFlow = MutableSharedFlow<WriteChipResult>()
    private val _stationSettingFlow = MutableSharedFlow<WriteChipResult>()
    private val _nfcErrorFlow = MutableSharedFlow<String>()
    override val nfcErrorFlow: SharedFlow<String> = _nfcErrorFlow.asSharedFlow()

    override var nfcMode: SportiduinoNfcMode = SportiduinoNfcMode.READ_CARD

    var pendingCardNumber: Int = 0
    var pendingFastPunch: Boolean = false
    var pendingMasterCardType: CardType? = null
    var pendingMasterData: Array<ByteArray> = emptyArray()

    override fun setNfcAdapter(nfcAdapter: NfcAdapter) {
        this.nfcAdapter = nfcAdapter
    }

    override fun enableForegroundDispatch(
        activity: ComponentActivity,
        pendingIntent: PendingIntent
    ) {
        nfcAdapter?.enableForegroundDispatch(activity, pendingIntent, null, techList)
    }

    override fun enableReaderMode(activity: ComponentActivity, handleTag: (Tag) -> Unit) {
        nfcAdapter?.enableReaderMode(activity,
            { tag ->
                handleTag.invoke(tag)
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null)
    }

    override fun disableReaderMode(activity: ComponentActivity) {
        nfcAdapter?.disableReaderMode(activity)
    }

    override fun disableForegroundDispatch(activity: ComponentActivity) {
        nfcAdapter?.disableForegroundDispatch(activity)
    }

    override suspend fun onNewTagDetected(tag: Tag) {
        when (nfcMode) {
            SportiduinoNfcMode.READ_CARD -> readCard(tag)
            SportiduinoNfcMode.WRITE_CARD -> writeCard(tag)
            SportiduinoNfcMode.STATION_SETTING -> stationSetting(tag)
        }
    }

    override suspend fun subscribeToReadCard(handler: (ReadChipData) -> Unit) {
        nfcMode = SportiduinoNfcMode.READ_CARD
        _readCardFlow.collect { handler.invoke(it) }
    }

    override suspend fun subscribeToWriteCard(handler: (WriteChipResult) -> Unit) {
        _writeCardFlow.collect { handler.invoke(it) }
    }

    override suspend fun subscribeToStationSetting(handler: (WriteChipResult) -> Unit) {
        _stationSettingFlow.collect { handler.invoke(it) }
    }

    override fun prepareWriteCard(cardNumber: Int, fastPunch: Boolean) {
        pendingCardNumber = cardNumber
        pendingFastPunch = fastPunch
        nfcMode = SportiduinoNfcMode.WRITE_CARD
    }

    override fun prepareStationSetting(type: com.competra.nfchelper.nfccard.CardType, data: Array<ByteArray>) {
        pendingMasterCardType = type
        pendingMasterData = data
        nfcMode = SportiduinoNfcMode.STATION_SETTING
    }

    override fun resetToReadMode() {
        pendingCardNumber = 0
        pendingFastPunch = false
        pendingMasterCardType = null
        pendingMasterData = emptyArray()
        nfcMode = SportiduinoNfcMode.READ_CARD
    }

    private suspend fun writeCard(tag: Tag) {
        val adapter = resolveAdapter(tag) ?: return
        try {
            adapter.connect()
            val card = ParticipantCard(adapter, pendingCardNumber, pendingFastPunch, resourceProvider)
            card.write()
            val msg = if (pendingCardNumber > 0) "Чип #$pendingCardNumber записан" else "Чип очищен"
            _writeCardFlow.emit(WriteChipResult.Success(msg))
        } catch (e: ReadWriteCardException) {
            _writeCardFlow.emit(WriteChipResult.Error(e.message ?: "Ошибка записи"))
        } finally {
            adapter.close()
        }
    }

    private suspend fun stationSetting(tag: Tag) {
        val type = pendingMasterCardType ?: return
        val adapter = resolveAdapter(tag) ?: return
        try {
            adapter.connect()
            val card = MasterCard(adapter, type, Password.defaultPassword(), resourceProvider)
            card.dataForWriting = pendingMasterData
            card.write()
            _stationSettingFlow.emit(WriteChipResult.Success("Карта управления записана"))
        } catch (e: ReadWriteCardException) {
            _stationSettingFlow.emit(WriteChipResult.Error(e.message ?: "Ошибка записи"))
        } finally {
            adapter.close()
        }
    }

    private fun resolveAdapter(tag: Tag): CardAdapter? {
        tag.techList.forEach { tech ->
            if (tech == MifareClassic::class.java.name) return CardMifareClassic(MifareClassic.get(tag))
            if (tech == MifareUltralight::class.java.name) return CardMifareUltralight(MifareUltralight.get(tag))
        }
        return null
    }

    private suspend fun readCard(tag: Tag) {
        val tagInfo = StringBuilder()
        val tagId = tag.id
        for (b in tagId) {
            tagInfo.append(Integer.toHexString(b.toInt() and 0xFF)).append(" ")
        }
        val tagIdStr = tagInfo.toString()

        val techList = tag.techList
        var adapter: CardAdapter? = null
        techList.forEach { s ->
            if (s == MifareClassic::class.java.getName()) {
                adapter = CardMifareClassic(MifareClassic.get(tag))
            } else if (s == MifareUltralight::class.java.getName()) {
                adapter = CardMifareUltralight(MifareUltralight.get(tag))
            }
            if (adapter != null) {
                try {
                    adapter.connect()
                    val card = Card.detectCard(adapter, resourceProvider)
                    val buffer = card.read()
                    _readCardFlow.emit(card.parseData(buffer))
                } catch (e: ReadWriteCardException) {
                    _nfcErrorFlow.emit(e.message ?: "Ошибка чтения метки")
                } finally {
                    adapter.close()
                }
                return@forEach
            }
        }
    }
}