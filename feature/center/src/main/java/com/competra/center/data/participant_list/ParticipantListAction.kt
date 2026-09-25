package com.competra.center.data.participant_list

import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.ui.BaseAction

/**
 * Действия на экране списка участников.
 */
sealed class ParticipantListAction : BaseAction {

    /**
     * Показать диалог создания нового участника.
     * @param group Индекс группы в списке.
     */
    data class ShowCreateParticipantDialog(val group: Int): ParticipantListAction()
    
    /**
     * Показать диалог редактирования участника.
     * @param group Индекс группы в списке.
     * @param participant Участник для редактирования.
     */
    data class ShowEditParticipantDialog(val group: Int, val participant: OrienteeringParticipant): ParticipantListAction()
    
    /**
     * Скрыть диалог.
     */
    data object HideCreateParticipantDialog: ParticipantListAction()

    /**
     * Создать нового участника.
     */
    data class CreateNewParticipant(val group: Int, val firstName: String, val secondName: String) :
        ParticipantListAction()

    /**
     * Обновить данные существующего участника.
     */
    data class UpdateParticipant(val participant: OrienteeringParticipant) : ParticipantListAction()

    /**
     * Показать диалог подтверждения удаления участника.
     * @param participant Участник для удаления.
     */
    data class ShowDeleteParticipantDialog(val participant: OrienteeringParticipant) : ParticipantListAction()

    /**
     * Скрыть диалог подтверждения удаления.
     */
    data object HideDeleteParticipantDialog : ParticipantListAction()

    /**
     * Удалить участника и пересчитать список группы.
     * @param participant Участник для удаления.
     */
    data class DeleteParticipant(val participant: OrienteeringParticipant) : ParticipantListAction()

    /**
     * Поставить или снять участнику статус «Не стартовал» (DNS). Используется после старта
     * соревнования вместо удаления.
     * @param participant Участник.
     * @param isDns true — отметить «Не стартовал», false — снять отметку.
     */
    data class SetParticipantDns(val participant: OrienteeringParticipant, val isDns: Boolean) : ParticipantListAction()

    /**
     * Скрыть сообщение об ошибке.
     */
    data object DismissError : ParticipantListAction()

    /**
     * Сгенерировать тестовых участников для каждой пустой группы (только debug-сборка,
     * только тестовое соревнование).
     */
    data object GenerateTestParticipants : ParticipantListAction()

}
