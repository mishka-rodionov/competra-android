package com.competra.center.data.chip_inspect

import com.competra.domain.models.orienteering.SplitTime
import com.competra.ui.BaseState

/**
 * Состояние экрана «Считать / Проверить».
 *
 * @property lastScan последний считанный чип; `null` — ещё ничего не прикладывали.
 * @property scanCount сколько чипов считано за время открытия экрана (удобно при проверке
 * чипов на старте подряд — видно, что новый скан действительно прошёл).
 */
data class ChipInspectState(
    val lastScan: ChipScan? = null,
    val scanCount: Int = 0,
) : BaseState

/**
 * Результат чтения метки на экране проверки.
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
