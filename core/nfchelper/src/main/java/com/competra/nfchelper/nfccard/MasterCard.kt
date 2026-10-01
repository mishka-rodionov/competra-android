package com.competra.nfchelper.nfccard

import android.text.Html
import com.competra.domain.models.orienteering.ReadChipData
import com.competra.nfchelper.Password
import com.competra.nfchelper.R
import com.competra.nfchelper.nfccard.Constants.CARD_PAGE_INFO1
import com.competra.nfchelper.nfccard.Constants.CARD_PAGE_INIT
import com.competra.nfchelper.nfccard.Constants.FW_PROTO_VERSION
import com.competra.nfchelper.nfccard.Constants.MASTER_CARD_SIGN
import com.competra.nfchelper.nfccard.Constants.OPERATED_YEAR_MIN
import com.competra.resources.ResourceProvider

class MasterCard(adapter: CardAdapter, type: CardType, password: Password, private val resourceProvider: ResourceProvider) :
    Card(adapter) {
    private val password: ByteArray
    var dataForWriting: Array<ByteArray> = emptyArray()

    init {
        this.type = type
        this.password = password.toByteArray()
    }

    @Throws(ReadWriteCardException::class)
    override fun read(): Array<ByteArray> {
        return when (type) {
            CardType.MASTER_GET_STATE -> adapter.readPages(8, 12)
            CardType.MASTER_READ_BACKUP -> adapter.readPages(
                CARD_PAGE_INFO1,
                adapter.maxPage,
                true
            )

            else -> Array(0) { ByteArray(0) }
        }
    }

    override fun parseData(data: Array<ByteArray>): ReadChipData {
        return ReadChipData.MasterChipData(
            when (type) {
                CardType.MASTER_SET_TIME -> resourceProvider.getString(R.string.time_master_card)
                CardType.MASTER_SET_NUMBER -> resourceProvider.getString(R.string.number_master_card)
                CardType.MASTER_GET_STATE -> {
                    val state = State(data, resourceProvider)
                    Html.fromHtml(
                        (resourceProvider.getString(R.string.state_master_card) + "\n" + state.toString()).replace(
                            "\n",
                            "<br/>"
                        )
                    )
                }

                CardType.MASTER_SLEEP -> resourceProvider.getString(R.string.sleep_master_card)
                CardType.MASTER_READ_BACKUP -> parseBackupMaster(data)
                CardType.MASTER_CONFIG -> resourceProvider.getString(R.string.config_master_card)
                CardType.MASTER_PASSWORD -> resourceProvider.getString(R.string.password_master_card)
                else -> resourceProvider.getString(R.string.unknown_card_type)
            // toString, а не `as String`: для MASTER_GET_STATE Html.fromHtml возвращает Spanned,
            // и приведение падало с ClassCastException при чтении карты состояния станции.
            }.toString()
        )
    }

    @Throws(ReadWriteCardException::class)
    override fun writeImpl() {
        val header = arrayOf(
            byteArrayOf(0, type.value.toByte(), MASTER_CARD_SIGN, FW_PROTO_VERSION),
            byteArrayOf(password[0], password[1], password[2], 0)
        )
        adapter.writePages(CARD_PAGE_INIT, header, header.size)
        if (dataForWriting.isNotEmpty()) {
            adapter.writePages(6, dataForWriting, dataForWriting!!.size)
        }
    }

    private fun parseBackupMaster(data: Array<ByteArray>): String {
        val ret: java.lang.StringBuilder =
            java.lang.StringBuilder(resourceProvider.getString(R.string.backup_master_card))
        val stationNumber = dataPage4[0].toInt() and 0xff
        ret.append("\n").append(resourceProvider.getString(R.string.station_no_)).append(stationNumber)
        ret.append("\n").append(resourceProvider.getString(R.string.record_count)).append(" %d")
        var recordCount = 0
        if (dataPage4[3].toInt() == 1) { // old format with timestamps
            var timeHigh12bits: Long = 0
            var initTime: Long = 0
            for (datum in data) {
                if (timeHigh12bits == 0L) {
                    initTime = Util.toUint32(datum)
                    timeHigh12bits = initTime and 0xfff00000L
                    continue
                }

                var cardNum: Int = Util.toUint16(datum[0], datum[1])
                cardNum = cardNum shr 4

                if (cardNum == 0) {
                    continue
                }

                var punchTime: Long = Util.toUint32(datum) and 0xfffff or timeHigh12bits
                if (punchTime < initTime) {
                    punchTime += 0x100000
                }
                ret.append(String.format("\n%1$4s", cardNum))
                ret.append(" - ").append(Util.dformat.format(java.util.Date(punchTime * 1000)))
                ++recordCount
            }
        } else if (dataPage4!![3] >= 10) { // new format (FW version 10 or greater)
            var lastTimeHigh16bits = 0
            for (datum in data) {
                val cardNum: Int = Util.toUint16(datum[0], datum[1])
                val time16bits: Int = Util.toUint16(datum[2], datum[3])
                if (cardNum == 0) {
                    if (time16bits > 0 && time16bits != lastTimeHigh16bits) {
                        lastTimeHigh16bits = time16bits
                    }
                    continue
                }
                val punchTime = lastTimeHigh16bits.toLong() shl 16 or time16bits.toLong()
                ret.append(String.format("\n%1$5s", cardNum))
                ret.append(" - ").append(Util.dformat.format(java.util.Date(punchTime * 1000)))
                ++recordCount
            }
        }
        return String.format(ret.toString(), recordCount)
    }

    companion object {
        fun packStationNumber(stationNumber: Int): Array<ByteArray?> {
            return arrayOf(
                byteArrayOf(
                    stationNumber.toByte(),
                    0,
                    0,
                    0
                )
            )
        }

        fun packTime(calendar: java.util.Calendar): Array<ByteArray?> {
            val c = calendar.clone() as java.util.Calendar
            c.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
            val year: Int = c.get(java.util.Calendar.YEAR) - OPERATED_YEAR_MIN
            val month = c.get(java.util.Calendar.MONTH) + 1
            val day = c.get(java.util.Calendar.DAY_OF_MONTH)
            val hour = c.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = c.get(java.util.Calendar.MINUTE)
            val second = c.get(java.util.Calendar.SECOND)
            return arrayOf(
                byteArrayOf(month.toByte(), year.toByte(), day.toByte(), 0),
                byteArrayOf(hour.toByte(), minute.toByte(), second.toByte(), 0)
            )
        }

        fun packNewPassword(password: Password): Array<ByteArray> {
            return arrayOf(
                byteArrayOf(
                    password.getValue(2).toByte(),
                    password.getValue(1).toByte(),
                    password.getValue(0).toByte(),
                    0
                )
            )
        }
    }
}

