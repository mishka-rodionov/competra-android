package com.competra.center.data.read_card

import com.competra.ui.BaseAction

sealed class OrientReadCardAction : BaseAction {
    data class EditSplitClicked(val index: Int) : OrientReadCardAction()
    data class SaveSplitEdit(val index: Int, val newTimestamp: Long) : OrientReadCardAction()
    data class DeleteSplit(val index: Int) : OrientReadCardAction()
    data object DismissEditSplit : OrientReadCardAction()
    /**
     * Засчитать пропущенный КП: отметка вставляется сразу после предыдущего по дистанции КП
     * с его временем (или временем старта) — см. [com.competra.center.presentation.read_card.insertCreditedPunch].
     *
     * @param distanceOrdinal Порядковый номер пропущенного КП по дистанции (1-based).
     */
    data class CreditMissedCp(val cpNumber: Int, val distanceOrdinal: Int) : OrientReadCardAction()
    /** Явное сохранение DSQ-результата в БД после проверки организатором. */
    data object SaveResult : OrientReadCardAction()
}
