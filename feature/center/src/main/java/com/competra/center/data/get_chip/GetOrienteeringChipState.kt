package com.competra.center.data.get_chip

import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.ui.BaseState

/**
 * Состояние экрана выдачи чипов.
 *
 * @property participants Плоский список участников всех групп, отсортированный по номеру чипа
 * (порядок фиксируется при загрузке, чтобы карточки не прыгали во время ввода номера).
 * @property isLoading Флаг загрузки данных.
 * @property isSaving Флаг процесса сохранения данных.
 */
data class GetOrienteeringChipState(
    val participants: List<OrienteeringParticipant> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false
) : BaseState
