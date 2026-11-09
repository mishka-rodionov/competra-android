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

    // --- Режим «Регистрация на финише» ---

    /** Показать список неразобранных считываний. */
    data object ShowUnassignedReads : OrientReadCardAction()
    /** Скрыть список неразобранных считываний. */
    data object HideUnassignedReads : OrientReadCardAction()
    /** Открыть карточку участника для неразобранного считывания чипа [chipNumber]. */
    data class OpenAssignRead(val chipNumber: Int) : OrientReadCardAction()
    /** Закрыть карточку — считывание остаётся в очереди. */
    data object DismissAssignRead : OrientReadCardAction()
    /**
     * Создать участника по считыванию и посчитать его результат.
     *
     * @param fromHistory ФИО взято из подсказки по прошлым соревнованиям.
     */
    data class CreateParticipantFromRead(
        val lastName: String,
        val firstName: String,
        val commandName: String,
        val groupId: Long,
        val fromHistory: Boolean,
    ) : OrientReadCardAction()
    /** Привязать считывание к уже заведённому участнику без результата. */
    data class AssignReadToParticipant(val participantId: String) : OrientReadCardAction()
    /** Удалить ошибочное считывание из очереди. */
    data class DeleteUnassignedRead(val chipNumber: Int) : OrientReadCardAction()
    /** Создать новую группу прямо из карточки участника. */
    data class CreateGroupForRead(val title: String) : OrientReadCardAction()
    /** Вручную снять (DSQ) или засчитать (FINISHED) показанный результат. */
    data class SetManualStatus(val finished: Boolean) : OrientReadCardAction()
    /** Нет отметки финиша — засчитать финиш по времени считывания чипа. */
    data object CreditFinishByReadTime : OrientReadCardAction()
}
