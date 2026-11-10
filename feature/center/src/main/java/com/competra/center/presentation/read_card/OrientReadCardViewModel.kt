package com.competra.center.presentation.read_card

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.center.data.read_card.CheckResult
import com.competra.center.data.read_card.OrientReadCardState
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.getArguments
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.ByChoiceMode
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.models.orienteering.ControlPoint
import com.competra.domain.models.orienteering.ControlPointRole
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.ReadChipData
import com.competra.domain.models.orienteering.applyOvertimePolicy
import com.competra.domain.models.orienteering.effectiveControlTimeMinutes
import com.competra.domain.models.orienteering.ResultConflictEvent
import com.competra.domain.models.orienteering.SplitTime
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.domain.models.orienteering.expectedSequence
import com.competra.domain.models.orienteering.startPoint
import com.competra.domain.models.orienteering.punchesAfterStart
import com.competra.domain.models.orienteering.unsyncedStationControlPoints
import com.competra.domain.models.orienteering.withoutDistance
import com.competra.domain.models.orienteering.ParticipantHistory
import com.competra.domain.models.orienteering.UnassignedChipRead
import com.competra.domain.models.orienteering.toRawResult
import com.competra.domain.models.orienteering.matchingGroupIdsByPunches
import com.competra.domain.models.cyclic_event.normalizeCommandName
import com.competra.domain.models.orienteering.NO_DISTANCE_ID
import com.competra.domain.repository.orienteering.OrienteeringCompetitionLocalRepository
import com.competra.domain.repository.orienteering.UnassignedChipReadRepository
import com.competra.center.data.read_card.finishPunch
import com.competra.domain.repository.ResultConflictRepository
import com.competra.nfchelper.SportiduinoHelper
import com.competra.center.data.read_card.OrientReadCardAction
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import com.competra.utils.constants.EventsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlinx.coroutines.launch
import kotlin.math.ceil

