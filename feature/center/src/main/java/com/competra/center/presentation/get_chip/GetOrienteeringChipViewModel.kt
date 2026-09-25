package com.competra.center.presentation.get_chip

import androidx.lifecycle.viewModelScope
import com.competra.center.data.get_chip.GetOrienteeringChipAction
import com.competra.center.data.get_chip.GetOrienteeringChipState
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.data.navigation.Navigation
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel для экрана выдачи чипов участникам.
 *
 * @property orienteeringCompetitionInteractor Интерактор для работы с данными соревнований.
 * @property navigation Навигация для возврата на предыдущий экран.
 */
class GetOrienteeringChipViewModel(
    private val orienteeringCompetitionInteractor: OrienteeringCompetitionInteractor,
    private val navigation: Navigation
) : BaseViewModel<GetOrienteeringChipState>(GetOrienteeringChipState()) {

    private var competitionId: String? = null

    /**
     * Загружает участников всех групп соревнования плоским списком, отсортированным по номеру чипа.
     * @param competitionId ID соревнования.
     */
    fun loadParticipants(competitionId: String) {
        this.competitionId = competitionId
        updateState { copy(isLoading = true) }
        viewModelScope.launch {
            orienteeringCompetitionInteractor.getCompetitionWithDetails(competitionId).onSuccess { details ->
                updateState {
                    copy(
                        participants = details.groupsWithParticipants
                            .flatMap { it.participants }
                            .sortedWith(chipOrderComparator),
                        isLoading = false
                    )
                }
            }.onFailure {
                updateState { copy(isLoading = false) }
            }
        }
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            is GetOrienteeringChipAction.UpdateChipNumber -> {
                updateParticipantInState(action.participantId) {
                    it.copy(chipNumber = action.chipNumber)
                }
            }

            is GetOrienteeringChipAction.ToggleChipGiven -> {
                // Реализация переключения состояния чекбокса в стейте
                updateParticipantInState(action.participantId) {
                    it.copy(isChipGiven = action.isGiven)
                }
            }

            GetOrienteeringChipAction.SaveChanges -> {
                saveChanges()
            }
        }
    }

    private fun updateParticipantInState(
        participantId: String,
        update: (OrienteeringParticipant) -> OrienteeringParticipant
    ) {
        // Порядок списка сохраняется — пересортировка происходит только при следующей загрузке.
        val updatedParticipants = stateValue.participants.map { participant ->
            if (participant.id == participantId) update(participant) else participant
        }
        updateState { copy(participants = updatedParticipants) }
    }

    private fun saveChanges() {
        updateState { copy(isSaving = true) }
        viewModelScope.launch {
            val allParticipants = stateValue.participants
            orienteeringCompetitionInteractor.updateParticipants(allParticipants)
            orienteeringCompetitionInteractor.syncParticipantsAfterDraw(allParticipants)
            updateState { copy(isSaving = false) }
            navigation.back()
        }
    }

    private companion object {
        /**
         * Порядок выдачи: сначала участники с числовым номером чипа по возрастанию, затем
         * с нечисловым (по строке), в конце — без чипа (по фамилии и имени).
         */
        val chipOrderComparator: Comparator<OrienteeringParticipant> = compareBy(
            { chipSortBucket(it.chipNumber) },
            { it.chipNumber.trim().toLongOrNull() ?: Long.MAX_VALUE },
            { it.chipNumber.trim() },
            { it.lastName },
            { it.firstName }
        )

        private fun chipSortBucket(chipNumber: String): Int {
            val trimmed = chipNumber.trim()
            return when {
                trimmed.isEmpty() -> 2
                trimmed.toLongOrNull() != null -> 0
                else -> 1
            }
        }
    }
}
