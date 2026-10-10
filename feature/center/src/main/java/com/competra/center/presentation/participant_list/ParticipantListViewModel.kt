package com.competra.center.presentation.participant_list

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.center.data.draw.LATE_ENTRY_PREPARATION_MS
import com.competra.center.data.draw.LateEntryPlacement
import com.competra.center.data.draw.LateEntrySlotFinder
import com.competra.center.data.draw.ProtocolStart
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.center.data.participant_list.LateEntryPreview
import com.competra.center.data.participant_list.ParticipantListAction
import com.competra.center.data.participant_list.ParticipantListState
import com.competra.center.data.participant_list.TestParticipantFixtures
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.getArguments
import com.competra.domain.models.cyclic_event.normalizeCommandName
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.domain.repository.LoadingRepository
import com.competra.domain.repository.orienteering.OrienteeringCompetitionLocalRepository
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import com.competra.utils.constants.EventsConstants
import com.competra.utils.isValidStartTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel для экрана списка участников.
 */
class ParticipantListViewModel(
    private val repository: OrienteeringCompetitionLocalRepository,
    private val competitionInteractor: OrienteeringCompetitionInteractor,
    private val navigation: Navigation,
    private val loadingRepository: LoadingRepository,
    private val analytics: AnalyticsTracker
): BaseViewModel<ParticipantListState>(ParticipantListState()) {

    val competitionId: String? = navigation.getArguments<String>(EventsConstants.EVENT_ID.name)

    override fun onAction(action: BaseAction) {
        when(action) {
            is ParticipantListAction.ShowCreateParticipantDialog -> {
                updateState { copy(group = action.group, editingParticipant = null, isShowParticipantCreateDialog = true) }
                refreshLateEntry()
            }
            is ParticipantListAction.ShowEditParticipantDialog -> {
                updateState { copy(group = action.group, editingParticipant = action.participant, isShowParticipantCreateDialog = true) }
            }
            is ParticipantListAction.CreateNewParticipant -> {
                val groupData = stateValue.participantGroupWithParticipants.getOrNull(action.group) ?: return
                val group = groupData.group
                val intervalMs = ((stateValue.competition?.startIntervalSeconds) ?: 60) * 1000L
                val isDrawConducted = stateValue.competition?.isDrawConducted == true
                val isByStartStation = stateValue.competition?.startTimeMode == StartTimeMode.BY_START_STATION

                val (nextStartNumber, startTime) = if (isByStartStation) {
                    // Жеребьёвки нет, общего времени старта тоже нет — реальное время участник
                    // получит только по факту отметки на стартовой станции (см. OrientReadCardViewModel).
                    val existingParticipants = groupData.participants
                    val nextNum = (existingParticipants.mapNotNull { it.startNumber.toIntOrNull() }.maxOrNull() ?: 0) + 1
                    nextNum to 0L
                } else if (isDrawConducted) {
                    // Дозаявка: номер — следующий сквозной, время — по выбору организатора
                    // (свободная минута по правилам жеребьёвки, конец протокола или вручную).
                    val allParticipants = stateValue.participantGroupWithParticipants.flatMap { it.participants }
                    val maxNumber = allParticipants.mapNotNull { it.startNumber.toIntOrNull() }.maxOrNull() ?: 0
                    val finder = lateEntrySlotFinder()
                    val st = when (action.placement) {
                        LateEntryPlacement.FREE_SLOT -> finder?.freeSlot(group.groupId)
                        LateEntryPlacement.END -> finder?.endOfProtocol()
                        LateEntryPlacement.MANUAL -> action.manualStartTime
                    } ?: 0L
                    (maxNumber + 1) to st
                } else {
                    val existingParticipants = groupData.participants
                    val nextNum = (existingParticipants.mapNotNull { it.startNumber.toIntOrNull() }.maxOrNull() ?: 0) + 1
                    val baseTime = stateValue.competition?.startTime ?: 0L
                    val st = if (baseTime > 0L) baseTime + (nextNum - 1) * intervalMs else 0L
                    nextNum to st
                }

                val participant = OrienteeringParticipant(
                    id = UUID.randomUUID().toString(),
                    userId = "",
                    firstName = action.firstName,
                    lastName = action.secondName,
                    groupId = group.groupId,
                    groupName = group.title,
                    competitionId = group.competitionId,
                    commandName = normalizeCommandName(action.commandName).orEmpty(),
                    startNumber = nextStartNumber.toString(),
                    startTime = startTime,
                    // После жеребьёвки у Sportiduino номер чипа равен стартовому номеру — иначе
                    // дозаявленного не найти при считывании чипа.
                    chipNumber = if (isDrawConducted && !isByStartStation &&
                        stateValue.competition?.punchingSystem == PunchingSystem.SPORTIDUINO
                    ) nextStartNumber.toString() else "",
                    comment = "",
                    isChipGiven = false
                )
                viewModelScope.launch(Dispatchers.IO) {
                    loadingRepository.emit(true)
                    val savedParticipant = competitionInteractor.saveParticipant(participant)
                    if (savedParticipant != null) {
                        getCompetitionDetails()
                    }
                    loadingRepository.emit(false)
                }
            }
            is ParticipantListAction.UpdateParticipant -> {
                onAction(ParticipantListAction.HideCreateParticipantDialog)
                viewModelScope.launch(Dispatchers.IO) {
                    loadingRepository.emit(true)
                    competitionInteractor.updateParticipantLocally(action.participant).onSuccess {
                        getCompetitionDetails()
                        competitionInteractor.syncParticipantWithServer(action.participant)
                    }
                    loadingRepository.emit(false)
                }
            }
            ParticipantListAction.HideCreateParticipantDialog -> {
                updateState { copy(isShowParticipantCreateDialog = false, editingParticipant = null) }
            }
            is ParticipantListAction.ShowDeleteParticipantDialog -> {
                updateState { copy(deletingParticipant = action.participant) }
            }
            ParticipantListAction.HideDeleteParticipantDialog -> {
                updateState { copy(deletingParticipant = null) }
            }
            is ParticipantListAction.DeleteParticipant -> {
                updateState { copy(deletingParticipant = null) }
                viewModelScope.launch(Dispatchers.IO) {
                    competitionInteractor.deleteParticipant(action.participant)
                        .onSuccess { recalculateAfterDeletion(action.participant) }
                        .onFailure { e -> updateState { copy(errorMessage = e.message) } }
                }
            }
            is ParticipantListAction.SetParticipantDns -> {
                viewModelScope.launch(Dispatchers.IO) {
                    competitionInteractor.setParticipantDns(action.participant, action.isDns)
                        .onSuccess {
                            if (action.isDns) {
                                analytics.trackEvent(
                                    AnalyticsEvent.ParticipantDnsMarked(action.participant.competitionId)
                                )
                            }
                            getCompetitionDetails()
                        }
                        .onFailure { e -> updateState { copy(errorMessage = e.message) } }
                }
            }
            ParticipantListAction.DismissError -> {
                updateState { copy(errorMessage = null) }
            }
            ParticipantListAction.GenerateTestParticipants -> {
                generateTestParticipants()
            }
        }
    }

    /**
     * Генерирует [TestParticipantFixtures.DEFAULT_COUNT] тестовых участников для каждой
     * пустой группы. Стартовые номера и времена считаются по той же логике, что и при
     * ручном добавлении (номера 1..N в группе, время от старта соревнования с шагом
     * стартового интервала). Группы с уже добавленными участниками пропускаются —
     * повторное нажатие не плодит дубликаты.
     *
     * Вызывается только из debug-сборки для тестового соревнования (проверка в UI).
     */
    private fun generateTestParticipants() {
        val competition = stateValue.competition ?: return
        if (!competition.competition.isTest) return

        val groups = stateValue.participantGroupWithParticipants
        val intervalMs = (competition.startIntervalSeconds ?: 60) * 1000L
        val baseTime = competition.startTime ?: 0L

        viewModelScope.launch(Dispatchers.IO) {
            loadingRepository.emit(true)
            groups.forEachIndexed { groupIndex, groupData ->
                if (groupData.participants.isNotEmpty()) return@forEachIndexed
                val group = groupData.group
                val names = TestParticipantFixtures.names(group.gender, seed = groupIndex)
                names.forEachIndexed { i, name ->
                    val startNumber = i + 1
                    val startTime = if (baseTime > 0L) baseTime + (startNumber - 1) * intervalMs else 0L
                    val participant = OrienteeringParticipant(
                        id = UUID.randomUUID().toString(),
                        userId = "",
                        firstName = name.firstName,
                        lastName = name.lastName,
                        groupId = group.groupId,
                        groupName = group.title,
                        competitionId = group.competitionId,
                        commandName = "",
                        startNumber = startNumber.toString(),
                        startTime = startTime,
                        chipNumber = "",
                        comment = "",
                        isChipGiven = false
                    )
                    competitionInteractor.saveParticipant(participant)
                }
            }
            getCompetitionDetails()
            loadingRepository.emit(false)
        }
    }

    /**
     * После удаления участника сдвигает стартовые номера и времена
     * всех участников той же группы, которые шли после удалённого.
     *
     * После жеребьёвки протокол не трогаем: номера и чипы уже выданы, а номера сквозные
     * по всем группам — сдвиг внутри группы дал бы дубли.
     */
    private suspend fun recalculateAfterDeletion(deletedParticipant: OrienteeringParticipant) {
        if (stateValue.competition?.isDrawConducted == true) {
            getCompetitionDetails()
            return
        }
        val deletedNumber = deletedParticipant.startNumber.toIntOrNull() ?: run {
            getCompetitionDetails()
            return
        }
        val intervalMs = ((stateValue.competition?.startIntervalSeconds) ?: 60) * 1000L

        val groupData = stateValue.participantGroupWithParticipants
            .find { it.group.groupId == deletedParticipant.groupId } ?: run {
            getCompetitionDetails()
            return
        }

        val toUpdate = groupData.participants
            .filter { it.id != deletedParticipant.id }
            .mapNotNull { p ->
                val num = p.startNumber.toIntOrNull() ?: return@mapNotNull null
                if (num > deletedNumber) {
                    p.copy(
                        startNumber = (num - 1).toString(),
                        startTime = if (p.startTime > 0L) p.startTime - intervalMs else p.startTime
                    )
                } else null
            }

        if (toUpdate.isNotEmpty()) {
            competitionInteractor.updateParticipants(toUpdate)
        }
        getCompetitionDetails()
    }

    /**
     * Загружает данные соревнования и участников.
     */
    fun getCompetitionDetails() {
        Log.d("LOG_TAG", "getCompetitionDetails: competitionId = $competitionId")
        viewModelScope.launch {
            competitionId?.let { compId ->
                repository.getCompetitionWithDetails(compId).onSuccess {
                    updateState {
                        copy(
                            competition = it.competition,
                            participantGroupWithParticipants = it.groupsWithParticipants
                        )
                    }
                    // Диалог добавления остаётся открытым для следующего участника — после
                    // добавления предыдущего свободная минута сместилась.
                    if (stateValue.isShowParticipantCreateDialog && stateValue.editingParticipant == null) {
                        refreshLateEntry()
                    }
                }
                // Статусы результатов нужны после старта: по ним показывается отметка
                // «Не стартовал» вместо удаления.
                competitionInteractor.getResultsByGroups(compId).onSuccess { groups ->
                    val statuses = groups
                        .flatMap { it.participants }
                        .mapNotNull { pw -> pw.result?.let { pw.participant.id to it.status } }
                        .toMap()
                    updateState { copy(resultStatuses = statuses) }
                }
            }
        }
    }

    /**
     * Подбор стартовой минуты для дозаявки; null — жеребьёвки не было или при старте по стартовой
     * станции (там время берётся из отметки).
     */
    private fun lateEntrySlotFinder(): LateEntrySlotFinder? {
        val competition = stateValue.competition ?: return null
        if (!competition.isDrawConducted || competition.startTimeMode == StartTimeMode.BY_START_STATION) return null
        val groups = stateValue.participantGroupWithParticipants
        return LateEntrySlotFinder(
            starts = groups.flatMap { it.participants }
                .filter { it.startTime.isValidStartTimestamp() }
                .map { ProtocolStart(startTime = it.startTime, groupId = it.groupId) },
            groupDistanceMap = groups.associate { it.group.groupId to it.group.distanceId },
            drawSettings = competition.drawSettings,
            intervalMs = (competition.startIntervalSeconds ?: 60) * 1000L,
            fallbackAnchor = competition.startTime?.takeIf { it.isValidStartTimestamp() }
                ?: competition.competition.startDate,
            notBefore = System.currentTimeMillis() + LATE_ENTRY_PREPARATION_MS
        )
    }

    /** Пересчитывает варианты стартового времени дозаявки для группы открытого диалога. */
    private fun refreshLateEntry() {
        val finder = lateEntrySlotFinder()
        val groupId = stateValue.participantGroupWithParticipants.getOrNull(stateValue.group)?.group?.groupId
        val preview = if (finder != null && groupId != null) {
            LateEntryPreview(freeSlotTime = finder.freeSlot(groupId), endTime = finder.endOfProtocol())
        } else null
        updateState { copy(lateEntry = preview) }
    }
}