class OrientReadCardViewModel(
    private val sportiduinoHelper: SportiduinoHelper,
    private val orienteeringCompetitionInteractor: OrienteeringCompetitionInteractor,
    private val resultConflictRepository: ResultConflictRepository,
    private val unassignedChipReadRepository: UnassignedChipReadRepository,
    private val localRepository: OrienteeringCompetitionLocalRepository,
    private val navigation: Navigation,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<OrientReadCardState>(OrientReadCardState()) {

    val competitionId: String? = navigation.getArguments<String>(EventsConstants.EVENT_ID.name)

    /**
     * Исходное считывание чипа, результат которого сейчас на экране (режим «Регистрация на финише»):
     * по нему результат пересчитывается при смене группы, и оно возвращается в очередь при
     * «Отвязать чип». Отметки в state могут быть уже поправлены организатором — здесь нет.
     */
    private var currentRead: UnassignedChipRead? = null

    override fun onAction(action: BaseAction) {
        if (action !is OrientReadCardAction) return
        when (action) {
            is OrientReadCardAction.EditSplitClicked -> {
                updateState { copy(editingSplitIndex = action.index) }
            }
            is OrientReadCardAction.DismissEditSplit -> {
                updateState { copy(editingSplitIndex = null) }
            }
            is OrientReadCardAction.SaveSplitEdit -> {
                analytics.trackEvent(AnalyticsEvent.SplitEditSaved)
                val updated = stateValue.rawSplits?.toMutableList() ?: return
                updated[action.index] = updated[action.index].copy(timestamp = action.newTimestamp)
                updated.sortBy { it.timestamp }
                updateState { copy(rawSplits = updated) }
                viewModelScope.launch(Dispatchers.IO) { recalculateAndSaveResult() }
            }
            is OrientReadCardAction.DeleteSplit -> {
                analytics.trackEvent(AnalyticsEvent.SplitDeleted)
                val updated = stateValue.rawSplits?.toMutableList() ?: return
                updated.removeAt(action.index)
                updateState { copy(rawSplits = updated) }
                viewModelScope.launch(Dispatchers.IO) { recalculateAndSaveResult() }
            }
            is OrientReadCardAction.CreditMissedCp -> {
                val rawSplits = stateValue.rawSplits ?: return
                val updated = insertCreditedPunch(
                    rawSplits = rawSplits,
                    expectedCpNumbers = stateValue.expectedCpNumbers,
                    cpNumber = action.cpNumber,
                    distanceOrdinal = action.distanceOrdinal,
                    startTime = stateValue.participant?.startTime,
                    startControlPoint = stateValue.startControlPoint,
                )
                updateState { copy(rawSplits = updated) }
                viewModelScope.launch(Dispatchers.IO) { recalculatePending() }
            }
            is OrientReadCardAction.SaveResult -> {
                viewModelScope.launch(Dispatchers.IO) { saveResultFromPending() }
            }
            OrientReadCardAction.BackPressedWithPendingSave -> updateState { copy(isLeaveConfirmShown = true) }
            OrientReadCardAction.DismissLeaveConfirm -> updateState { copy(isLeaveConfirmShown = false) }
            OrientReadCardAction.SaveAndLeave -> {
                updateState { copy(isLeaveConfirmShown = false) }
                viewModelScope.launch(Dispatchers.IO) {
                    saveResultFromPending()
                    launch(Dispatchers.Main) { navigation.back() }
                }
            }
            OrientReadCardAction.LeaveWithoutSaving -> {
                updateState { copy(isLeaveConfirmShown = false) }
                viewModelScope.launch(Dispatchers.Main) { navigation.back() }
            }
            OrientReadCardAction.ShowUnassignedReads -> updateState { copy(isUnassignedListShown = true) }
            OrientReadCardAction.HideUnassignedReads -> updateState { copy(isUnassignedListShown = false) }
            is OrientReadCardAction.OpenAssignRead -> {
                val read = stateValue.unassignedReads.firstOrNull { it.chipNumber == action.chipNumber } ?: return
                updateState { copy(isUnassignedListShown = false) }
                viewModelScope.launch(Dispatchers.IO) { openAssign(read, AnalyticsEvent.FinishReadSource.QUEUE) }
            }
            OrientReadCardAction.DismissAssignRead -> updateState { copy(assigningRead = null, createdGroupId = null) }
            is OrientReadCardAction.CreateParticipantFromRead -> {
                viewModelScope.launch(Dispatchers.IO) { createParticipantFromRead(action) }
            }
            is OrientReadCardAction.AssignReadToParticipant -> {
                viewModelScope.launch(Dispatchers.IO) { assignReadToParticipant(action.participantId) }
            }
            is OrientReadCardAction.DeleteUnassignedRead -> {
                val cid = competitionId ?: return
                viewModelScope.launch(Dispatchers.IO) { unassignedChipReadRepository.delete(cid, action.chipNumber) }
            }
            is OrientReadCardAction.CreateGroupForRead -> {
                viewModelScope.launch(Dispatchers.IO) { createGroupForRead(action.title) }
            }
            is OrientReadCardAction.SetManualStatus -> {
                viewModelScope.launch(Dispatchers.IO) { setManualStatus(action.finished) }
            }
            OrientReadCardAction.ShowEditParticipant -> {
                val cid = competitionId ?: return
                viewModelScope.launch(Dispatchers.IO) {
                    val groups = loadAssignGroups(cid)
                    val distances = orienteeringCompetitionInteractor.getDistances(cid).getOrNull().orEmpty()
                    val splits = currentRead?.splits ?: stateValue.rawSplits.orEmpty()
                    updateState {
                        copy(
                            isEditParticipantShown = true,
                            assignGroups = groups,
                            assignMatchingGroupIds = matchingGroupIdsByPunches(splits, groups, distances),
                            createdGroupId = null
                        )
                    }
                }
            }
            OrientReadCardAction.DismissEditParticipant -> updateState { copy(isEditParticipantShown = false, createdGroupId = null) }
            is OrientReadCardAction.UpdateParticipantFromRead -> {
                viewModelScope.launch(Dispatchers.IO) { updateParticipantFromRead(action) }
            }
            OrientReadCardAction.ShowUnlinkChip -> updateState { copy(isUnlinkChipShown = true) }
            OrientReadCardAction.DismissUnlinkChip -> updateState { copy(isUnlinkChipShown = false) }
            is OrientReadCardAction.UnlinkChip -> {
                updateState { copy(isUnlinkChipShown = false) }
                viewModelScope.launch(Dispatchers.IO) { unlinkChip(action.deleteParticipant) }
            }
            OrientReadCardAction.CreditFinishByReadTime -> {
                val finishCode = stateValue.finishControlPoint ?: return
                val readAt = stateValue.participantResult?.readAt ?: return
                val rawSplits = stateValue.rawSplits ?: return
                // Синтетическая отметка финиша во время считывания — дальше обычная проверка, как
                // у «Засчитать КП»: организатор сохраняет результат кнопкой «Сохранить».
                val updated = (rawSplits + SplitTime(controlPoint = finishCode, timestamp = readAt)).sortedBy { it.timestamp }
                updateState { copy(rawSplits = updated, isPendingSave = true) }
                viewModelScope.launch(Dispatchers.IO) { recalculatePending() }
            }
        }
    }

    private suspend fun recalculateAndSaveResult() {
        val participant = stateValue.participant ?: return
        val rawSplits = stateValue.rawSplits ?: return
        if (rawSplits.isEmpty()) return
        val expected = getExpectedControlPoints(participant.groupId)
        val startTime = resolveStartTime(participant, rawSplits)
        val newResult = if (startTime == null) {
            OrienteeringResult(
                competitionId = participant.competitionId,
                participantId = participant.id,
                groupId = participant.groupId,
                startTime = participant.startTime,
                finishTime = null,
                totalTime = null,
                rank = -1,
                status = ResultStatus.DSQ,
                penaltyTime = 0,
                splits = rawSplits,
                isEdited = true,
                readAt = stateValue.participantResult?.readAt
            )
        } else {
            val checkResult = computeCheckResult(participant.groupId, startTime, expected, rawSplits)
            val lastValidPunch = checkResult.validSplits.lastOrNull() ?: rawSplits.last()
            val finishTime = lastValidPunch.timestamp
            val totalTime = (finishTime - startTime) / 1000L
            val effectiveStatus = applyControlTime(participant.groupId, totalTime, checkResult.status)
            if (stateValue.startTimeMode == StartTimeMode.BY_START_STATION && startTime != participant.startTime) {
                orienteeringCompetitionInteractor.updateParticipantLocally(participant.copy(startTime = startTime))
            }
            OrienteeringResult(
                competitionId = participant.competitionId,
                participantId = participant.id,
                groupId = participant.groupId,
                startTime = startTime,
                finishTime = finishTime,
                totalTime = totalTime,
                rank = -1,
                status = effectiveStatus,
                penaltyTime = 0,
                totalScore = checkResult.totalScore,
                scorePenalty = checkResult.scorePenalty,
                splits = checkResult.validSplits,
                isEdited = true,
                readAt = stateValue.participantResult?.readAt
            )
        }
        updateState { copy(participantResult = newResult, editingSplitIndex = null) }
        val existing = orienteeringCompetitionInteractor.getResultByParticipantId(participant.id)
        if (existing != null) {
            orienteeringCompetitionInteractor.applyConflictResult(existing, newResult)
        } else {
            orienteeringCompetitionInteractor.saveParticipantResult(newResult)
        }
        refreshGroupRank(participant)
    }

    private suspend fun refreshGroupRank(participant: OrienteeringParticipant) {
        val cid = competitionId ?: return
        val updatedResult = orienteeringCompetitionInteractor.getResultByParticipantId(participant.id)
        val groupRank = updatedResult?.rank?.takeIf { it > 0 }
        val groupsData = orienteeringCompetitionInteractor.getResultsByGroups(cid).getOrNull()
        val groupParticipants = groupsData?.firstOrNull { it.group.groupId == participant.groupId }?.participants
        val totalFinished = groupParticipants?.count { it.result?.status == ResultStatus.FINISHED } ?: 0
        updateState { copy(groupRank = groupRank, groupTotalFinished = totalFinished) }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            competitionId?.let { id ->
                val competition = orienteeringCompetitionInteractor.getCompetition(id)
                if (competition?.competition?.status == CompetitionStatus.FINISHED) {
                    updateState { copy(isCompetitionFinished = true) }
                }
                val direction = competition?.direction
                val startTimeMode = competition?.startTimeMode
                updateState {
                    copy(
                        competitionControlTimeMinutes = competition?.controlTimeMinutes,
                        overtimePolicy = competition?.overtimePolicy ?: this.overtimePolicy
                    )
                }
                if (direction != null) {
                    val byChoiceMode = competition?.byChoiceMode ?: ByChoiceMode.DEFAULT
                    val isFinishRegistration = competition?.isFinishRegistration == true
                    updateState {
                        copy(
                            competitionDirection = direction,
                            byChoiceMode = byChoiceMode,
                            startTimeMode = startTimeMode ?: this.startTimeMode,
                            isFinishRegistration = isFinishRegistration,
                            defaultStartStationCode = competition?.punchingSystem?.defaultStartStationCode,
                            defaultFinishStationCode = competition?.punchingSystem?.defaultFinishStationCode
                        )
                    }
                    // updateState — fire-and-forget (постит апдейт на Main.immediate и не ждёт его
                    // применения). Если чип уже лежит на ридере в момент открытия экрана (обычный
                    // рабочий процесс судьи), скан может дойти до subscribeToReadCard раньше, чем
                    // competitionDirection фактически обновится в state, и computeCheckResult
                    // ошибочно уйдёт в ветку checkControlPointOrderPro (без дедупликации повторных
                    // отметок и без учёта баллов КП) вместо computeByChoiceResult. Дожидаемся
                    // реального применения, чтобы гарантировать порядок.
                    state.first {
                        it.competitionDirection == direction && it.byChoiceMode == byChoiceMode &&
                            it.isFinishRegistration == isFinishRegistration
                    }
                    if (isFinishRegistration) {
                        viewModelScope.launch {
                            unassignedChipReadRepository.observe(id).collect { reads ->
                                val matches = matchingGroupTitles(id, reads)
                                updateState { copy(unassignedReads = reads, unassignedMatchingGroups = matches) }
                            }
                        }
                        loadParticipantHistory(id)
                    }
                }
                // В режиме «Регистрация на финише» группа без дистанции — норма: считается по времени.
                if (competition?.isFinishRegistration != true) loadGroupsWithoutDistance(id)
            }
            sportiduinoHelper.subscribeToReadCard { chipData ->
                handleChipData(chipData)
            }
        }
    }

    fun handleChipData(chipData: ReadChipData) {
        // После завершения соревнования чип всё ещё можно отсканировать, но данные
        // показываются только для просмотра и не сохраняются (см. createParticipantResult).
        val cardType = when (chipData) {
            is ReadChipData.RawResult -> AnalyticsEvent.NfcCardType.PARTICIPANT
            is ReadChipData.MasterChipData -> AnalyticsEvent.NfcCardType.MASTER
        }
        analytics.trackEvent(AnalyticsEvent.NfcChipReadAttempted(cardType))
        val readAt = System.currentTimeMillis()
        val unsyncedCps = (chipData as? ReadChipData.RawResult)
            ?.let { unsyncedStationControlPoints(it.splits, readAt = readAt) }
            .orEmpty()
        updateState { copy(unsyncedStationCps = unsyncedCps) }
        when (chipData) {
            is ReadChipData.RawResult -> {
                competitionId?.let {
                    viewModelScope.launch(Dispatchers.IO) {
                        orienteeringCompetitionInteractor.getParticipantByChipNumber(
                            competitionId = competitionId,
                            chipNumber = chipData.chipNumber
                        ).onSuccess { participant ->
                            analytics.trackEvent(AnalyticsEvent.NfcChipReadSuccess)
                            if (getDistance(participant.groupId) == null && !stateValue.isFinishRegistration) {
                                updateState {
                                    copy(
                                        scanNotice = "Группе ${participant.groupName} (${participant.lastName} " +
                                            "${participant.firstName}) не назначена дистанция — результат не посчитан. " +
                                            "Назначьте дистанцию и прочитайте чип ещё раз"
                                    )
                                }
                                return@onSuccess
                            }
                            val notice = if (chipData.splits.isEmpty()) {
                                "На чипе №${chipData.chipNumber} (${participant.lastName} ${participant.firstName}) нет отметок"
                            } else {
                                null
                            }
                            updateState { copy(scanNotice = notice) }
                            currentRead = UnassignedChipRead(
                                competitionId = competitionId,
                                chipNumber = chipData.chipNumber,
                                readAt = readAt,
                                clearTime = chipData.clearTime,
                                splits = chipData.splits
                            )
                            computeParticipantResult(
                                participant = participant,
                                rawResult = chipData,
                                readAt = readAt
                            )
                        }.onFailure {
                            if (stateValue.isFinishRegistration && !stateValue.isCompetitionFinished) {
                                analytics.trackEvent(AnalyticsEvent.NfcChipReadSuccess)
                                queueUnknownRead(chipData, readAt)
                                return@onFailure
                            }
                            analytics.trackEvent(AnalyticsEvent.NfcChipReadFailed("participant_not_found"))
                            updateState {
                                copy(scanNotice = "Чип №${chipData.chipNumber} не найден среди участников соревнования")
                            }
                        }
                    }
                }
            }

            is ReadChipData.MasterChipData -> {
                analytics.trackEvent(AnalyticsEvent.NfcChipReadSuccess)
                updateState { copy(scanNotice = "Приложена мастер-карта станции, а не чип участника") }
            }
        }
    }

    /** Группы с участниками, но без дистанции — см. [OrientReadCardState.groupsWithoutDistance]. */
    private suspend fun loadGroupsWithoutDistance(competitionId: String) {
        val details = orienteeringCompetitionInteractor.getCompetitionWithDetails(competitionId).getOrNull() ?: return
        val distances = orienteeringCompetitionInteractor.getDistances(competitionId).getOrNull().orEmpty()
        val titles = details.groupsWithParticipants
            .filter { it.participants.isNotEmpty() }
            .map { it.group }
            .withoutDistance(distances)
            .map { it.title }
        updateState { copy(groupsWithoutDistance = titles) }
    }

    /** КП дистанции по порядку плюс финишный пункт (с координатами финиша, если известны). */
    suspend fun getExpectedControlPoints(groupId: Long): List<ControlPoint> {
        val distance = getDistance(groupId) ?: return emptyList()
        // Финиш не задан у дистанции — берём стандартный код финишной станции системы отметки.
        val withFinish = if (distance.finishControlPoint == null) {
            distance.copy(finishControlPoint = stateValue.defaultFinishStationCode)
        } else {
            distance
        }
        return withFinish.expectedSequence()
    }

    /** Стартовый КП дистанции группы, а если не задан (или дистанции нет) — код стартовой станции по умолчанию. */
    private suspend fun effectiveStartControlPoint(groupId: Long): Int? =
        getDistance(groupId)?.startControlPoint ?: stateValue.defaultStartStationCode

    private suspend fun getDistance(groupId: Long): Distance? {
        val group = orienteeringCompetitionInteractor.getParticipantGroup(groupId).getOrNull()
            ?: return null
        return orienteeringCompetitionInteractor.getDistanceById(group.distanceId).getOrNull()
    }

    /**
     * Реальное время старта участника для текущего режима старта соревнования.
     *
     * Для [StartTimeMode.BY_START_STATION] — это timestamp отметки на стартовом КП дистанции
     * среди прочитанных с чипа сплитов (участник отмечается на отдельной физической старт-станции
     * самостоятельно, до начала дистанции), а не заранее назначенное на жеребьёвке
     * [OrienteeringParticipant.startTime]. Возвращает `null`, если режим BY_START_STATION, а
     * отметки на старте в чипе нет — участник ещё не стартовал (это не то же самое, что "время
     * не назначено", и требует отдельной обработки на стороне вызывающего кода — см.
     * [computeParticipantResult]).
     *
     * Для остальных режимов поведение не меняется — возвращается [OrienteeringParticipant.startTime].
     */
    private suspend fun resolveStartTime(participant: OrienteeringParticipant, splits: List<SplitTime>): Long? {
        if (stateValue.startTimeMode != StartTimeMode.BY_START_STATION) return participant.startTime
        val startCp = effectiveStartControlPoint(participant.groupId) ?: return null
        return splits.firstOrNull { it.controlPoint == startCp }?.timestamp
    }

    /**
     * Считает результат участника по отметкам чипа и сохраняет его (DSQ — ждёт «Сохранить»).
     *
     * @param replaceExisting Заменить уже сохранённый результат без диалога конфликта (и при DSQ):
     *   организатор сменил группу участника, и старый результат относится к прежней группе.
     */
    suspend fun computeParticipantResult(
        participant: OrienteeringParticipant,
        rawResult: ReadChipData.RawResult,
        readAt: Long? = null,
        replaceExisting: Boolean = false
    ) {
        val splits = rawResult.splits
        Log.d("LOG_TAG", "computeParticipantResult: $splits")
        if (splits.isEmpty()) return
        val expected = getExpectedControlPoints(participant.groupId)
        Log.d("LOG_TAG", "computeParticipantResult: $expected")
        val expectedCpNumbers = expected.map { it.number }
        val startControlPoint = effectiveStartControlPoint(participant.groupId)
        val finishControlPoint = getDistance(participant.groupId)?.finishControlPoint ?: stateValue.defaultFinishStationCode
        updateState { copy(finishControlPoint = finishControlPoint) }

        val startTime = resolveStartTime(participant, splits)
        if (startTime == null) {
            // BY_START_STATION без отметки на стартовой станции — считать результат не по чему.
            // Различаем две причины: у дистанции вообще не задано стартовое КП (ошибка настройки
            // организатора) — и стартовое КП задано, но именно на чипе этого участника отметки
            // о нём нет (участник не отметился на старт-станции).
            val message = if (startControlPoint != null) {
                "Нет отметки на стартовой станции"
            } else {
                "У дистанции не задано стартовое КП — укажите его в редактировании дистанции"
            }
            createParticipantResult(
                participant = participant,
                startTime = participant.startTime,
                finishTime = 0L,
                totalTime = 0L,
                result = CheckResult(status = ResultStatus.DSQ, message = message),
                rawSplits = splits,
                expectedCpNumbers = expectedCpNumbers,
                expectedControlPoints = expected,
                startControlPoint = startControlPoint,
                readAt = readAt,
                replaceExisting = replaceExisting
            )
            return
        }

        val result = computeCheckResult(
            groupId = participant.groupId,
            startTime = startTime,
            expected = expected,
            actual = splits
        )
        val lastValidPunch = result.validSplits.lastOrNull() ?: splits.last()
        val finishTime = lastValidPunch.timestamp
        val totalTime = (finishTime - startTime) / 1000L
        createParticipantResult(
            participant = participant,
            startTime = startTime,
            finishTime = finishTime,
            totalTime = totalTime,
            result = result,
            rawSplits = splits,
            expectedCpNumbers = expectedCpNumbers,
            expectedControlPoints = expected,
            startControlPoint = startControlPoint,
            readAt = readAt,
            replaceExisting = replaceExisting
        )
    }

    private suspend fun createParticipantResult(
        participant: OrienteeringParticipant,
        startTime: Long,
        finishTime: Long,
        totalTime: Long,
        result: CheckResult,
        rawSplits: List<SplitTime>,
        expectedCpNumbers: List<Int> = emptyList(),
        expectedControlPoints: List<ControlPoint> = emptyList(),
        startControlPoint: Int? = null,
        readAt: Long? = null,
        replaceExisting: Boolean = false
    ) {
        val effectiveStatus = applyControlTime(participant.groupId, totalTime, result.status)
        val startPoint = getDistance(participant.groupId)?.startPoint()
        // Причину показываем организатору на экране: результат с временем, но без места
        // иначе выглядит сбоем.
        val statusText = result.message
            ?: "Превышено контрольное время".takeIf { effectiveStatus == ResultStatus.OVERTIME }
        val newResult = OrienteeringResult(
            competitionId = participant.competitionId,
            participantId = participant.id,
            groupId = participant.groupId,
            startTime = startTime,
            finishTime = finishTime,
            totalTime = totalTime,
            rank = -1,
            status = effectiveStatus,
            penaltyTime = 0,
            totalScore = result.totalScore,
            scorePenalty = result.scorePenalty,
            // DSQ: сохраняем все отметки с дистанции (для разбора), но без сделанных до старта.
            splits = if (result.status == ResultStatus.DSQ) punchesAfterStart(rawSplits, startTime) else result.validSplits,
            readAt = readAt
        )

        // При старте по стартовой станции реальное время старта узнаётся только сейчас, из чипа —
        // сохраняем его в участника, чтобы протокол/список участников тоже показывали факт, а не 0L.
        // resolvedParticipant (а не исходный participant с устаревшим startTime) кладём в state —
        // иначе экран сканирования при первом же скане показывает пустое/старое время старта и
        // считает сплиты от него, выдавая гигантские значения; они «чинились» только со второго
        // скана этого же чипа, когда getParticipantByChipNumber уже отдавал обновлённого участника.
        val resolvedParticipant = if (!stateValue.isCompetitionFinished &&
            stateValue.startTimeMode == StartTimeMode.BY_START_STATION &&
            startTime != participant.startTime
        ) {
            orienteeringCompetitionInteractor.updateParticipantLocally(participant.copy(startTime = startTime))
            participant.copy(startTime = startTime)
        } else {
            participant
        }

        if (stateValue.isCompetitionFinished) {
            // Соревнование завершено — режим «только просмотр»: показываем данные,
            // но не сохраняем результат и не влияем на итоговые протоколы.
            updateState {
                copy(
                    participant = resolvedParticipant,
                    participantResult = newResult,
                    rawSplits = rawSplits,
                    expectedCpNumbers = expectedCpNumbers,
                    expectedControlPoints = expectedControlPoints,
                    isPendingSave = false,
                    statusMessage = statusText,
                    startControlPoint = startControlPoint,
                    startPoint = startPoint,
                )
            }
            return
        }

        if (replaceExisting) {
            // Смена группы: сохранённый результат относится к прежней группе — заменяем его сразу,
            // при любом статусе, иначе в прежней группе остался бы чужой результат.
            val existing = orienteeringCompetitionInteractor.getResultByParticipantId(participant.id)
            if (existing != null) {
                orienteeringCompetitionInteractor.applyConflictResult(existing, newResult)
                orienteeringCompetitionInteractor.recalculateGroupRanks(existing.competitionId, existing.groupId)
                updateState {
                    copy(
                        participant = resolvedParticipant,
                        participantResult = newResult,
                        rawSplits = rawSplits,
                        expectedCpNumbers = expectedCpNumbers,
                        expectedControlPoints = expectedControlPoints,
                        isPendingSave = false,
                        statusMessage = statusText,
                        startControlPoint = startControlPoint,
                        startPoint = startPoint,
                    )
                }
                refreshGroupRank(resolvedParticipant)
                return
            }
        }

        if (newResult.status == ResultStatus.DSQ) {
            // DSQ: показываем результат организатору и ждём явного сохранения
            updateState {
                copy(
                    participant = resolvedParticipant,
                    participantResult = newResult,
                    rawSplits = rawSplits,
                    expectedCpNumbers = expectedCpNumbers,
                    expectedControlPoints = expectedControlPoints,
                    isPendingSave = true,
                    statusMessage = statusText,
                    startControlPoint = startControlPoint,
                    startPoint = startPoint,
                )
            }
            return
        }

        updateState {
            copy(
                participant = resolvedParticipant,
                participantResult = newResult,
                rawSplits = rawSplits,
                expectedCpNumbers = expectedCpNumbers,
                expectedControlPoints = expectedControlPoints,
                statusMessage = statusText,
                startControlPoint = startControlPoint,
                startPoint = startPoint,
            )
        }

        val existing = orienteeringCompetitionInteractor.getResultByParticipantId(participant.id)

        if (existing != null && existing.status == ResultStatus.REGISTERED) {
            // «Зарегистрирован» — результата ещё нет (например, чип отвязали): терять нечего.
            orienteeringCompetitionInteractor.applyConflictResult(existing, newResult)
            refreshGroupRank(participant)
        } else if (existing != null) {
            // Уже есть запись — эмитим конфликт для отображения диалога в MainActivity
            resultConflictRepository.emit(
                ResultConflictEvent(
                    participantName = "${participant.lastName} ${participant.firstName}",
                    existingResult = existing,
                    newResult = newResult,
                    onApply = {
                        orienteeringCompetitionInteractor.applyConflictResult(existing, newResult)
                    }
                )
            )
        } else {
            orienteeringCompetitionInteractor.saveParticipantResult(newResult)
            refreshGroupRank(participant)
        }
    }

    /** Пересчитывает результат после «Засчитать» без записи в БД. */
    private suspend fun recalculatePending() {
        val participant = stateValue.participant ?: return
        val rawSplits = stateValue.rawSplits ?: return
        if (rawSplits.isEmpty()) return
        val expected = getExpectedControlPoints(participant.groupId)
        val startTime = resolveStartTime(participant, rawSplits)
        // Причина DSQ после пересчёта: без обновления на экране оставалась бы причина первого
        // скана («Пропущен КП N») даже после того, как этот КП засчитан.
        var statusText: String? = null
        val newResult = if (startTime == null) {
            statusText = if (stateValue.startControlPoint != null) {
                "Нет отметки на стартовой станции"
            } else {
                "У дистанции не задано стартовое КП — укажите его в редактировании дистанции"
            }
            OrienteeringResult(
                competitionId = participant.competitionId,
                participantId = participant.id,
                groupId = participant.groupId,
                startTime = participant.startTime,
                finishTime = null,
                totalTime = null,
                rank = -1,
                status = ResultStatus.DSQ,
                penaltyTime = 0,
                splits = rawSplits,
                isEdited = true,
                readAt = stateValue.participantResult?.readAt
            )
        } else {
            val checkResult = computeCheckResult(participant.groupId, startTime, expected, rawSplits)
            val lastValidPunch = checkResult.validSplits.lastOrNull() ?: rawSplits.last()
            val finishTime = lastValidPunch.timestamp
            val totalTime = (finishTime - startTime) / 1000L
            val effectiveStatus = applyControlTime(participant.groupId, totalTime, checkResult.status)
            statusText = checkResult.message
                ?: "Превышено контрольное время".takeIf { effectiveStatus == ResultStatus.OVERTIME }
            OrienteeringResult(
                competitionId = participant.competitionId,
                participantId = participant.id,
                groupId = participant.groupId,
                startTime = startTime,
                finishTime = finishTime,
                totalTime = totalTime,
                rank = -1,
                status = effectiveStatus,
                penaltyTime = 0,
                totalScore = checkResult.totalScore,
                scorePenalty = checkResult.scorePenalty,
                splits = if (checkResult.status == ResultStatus.DSQ) punchesAfterStart(rawSplits, startTime) else checkResult.validSplits,
                isEdited = true,
                readAt = stateValue.participantResult?.readAt,
            )
        }
        updateState { copy(participantResult = newResult, statusMessage = statusText) }
    }

    /** Явное сохранение результата организатором (вызывается по кнопке «Сохранить результат»). */
    private suspend fun saveResultFromPending() {
        val participant = stateValue.participant ?: return
        val newResult = stateValue.participantResult ?: return
        val resultStartTime = newResult.startTime
        if (stateValue.startTimeMode == StartTimeMode.BY_START_STATION &&
            resultStartTime != null && resultStartTime != participant.startTime
        ) {
            orienteeringCompetitionInteractor.updateParticipantLocally(participant.copy(startTime = resultStartTime))
        }
        val existing = orienteeringCompetitionInteractor.getResultByParticipantId(participant.id)
        if (existing != null && existing.status == ResultStatus.REGISTERED) {
            orienteeringCompetitionInteractor.applyConflictResult(existing, newResult)
            refreshGroupRank(participant)
        } else if (existing != null) {
            resultConflictRepository.emit(
                ResultConflictEvent(
                    participantName = "${participant.lastName} ${participant.firstName}",
                    existingResult = existing,
                    newResult = newResult,
                    onApply = {
                        orienteeringCompetitionInteractor.applyConflictResult(existing, newResult)
                    }
                )
            )
        } else {
            orienteeringCompetitionInteractor.saveParticipantResult(newResult)
            refreshGroupRank(participant)
        }
        updateState { copy(isPendingSave = false) }
    }

    /**
     * Приводит статус к контрольному времени: КВ группы (или соревнования, если у группы своего
     * нет) и политика соревнования. Та же логика, что на сервере — см. applyOvertimePolicy.
     */
    private suspend fun applyControlTime(
        groupId: Long,
        totalTimeSeconds: Long?,
        status: ResultStatus
    ): ResultStatus {
        val group = orienteeringCompetitionInteractor.getParticipantGroup(groupId).getOrNull()
        return applyOvertimePolicy(
            status = status,
            totalTimeSeconds = totalTimeSeconds,
            controlTimeMinutes = effectiveControlTimeMinutes(
                groupLimitMinutes = group?.timeLimitMinutes,
                competitionControlTimeMinutes = stateValue.competitionControlTimeMinutes
            ),
            policy = stateValue.overtimePolicy
        )
    }

    /** Выбирает алгоритм проверки отметок в зависимости от формата соревнования. */
    private suspend fun computeCheckResult(
        groupId: Long,
        startTime: Long,
        expected: List<ControlPoint>,
        actual: List<SplitTime>
    ): CheckResult {
        if (expected.isEmpty() && stateValue.isFinishRegistration) {
            return finishOnlyCheck(actual, startTime)
        }
        if (stateValue.competitionDirection != OrienteeringDirection.BY_CHOICE) {
            return checkControlPointOrderPro(expected, punchesAfterStart(actual, startTime))
        }
        if (stateValue.byChoiceMode == ByChoiceMode.MIN_CONTROLS) {
            return computeMinControlsResult(expected, actual, startTime, getDistance(groupId)?.minControlsCount)
        }
        val group = orienteeringCompetitionInteractor.getParticipantGroup(groupId).getOrNull()
            ?: return CheckResult(ResultStatus.DSQ, "Группа участника не найдена")
        return computeByChoiceResult(expected, actual, startTime, group)
    }

    fun checkControlPointOrderPro(
        expected: List<ControlPoint>,
        actual: List<SplitTime>
    ): CheckResult {

        if (expected.isEmpty()) {
            return CheckResult(ResultStatus.DSQ, "Для группы не заданы КП")
        }

        if (actual.isEmpty()) {
            return CheckResult(ResultStatus.DSQ, "В чипе нет отметок")
        }

        val expectedNumbers = expected.map { it.number }

        var searchIndex = 0
        val validSplits = mutableListOf<SplitTime>()

        for (expectedCp in expectedNumbers) {

            var found = false

            for (i in searchIndex until actual.size) {
                val punch = actual[i]

                if (punch.controlPoint == expectedCp) {
                    validSplits.add(punch)
                    searchIndex = i + 1
                    found = true
                    break
                }
            }

            if (!found) {
                return CheckResult(
                    ResultStatus.DSQ,
                    "Пропущен КП $expectedCp"
                )
            }
        }

        return CheckResult(
            status = ResultStatus.FINISHED,
            validSplits = validSplits
        )
    }


    // --- Режим «Регистрация на финише» ---

    private fun finishOnlyCheck(actual: List<SplitTime>, startTime: Long): CheckResult =
        computeFinishOnlyResult(actual, startTime, stateValue.defaultFinishStationCode)

    /** Незнакомый чип: считывание — в очередь, сразу открывается карточка участника. */
    private suspend fun queueUnknownRead(chipData: ReadChipData.RawResult, readAt: Long) {
        val cid = competitionId ?: return
        val read = UnassignedChipRead(
            competitionId = cid,
            chipNumber = chipData.chipNumber,
            readAt = readAt,
            clearTime = chipData.clearTime,
            splits = chipData.splits
        )
        unassignedChipReadRepository.save(read)
        updateState {
            copy(
                participant = null,
                participantResult = null,
                rawSplits = null,
                isPendingSave = false,
                statusMessage = null,
                // Незнакомый чип в этом режиме — норма, а не ошибка: открывается карточка участника,
                // а в очереди его видно по плашке «Неразобранные считывания». Красное сообщение не нужно.
                scanNotice = null
            )
        }
        openAssign(read, AnalyticsEvent.FinishReadSource.ON_READ)
    }

    /** Открывает карточку участника для [read]: подгружает группы и участников без результата. */
    private suspend fun openAssign(read: UnassignedChipRead, source: AnalyticsEvent.FinishReadSource) {
        val cid = competitionId ?: return
        val groups = loadAssignGroups(cid)
        val withResult = orienteeringCompetitionInteractor.getResultsByGroups(cid).getOrNull().orEmpty()
            .flatMap { it.participants }
            // «Зарегистрирован» — результата нет (например, чип отвязали), к такому можно привязать.
            .filter { it.result != null && it.result?.status != ResultStatus.REGISTERED }
            .map { it.participant.id }
            .toSet()
        val candidates = orienteeringCompetitionInteractor.getParticipants(cid).getOrNull().orEmpty()
            .filter { !it.isDeleted && it.id !in withResult }
            .sortedWith(compareBy({ it.lastName }, { it.firstName }))
        val distances = orienteeringCompetitionInteractor.getDistances(cid).getOrNull().orEmpty()
        updateState {
            copy(
                assigningRead = read,
                assignSource = source,
                assignGroups = groups,
                assignCandidates = candidates,
                assignMatchingGroupIds = matchingGroupIdsByPunches(read.splits, groups, distances),
                createdGroupId = null
            )
        }
    }

    /** Подходящие по отметкам группы для каждого неразобранного считывания — подсказка в списке. */
    private suspend fun matchingGroupTitles(competitionId: String, reads: List<UnassignedChipRead>): Map<Int, List<String>> {
        if (reads.isEmpty()) return emptyMap()
        val groups = loadAssignGroups(competitionId)
        val distances = orienteeringCompetitionInteractor.getDistances(competitionId).getOrNull().orEmpty()
        return reads.associate { read ->
            val ids = matchingGroupIdsByPunches(read.splits, groups, distances)
            read.chipNumber to groups.filter { it.groupId in ids }.map { it.title }
        }
    }

    private suspend fun loadAssignGroups(competitionId: String): List<ParticipantGroup> =
        orienteeringCompetitionInteractor.getCompetitionWithDetails(competitionId).getOrNull()
            ?.groupsWithParticipants
            ?.map { it.group }
            ?.filter { !it.isDeleted }
            .orEmpty()

    /** Создаёт участника по считыванию: номер — следующий свободный, чип — считанный. */
    private suspend fun createParticipantFromRead(action: OrientReadCardAction.CreateParticipantFromRead) {
        val cid = competitionId ?: return
        val read = stateValue.assigningRead ?: return
        val group = stateValue.assignGroups.firstOrNull { it.groupId == action.groupId } ?: return
        val maxNumber = orienteeringCompetitionInteractor.getParticipants(cid).getOrNull().orEmpty()
            .mapNotNull { it.startNumber.toIntOrNull() }
            .maxOrNull() ?: 0
        val participant = OrienteeringParticipant(
            id = UUID.randomUUID().toString(),
            userId = "",
            firstName = action.firstName.trim(),
            lastName = action.lastName.trim(),
            groupId = group.groupId,
            groupName = group.title,
            competitionId = cid,
            commandName = normalizeCommandName(action.commandName).orEmpty(),
            startNumber = (maxNumber + 1).toString(),
            // Время старта участник получит по отметке на стартовой станции.
            startTime = 0L,
            chipNumber = read.chipNumber.toString(),
            comment = "",
            isChipGiven = true,
            isOutOfCompetition = action.isOutOfCompetition
        )
        val saved = orienteeringCompetitionInteractor.saveParticipant(participant)
        if (saved == null) {
            updateState { copy(scanNotice = "Не удалось сохранить участника — попробуйте ещё раз") }
            return
        }
        analytics.trackEvent(
            AnalyticsEvent.ParticipantAdded(
                if (action.fromHistory) AnalyticsEvent.ParticipantAddMethod.HISTORY
                else AnalyticsEvent.ParticipantAddMethod.NFC,
                outOfCompetition = action.isOutOfCompetition
            )
        )
        finishAssignment(saved, read, AnalyticsEvent.FinishReadTarget.NEW_PARTICIPANT)
    }

    /** Привязывает считывание к уже заведённому участнику (например, заявленному заранее без чипа). */
    private suspend fun assignReadToParticipant(participantId: String) {
        val read = stateValue.assigningRead ?: return
        val candidate = stateValue.assignCandidates.firstOrNull { it.id == participantId } ?: return
        val updated = candidate.copy(chipNumber = read.chipNumber.toString(), isChipGiven = true)
        orienteeringCompetitionInteractor.updateParticipantLocally(updated).onSuccess {
            orienteeringCompetitionInteractor.syncParticipantWithServer(updated)
            finishAssignment(updated, read, AnalyticsEvent.FinishReadTarget.EXISTING_PARTICIPANT)
        }.onFailure {
            updateState { copy(scanNotice = "Не удалось привязать чип — попробуйте ещё раз") }
        }
    }

    /** Убирает считывание из очереди и считает результат участника по его отметкам. */
    private suspend fun finishAssignment(
        participant: OrienteeringParticipant,
        read: UnassignedChipRead,
        target: AnalyticsEvent.FinishReadTarget
    ) {
        unassignedChipReadRepository.delete(read.competitionId, read.chipNumber)
        currentRead = read
        analytics.trackEvent(AnalyticsEvent.FinishReadAssigned(target, stateValue.assignSource))
        updateState { copy(assigningRead = null, createdGroupId = null, scanNotice = null) }
        val rawResult = read.toRawResult()
        if (rawResult.splits.isEmpty()) {
            updateState {
                copy(
                    participant = participant,
                    participantResult = null,
                    rawSplits = emptyList(),
                    scanNotice = "На чипе №${read.chipNumber} нет отметок"
                )
            }
            return
        }
        computeParticipantResult(participant = participant, rawResult = rawResult, readAt = read.readAt)
    }

    /**
     * «Изменить участника» на экране считывания: ФИО, команда, группа. При смене группы результат
     * пересчитывается по дистанции новой группы из исходного считывания и сразу заменяет
     * сохранённый (места пересчитываются в обеих группах).
     */
    private suspend fun updateParticipantFromRead(action: OrientReadCardAction.UpdateParticipantFromRead) {
        val participant = stateValue.participant ?: return
        val group = stateValue.assignGroups.firstOrNull { it.groupId == action.groupId } ?: return
        val updated = participant.copy(
            lastName = action.lastName.trim(),
            firstName = action.firstName.trim(),
            commandName = normalizeCommandName(action.commandName).orEmpty(),
            groupId = group.groupId,
            groupName = group.title,
            isOutOfCompetition = action.isOutOfCompetition
        )
        val groupChanged = updated.groupId != participant.groupId
        val outOfCompetitionChanged = updated.isOutOfCompetition != participant.isOutOfCompetition
        orienteeringCompetitionInteractor.updateParticipantLocally(updated).onFailure {
            updateState { copy(scanNotice = "Не удалось сохранить участника — попробуйте ещё раз") }
            return
        }
        analytics.trackEvent(AnalyticsEvent.FinishParticipantEdited(groupChanged = groupChanged))
        if (outOfCompetitionChanged) {
            analytics.trackEvent(
                AnalyticsEvent.ParticipantOutOfCompetitionChanged(
                    enabled = updated.isOutOfCompetition,
                    source = AnalyticsEvent.OutOfCompetitionSource.FINISH_READ
                )
            )
        }
        updateState { copy(isEditParticipantShown = false, createdGroupId = null, participant = updated) }
        if (!groupChanged) {
            // Места группы пересчитаны при сохранении — показываем результат с новым местом / «ВК».
            if (outOfCompetitionChanged) {
                orienteeringCompetitionInteractor.getResultByParticipantId(updated.id)?.let { result ->
                    updateState { copy(participantResult = result) }
                }
            }
            return
        }
        val raw = currentRead?.toRawResult()
            ?: stateValue.rawSplits?.let { ReadChipData.RawResult(chipNumber = updated.chipNumber.toIntOrNull() ?: 0, clearTime = 0L, splits = it) }
            ?: return
        val readAt = currentRead?.readAt ?: stateValue.participantResult?.readAt
        computeParticipantResult(participant = updated, rawResult = raw, readAt = readAt, replaceExisting = true)
    }

    /**
     * «Отвязать чип»: считывание возвращается в очередь неразобранных и сразу открывается его
     * карточка. Участник либо удаляется ([deleteParticipant] — внесён на финише по ошибке), либо
     * остаётся без чипа и результата (заявлен заранее, чип считан не ему).
     */
    private suspend fun unlinkChip(deleteParticipant: Boolean) {
        val cid = competitionId ?: return
        val participant = stateValue.participant ?: return
        val chipNumber = participant.chipNumber.toIntOrNull()
        val read = currentRead?.takeIf { it.chipNumber == chipNumber }
            ?: chipNumber?.let {
                UnassignedChipRead(
                    competitionId = cid,
                    chipNumber = it,
                    readAt = stateValue.participantResult?.readAt ?: System.currentTimeMillis(),
                    clearTime = 0L,
                    splits = stateValue.rawSplits.orEmpty()
                )
            }
        if (deleteParticipant) {
            orienteeringCompetitionInteractor.deleteParticipantAtFinish(participant).onFailure { e ->
                updateState { copy(scanNotice = e.message ?: "Не удалось удалить участника") }
                return
            }
        } else {
            orienteeringCompetitionInteractor.getResultByParticipantId(participant.id)?.let {
                orienteeringCompetitionInteractor.clearParticipantResult(it)
            }
            val unlinked = participant.copy(chipNumber = "", isChipGiven = false, startTime = 0L)
            orienteeringCompetitionInteractor.updateParticipantLocally(unlinked).onFailure {
                updateState { copy(scanNotice = "Не удалось отвязать чип — попробуйте ещё раз") }
                return
            }
        }
        analytics.trackEvent(AnalyticsEvent.FinishChipUnlinked(participantDeleted = deleteParticipant))
        currentRead = null
        updateState {
            copy(
                participant = null,
                participantResult = null,
                rawSplits = null,
                isPendingSave = false,
                statusMessage = null,
                groupRank = null,
                scanNotice = null
            )
        }
        if (read != null) {
            unassignedChipReadRepository.save(read)
            openAssign(read, AnalyticsEvent.FinishReadSource.QUEUE)
        }
    }

    /** Новая группа прямо из карточки участника (без дистанции — результат по времени). */
    private suspend fun createGroupForRead(title: String) {
        val cid = competitionId ?: return
        val trimmed = title.trim().takeIf { it.isNotEmpty() } ?: return
        val existing = stateValue.assignGroups.firstOrNull { it.title.equals(trimmed, ignoreCase = true) }
        if (existing != null) {
            updateState { copy(createdGroupId = existing.groupId) }
            return
        }
        orienteeringCompetitionInteractor.localSaveParticipantGroups(
            listOf(
                ParticipantGroup(
                    groupId = 0L,
                    competitionId = cid,
                    title = trimmed,
                    gender = null,
                    distanceId = NO_DISTANCE_ID
                )
            )
        )
        val groups = loadAssignGroups(cid)
        updateState {
            copy(
                assignGroups = groups,
                createdGroupId = groups.firstOrNull { it.title == trimmed }?.groupId
            )
        }
    }

    /**
     * Ручной статус организатора: «Снят» (DSQ) или «Засчитать» (FINISHED — время от старта до
     * отметки финишной станции). Ручной статус не перетирается автоматическим пересчётом КВ.
     */
    private suspend fun setManualStatus(finished: Boolean) {
        val participant = stateValue.participant ?: return
        val current = stateValue.participantResult ?: return
        val updated = if (finished) {
            val start = current.startTime?.takeIf { it > 0L } ?: return
            val finish = stateValue.finishPunch ?: return
            current.copy(
                status = ResultStatus.FINISHED,
                finishTime = finish.timestamp,
                totalTime = (finish.timestamp - start) / 1000L,
                splits = punchesAfterStart(stateValue.rawSplits.orEmpty(), start),
                rank = -1,
                isStatusManual = true,
                isEdited = true
            )
        } else {
            current.copy(status = ResultStatus.DSQ, rank = -1, isStatusManual = true, isEdited = true)
        }
        analytics.trackEvent(
            AnalyticsEvent.ResultStatusChangedManually(
                if (finished) AnalyticsEvent.ManualResultStatus.FINISHED else AnalyticsEvent.ManualResultStatus.DSQ
            )
        )
        val existing = orienteeringCompetitionInteractor.getResultByParticipantId(participant.id)
        if (existing != null) {
            orienteeringCompetitionInteractor.applyConflictResult(existing, updated)
        } else {
            orienteeringCompetitionInteractor.saveParticipantResult(updated)
        }
        updateState {
            copy(
                participantResult = updated,
                isPendingSave = false,
                statusMessage = if (finished) null else "Снят организатором"
            )
        }
        refreshGroupRank(participant)
    }

    /** Люди из прошлых соревнований — подсказки ФИО в карточке (сначала локально, затем с сервера). */
    private fun loadParticipantHistory(competitionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val local = localRepository.getParticipantHistory(competitionId).getOrDefault(emptyList())
            updateState { copy(participantHistory = ParticipantHistory.aggregate(local)) }
            orienteeringCompetitionInteractor.getServerParticipantHistory(competitionId).onSuccess { server ->
                updateState { copy(participantHistory = ParticipantHistory.aggregate(ParticipantHistory.merge(local, server))) }
            }
        }
    }
}

