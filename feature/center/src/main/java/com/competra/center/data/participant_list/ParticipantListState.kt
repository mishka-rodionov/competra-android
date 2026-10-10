package com.competra.center.data.participant_list

import com.competra.center.data.draw.LateEntryPlacement
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.ParticipantGroupParticipants
import com.competra.ui.BaseState

/**
 * Состояние экрана списка участников.
 *
 * @property participantGroupWithParticipants Список групп с участниками.
 * @property competition Данные текущего соревнования (для расчёта стартового времени).
 * @property isShowParticipantCreateDialog Флаг отображения диалога создания/редактирования.
 * @property group Индекс текущей выбранной группы.
 * @property editingParticipant Участник, который редактируется в данный момент (null для создания).
 * @property deletingParticipant Участник, для которого открыт диалог подтверждения удаления.
 * @property resultStatuses Статусы результатов по id участника (нет ключа — результата нет).
 * @property errorMessage Сообщение об ошибке последнего действия (null — ошибки нет).
 * @property lateEntry Варианты стартового времени для дозаявки после жеребьёвки
 *   (null — жеребьёвки не было, время назначается как обычно).
 */
data class ParticipantListState(
    val participantGroupWithParticipants: List<ParticipantGroupParticipants> = emptyList(),
    val competition: OrienteeringCompetition? = null,
    val isShowParticipantCreateDialog: Boolean = false,
    val group: Int = 0,
    val editingParticipant: OrienteeringParticipant? = null,
    val deletingParticipant: OrienteeringParticipant? = null,
    val resultStatuses: Map<String, ResultStatus> = emptyMap(),
    val errorMessage: String? = null,
    val lateEntry: LateEntryPreview? = null
): BaseState

/**
 * Предпросмотр стартового времени дозаявки для открытой группы. Окончательное время
 * пересчитывается в момент добавления — за это время минута могла уйти.
 *
 * @property freeSlotTime Ближайшая свободная минута ([LateEntryPlacement.FREE_SLOT]).
 * @property endTime Минута после последнего старта протокола ([LateEntryPlacement.END]).
 */
data class LateEntryPreview(
    val freeSlotTime: Long,
    val endTime: Long
)
