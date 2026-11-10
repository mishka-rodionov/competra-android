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
    /** «Назад» при несохранённом результате — показать предупреждение о выходе. */
    data object BackPressedWithPendingSave : OrientReadCardAction()
    /** Остаться на экране — закрыть предупреждение о выходе. */
    data object DismissLeaveConfirm : OrientReadCardAction()
    /** Сохранить результат и закрыть экран. */
    data object SaveAndLeave : OrientReadCardAction()
    /** Закрыть экран без сохранения результата. */
    data object LeaveWithoutSaving : OrientReadCardAction()

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
     * @param isOutOfCompetition Участник вне конкурса (ВК).
     */
    data class CreateParticipantFromRead(
        val lastName: String,
        val firstName: String,
        val commandName: String,
        val groupId: Long,
        val fromHistory: Boolean,
        val isOutOfCompetition: Boolean = false,
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
    /** Открыть карточку «Изменить участника» для участника на экране. */
    data object ShowEditParticipant : OrientReadCardAction()
    /** Закрыть карточку «Изменить участника» без изменений. */
    data object DismissEditParticipant : OrientReadCardAction()
    /**
     * Сохранить ФИО, команду, группу и отметку «Вне конкурса»; при смене группы результат
     * пересчитывается, при смене отметки — места группы.
     */
    data class UpdateParticipantFromRead(
        val lastName: String,
        val firstName: String,
        val commandName: String,
        val groupId: Long,
        val isOutOfCompetition: Boolean = false,
    ) : OrientReadCardAction()
    /** Открыть диалог «Отвязать чип». */
    data object ShowUnlinkChip : OrientReadCardAction()
    /** Закрыть диалог «Отвязать чип». */
    data object DismissUnlinkChip : OrientReadCardAction()
    /**
     * Отвязать чип от участника на экране — считывание возвращается в очередь неразобранных.
     *
     * @param deleteParticipant true — удалить участника (внесён на финише по ошибке); false —
     *   оставить его без чипа и результата.
     */
    data class UnlinkChip(val deleteParticipant: Boolean) : OrientReadCardAction()
}