/**
 * Проверка отметок для формата «по выбору» (score-О): порядок взятия КП не важен,
 * повторные отметки одного КП засчитываются один раз, обязательные (REQUIRED) КП должны
 * быть взяты все — иначе DSQ. Результат — сумма баллов за минусом штрафа за опоздание
 * сверх лимита времени группы; при превышении порога сильного опоздания баллы обнуляются.
 *
 * Вынесена в top-level функцию (а не метод [OrientReadCardViewModel]), т.к. не использует
 * состояние ViewModel — это упрощает unit-тестирование без мокирования зависимостей.
 */
fun computeByChoiceResult(
    expected: List<ControlPoint>,
    actual: List<SplitTime>,
    startTime: Long,
    group: ParticipantGroup
): CheckResult {
    if (expected.isEmpty()) {
        return CheckResult(ResultStatus.DSQ, "Для группы не заданы КП")
    }
    if (actual.isEmpty()) {
        return CheckResult(ResultStatus.DSQ, "В чипе нет отметок")
    }

    val expectedNumbers = expected.map { it.number }.toSet()
    // Отметки до старта отбрасываем до дедупликации: иначе отметка финишной (или любой) станции в
    // стартовом городке оказалась бы «первой» отметкой этого КП и вытеснила настоящую.
    val dedupedSplits = punchesAfterStart(actual, startTime)
        .sortedBy { it.timestamp }
        .distinctBy { it.controlPoint }
        .filter { it.controlPoint in expectedNumbers }

    val requiredNumbers = expected.filter { it.role == ControlPointRole.REQUIRED }.map { it.number }
    val takenNumbers = dedupedSplits.map { it.controlPoint }.toSet()
    val missingRequired = requiredNumbers.firstOrNull { it !in takenNumbers }
    if (missingRequired != null) {
        return CheckResult(ResultStatus.DSQ, "Пропущен обязательный КП $missingRequired")
    }

    val scoreByNumber = expected.associate { it.number to it.score }
    val rawScore = dedupedSplits.sumOf { scoreByNumber[it.controlPoint] ?: 0 }

    val lastPunch = dedupedSplits.lastOrNull() ?: actual.last()
    val totalTimeSeconds = (lastPunch.timestamp - startTime) / 1000L

    val timeLimitMinutes = group.timeLimitMinutes
    var scorePenalty = 0
    var finalScore = rawScore

    if (timeLimitMinutes != null) {
        val lateSeconds = totalTimeSeconds - timeLimitMinutes * 60L
        val lateMinutes = if (lateSeconds > 0) ceil(lateSeconds / 60.0).toInt() else 0

        if (lateMinutes > 0) {
            scorePenalty = lateMinutes * (group.scorePenaltyPerMinute ?: 0)
            finalScore = (rawScore - scorePenalty).coerceAtLeast(0)
        }

        val maxLateness = group.maxLatenessMinutes
        if (maxLateness != null && lateMinutes > maxLateness) {
            finalScore = 0
        }
    }

    return CheckResult(
        status = ResultStatus.FINISHED,
        validSplits = dedupedSplits,
        totalScore = finalScore,
        scorePenalty = scorePenalty
    )
}

