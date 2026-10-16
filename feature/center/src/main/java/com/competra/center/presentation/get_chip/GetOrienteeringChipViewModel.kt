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

    /**
     * Сохраняет только номер чипа и отметку выдачи. Экран открывается и во время соревнования,
     * когда участники параллельно меняются (считывание чипов, синхронизация с сервером), поэтому
     * правки накладываются на свежие данные из БД, а не на снимок, загруженный при открытии экрана.
     */
    private fun saveChanges() {
        val id = competitionId ?: return
        updateState { copy(isSaving = true) }
        viewModelScope.launch {
            val edited = stateValue.participants.associateBy { it.id }
            val changed = orienteeringCompetitionInteractor.getCompetitionWithDetails(id).getOrNull()
                ?.groupsWithParticipants
                ?.flatMap { it.participants }
                ?.mapNotNull { fresh ->
                    val edit = edited[fresh.id] ?: return@mapNotNull null
                    if (fresh.chipNumber == edit.chipNumber && fresh.isChipGiven == edit.isChipGiven) {
                        null
                    } else {
                        fresh.copy(chipNumber = edit.chipNumber, isChipGiven = edit.isChipGiven)
                    }
                }
                .orEmpty()
            if (changed.isNotEmpty()) {
                orienteeringCompetitionInteractor.updateParticipants(changed)
                orienteeringCompetitionInteractor.syncParticipantsAfterDraw(changed)
            }
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
