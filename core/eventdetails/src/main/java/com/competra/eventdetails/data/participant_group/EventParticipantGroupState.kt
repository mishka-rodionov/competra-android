package com.competra.eventdetails.data.participant_group

import com.competra.domain.models.Participant
import com.competra.domain.models.cyclic_event.EventParticipantGroup
import com.competra.domain.models.cyclic_event.GroupEligibility
import com.competra.domain.models.events.EventStatus
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.ui.BaseState

/**
 * Состояние экрана группы участников события.
 * @property eventId Идентификатор события.
 * @property participantGroup Данные о группе участников.
 * @property participants Список участников группы.
 * @property isLoading Флаг загрузки данных.
 * @property isUserRegistered Зарегистрирован ли текущий пользователь в **этой** группе.
 * @property isUserRegisteredInEvent Зарегистрирован ли пользователь хоть в какой-либо группе данного события.
 * @property isRegistering Флаг процесса регистрации/отмены регистрации.
 * @property eventStatus Текущий статус события. Кнопка регистрации показывается только при [EventStatus.REGISTRATION].
 * @property competitionYear Год соревнования в его часовом поясе — от него считается возраст (по году рождения).
 * @property eligibility Подходит ли группа вошедшему пользователю по полу и возрасту; null — не вошёл или не загружено.
 */
data class EventParticipantGroupState(
    val eventId: String? = null,
    val participantGroup: EventParticipantGroup? = null,
    val participants: List<OrienteeringParticipant> = emptyList(),
    val isLoading: Boolean = false,
    val isUserRegistered: Boolean = false,
    val isUserRegisteredInEvent: Boolean = false,
    val isRegistering: Boolean = false,
    val eventStatus: EventStatus? = null,
    val competitionYear: Int? = null,
    val eligibility: GroupEligibility? = null
) : BaseState