/**
 * Проверка отметок для формата «по выбору» с минимумом КП ([ByChoiceMode.MIN_CONTROLS]): порядок
 * взятия КП не важен, повторные отметки одного КП засчитываются один раз, отметки до старта и
 * чужих КП отбрасываются. Нужно взять все обязательные ([ControlPointRole.REQUIRED]) КП и не меньше
 * [minControlsCount] КП дистанции (null — все); финишная станция в это число не входит. Иначе — DSQ
 * с причиной. Баллов нет: [CheckResult.totalScore] остаётся null, места считаются по времени.
 *
 * @param expected КП дистанции плюс финишный пункт ([com.competra.domain.models.orienteering.expectedSequence]).
 */
fun computeMinControlsResult(
    expected: List<ControlPoint>,
    actual: List<SplitTime>,
    startTime: Long,
    minControlsCount: Int?
): CheckResult {
    val controls = expected.filter { it.role != ControlPointRole.FINISH }
    if (controls.isEmpty()) {
        return CheckResult(ResultStatus.DSQ, "Для группы не заданы КП")
    }
    if (actual.isEmpty()) {
        return CheckResult(ResultStatus.DSQ, "В чипе нет отметок")
    }

    val expectedNumbers = expected.map { it.number }.toSet()
    // Как и в computeByChoiceResult: отметки до старта отбрасываем до дедупликации, иначе отметка
    // в стартовом городке вытеснила бы настоящую отметку этого КП.
    val dedupedSplits = punchesAfterStart(actual, startTime)
        .sortedBy { it.timestamp }
        .distinctBy { it.controlPoint }
        .filter { it.controlPoint in expectedNumbers }

    val controlNumbers = controls.map { it.number }.toSet()
    val takenNumbers = dedupedSplits.map { it.controlPoint }.filter { it in controlNumbers }.toSet()

    val missingRequired = controls
        .filter { it.role == ControlPointRole.REQUIRED }
        .firstOrNull { it.number !in takenNumbers }
    if (missingRequired != null) {
        return CheckResult(ResultStatus.DSQ, "Пропущен обязательный КП ${missingRequired.number}")
    }

    val needed = minControlsCount?.takeIf { it > 0 }?.coerceAtMost(controlNumbers.size) ?: controlNumbers.size
    if (takenNumbers.size < needed) {
        return CheckResult(ResultStatus.DSQ, "Взято ${takenNumbers.size} из $needed КП")
    }

    return CheckResult(status = ResultStatus.FINISHED, validSplits = dedupedSplits)
}

