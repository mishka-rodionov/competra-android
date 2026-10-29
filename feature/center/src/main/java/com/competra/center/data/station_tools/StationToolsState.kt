package com.competra.center.data.station_tools

import com.competra.domain.models.orienteering.SplitTime
import com.competra.domain.models.orienteering.unsyncedStationControlPoints
import com.competra.ui.BaseState

/**
 * Состояние экрана «Станции и чипы»: все инструменты на одном экране, переключаются вкладками.
 *
 * @property selectedTab открытая вкладка.
 * @property lastScan последний считанный чип; `null` — ещё ничего не прикладывали.
 * @property scanCount сколько чипов считано за время открытия экрана (удобно при проверке
 * чипов на старте подряд — видно, что новый скан действительно прошёл).
 * @property chipNumberInput номер для записи на чип (вкладка «Номер»).
 * @property fastPunch записывать чип в режиме быстрой отметки.
 * @property stationNumberInput номер станции для мастер-карт.
 * @property wakeUpDateTimeMs время пробуждения станции для карты сна.
 * @property newPassword новый пароль станции (три байта).
 * @property isBatchMode переключатель «подряд»: после успешной записи чипа запись взводится снова
 * (для «Номер» — со следующим номером). Действует на вкладках «Очистить» и «Номер».
 * @property isWaitingForChip запись подготовлена — следующий приложенный чип будет изменён.
 * @property batchProcessedCount сколько чипов обработано в текущей серии пакетного режима.
 * @property lastResult итог последней операции или подсказка для вкладок записи.
 */
data class StationToolsState(
    val selectedTab: StationToolsTab = StationToolsTab.INSPECT,
    val lastScan: ChipScan? = null,
    val scanCount: Int = 0,
    val chipNumberInput: String = "",
    val fastPunch: Boolean = false,
    val stationNumberInput: String = "",
    val wakeUpDateTimeMs: Long = System.currentTimeMillis() + 3_600_000L,
    val newPassword: Triple<Int, Int, Int> = Triple(0, 0, 0),
    val isBatchMode: Boolean = false,
    val isWaitingForChip: Boolean = false,
    val batchProcessedCount: Int = 0,
    val lastResult: StationToolsResult? = null,
) : BaseState {
    /** Идёт серия пакетного режима: каждый приложенный чип будет изменён. */
    val isBatchRunning: Boolean get() = isBatchMode && isWaitingForChip
}

/** Вкладки экрана «Станции и чипы». */
enum class StationToolsTab { INSPECT, CLEAR, WRITE_NUMBER, STATION }

/** Итог операции на вкладках записи. */
sealed class StationToolsResult {
    abstract val message: String

    data class Success(override val message: String) : StationToolsResult()
    data class Error(override val message: String) : StationToolsResult()

    /** Чип приложен без подготовленной записи — только считан, ничего не изменено. */
    data class Info(override val message: String) : StationToolsResult()
}

/**
 * Результат чтения метки на вкладке «Считать».
 */
sealed class ChipScan {
    /** Момент чтения (мс), чтобы повторный скан того же чипа был заметен. */
    abstract val scannedAt: Long

    /**
     * Чип участника.
     *
     * @property clearTime время последней очистки/инициализации чипа (мс).
     * @property punches отметки в порядке записи на чип; пусто — чип чистый.
     */
    data class Participant(
        val chipNumber: Int,
        val clearTime: Long,
        val punches: List<SplitTime>,
        override val scannedAt: Long,
    ) : ChipScan() {
        /** На чипе нет ни одной отметки — готов к старту. */
        val isClean: Boolean get() = punches.isEmpty()

        /** КП, чьи отметки позже момента чтения: часы этих станций не синхронизированы. */
        val unsyncedControlPoints: List<Int> get() = unsyncedStationControlPoints(punches, scannedAt)
    }

    /**
     * Мастер-карта станции или неразмеченная метка.
     *
     * @property description расшифровка содержимого (например, состояние станции).
     */
    data class Master(
        val description: String,
        override val scannedAt: Long,
    ) : ChipScan()
}
