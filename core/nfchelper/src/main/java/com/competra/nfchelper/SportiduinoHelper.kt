package com.competra.nfchelper

import android.app.PendingIntent
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.activity.ComponentActivity
import com.competra.domain.models.orienteering.ReadChipData
import kotlinx.coroutines.flow.SharedFlow

interface SportiduinoHelper {

    val nfcErrorFlow: SharedFlow<String>

    var nfcMode: SportiduinoNfcMode

    fun setNfcAdapter(nfcAdapter: NfcAdapter)

    fun enableForegroundDispatch(activity: ComponentActivity, pendingIntent: PendingIntent)

    fun enableReaderMode(activity: ComponentActivity, handleTag: (Tag) -> Unit)

    fun disableReaderMode(activity: ComponentActivity)

    fun disableForegroundDispatch(activity: ComponentActivity)

    /** Подписка на прочитанные чипы; переключает helper в режим чтения. */
    suspend fun subscribeToReadCard(handler: (ReadChipData) -> Unit)

    /**
     * Подписка на результаты записи чипа участника. Режим НЕ переключает — запись
     * включается только явным [prepareWriteCard].
     */
    suspend fun subscribeToWriteCard(handler: (WriteChipResult) -> Unit)

    /**
     * Подписка на результаты записи мастер-карты. Режим НЕ переключает — запись
     * включается только явным [prepareStationSetting].
     */
    suspend fun subscribeToStationSetting(handler: (WriteChipResult) -> Unit)

    /** Включает запись номера (0 — очистка) на следующий приложенный чип участника. */
    fun prepareWriteCard(cardNumber: Int, fastPunch: Boolean)

    /** Включает запись мастер-карты [type] с данными [data] на следующую приложенную метку. */
    fun prepareStationSetting(type: com.competra.nfchelper.nfccard.CardType, data: Array<ByteArray>)

    /**
     * Отменяет подготовленную запись и возвращает режим чтения. Экран, включавший запись,
     * обязан вызвать это при уходе — иначе следующий приложенный чип (например, чип
     * финишёра на экране сканирования) будет перезаписан.
     */
    fun resetToReadMode()

    suspend fun onNewTagDetected(tag: Tag)

}