/**
 * Вставляет «засчитанную» организатором отметку пропущенного КП в сырые сплиты чипа.
 *
 * Отметка ставится сразу после отметки предыдущего по дистанции КП и получает её время +1 мс
 * (либо время старта +1 мс, если пропущен первый КП). Позиция ищется тем же последовательным проходом,
 * что и в [OrientReadCardViewModel.checkControlPointOrderPro], поэтому засчитанный КП гарантированно
 * встаёт в проверяемую последовательность на своё место. Порядок засчитывания нескольких пропущенных
 * КП не важен: уже засчитанные учитываются при поиске позиции как обычные отметки.
 *
 * Раньше время бралось у ближайшей строки выше в таблице сплитов, а пропущенные КП выводятся в конце
 * таблицы — засчитанный КП получал время финиша, оказывался после финишной отметки, и проверка порядка
 * всё равно давала DSQ.
 *
 * Отметки со стартовой станции ([startControlPoint]) в проверяемую последовательность не входят —
 * засчитанный первый КП ставится после неё, а не перед.
 *
 * Время строго больше предыдущей отметки, а при совпадении со следующими (несколько засчитанных
 * подряд) они сдвигаются на 1 мс: сервер и локальная БД упорядочивают сплиты только по timestamp,
 * и при равных значениях засчитанный КП после синхронизации мог бы встать перед предыдущим.
 *
 * @param expectedCpNumbers Номера КП дистанции по порядку (включая финишный).
 * @param distanceOrdinal Порядковый номер засчитываемого КП по дистанции (1-based).
 * @param startTime Время старта участника — используется, если перед КП нет ни одной отметки.
 */
fun insertCreditedPunch(
    rawSplits: List<SplitTime>,
    expectedCpNumbers: List<Int>,
    cpNumber: Int,
    distanceOrdinal: Int,
    startTime: Long?,
    startControlPoint: Int? = null,
): List<SplitTime> {
    var searchIndex = startControlPoint
        ?.let { start -> rawSplits.indexOfFirst { it.controlPoint == start } + 1 }
        ?: 0
    for (expectedCp in expectedCpNumbers.take((distanceOrdinal - 1).coerceAtLeast(0))) {
        val found = (searchIndex until rawSplits.size).firstOrNull { rawSplits[it].controlPoint == expectedCp }
        if (found != null) searchIndex = found + 1
    }
    val prevTimestamp = rawSplits.getOrNull(searchIndex - 1)?.timestamp
        ?: startTime
        ?: rawSplits.firstOrNull()?.timestamp
        ?: 0L
    val result = rawSplits.toMutableList()
    result.add(searchIndex, SplitTime(controlPoint = cpNumber, timestamp = prevTimestamp + 1))
    for (i in searchIndex + 1 until result.size) {
        if (result[i].timestamp > result[i - 1].timestamp) break
        result[i] = result[i].copy(timestamp = result[i - 1].timestamp + 1)
    }
    return result
}


/**
 * Проверка без дистанции (режим «Регистрация на финише», группа без дистанции): КП не
 * проверяются, результат — время от старта до первой после старта отметки финишной станции
 * [finishStationCode]. Нет такой отметки — DSQ «Нет отметки финиша» (организатор может
 * засчитать финиш по времени считывания).
 */
fun computeFinishOnlyResult(actual: List<SplitTime>, startTime: Long, finishStationCode: Int?): CheckResult {
    val afterStart = punchesAfterStart(actual, startTime)
    val finish = finishStationCode?.let { code -> afterStart.firstOrNull { it.controlPoint == code && it.timestamp > startTime } }
        ?: return CheckResult(status = ResultStatus.DSQ, message = "Нет отметки финиша", validSplits = afterStart)
    return CheckResult(
        status = ResultStatus.FINISHED,
        validSplits = afterStart.filter { it.timestamp <= finish.timestamp }
    )
}
