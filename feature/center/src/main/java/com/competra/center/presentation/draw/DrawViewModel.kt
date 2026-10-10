package com.competra.center.presentation.draw

import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.center.data.draw.DrawAction
import com.competra.center.data.draw.DrawState
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.getArguments
import com.competra.domain.models.orienteering.DrawMode
import com.competra.domain.models.orienteering.DrawSettings
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.repository.LoadingRepository
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import com.competra.utils.constants.EventsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Минимальный допустимый timestamp — 1 января 2000 года. */
private const val MIN_VALID_TIMESTAMP_MS = 946_684_800_000L

/**
 * Вьюмодель жеребьевки участников соревнований.
 */
class DrawViewModel(
    private val interactor: OrienteeringCompetitionInteractor,
    private val navigation: Navigation,
    private val loadingRepository: LoadingRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<DrawState>(DrawState()) {

    val competitionId: String? = navigation.getArguments<String>(EventsConstants.EVENT_ID.name)

    override fun onAction(action: BaseAction) {
        when (action) {
            DrawAction.StartDrawOperation -> startDrawOperation()
            DrawAction.StartGroupDrawOperation -> startGroupDrawOperation()
            is DrawAction.StartDistanceDrawOperation ->
                startDistanceDrawOperation(corridors = action.corridors, gap = action.gap)
        }
    }

    /**
     * Общая жеребьевка: участники из всех групп перемешиваются вместе с чередованием групп.
     * Стартовые номера и времена назначаются глобально — каждый следующий участник
     * стартует на `startIntervalSeconds` позже предыдущего.
     */
    private fun startDrawOperation() {
        val compId = competitionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            loadingRepository.emit(true)
            try {
                val competition = interactor.getCompetition(compId) ?: return@launch
                val participants = interactor.getParticipants(competitionId = compId).getOrNull()
                    ?: return@launch
                val competitionStartTime = resolveStartTime(competition)
                val intervalMs = (competition.startIntervalSeconds?.toLong() ?: 60L) * 1000L
                val sortedParticipants = drawParticipants(
                    participants = participants.shuffled(),
                    punchingSystem = competition.punchingSystem,
                    competitionStartTime = competitionStartTime,
                    intervalMs = intervalMs
                )
                interactor.updateParticipants(sortedParticipants)
                interactor.syncParticipantsAfterDraw(sortedParticipants)
                interactor.setDrawConducted(compId, DrawSettings(DrawMode.GENERAL))
                analytics.trackEvent(
                    AnalyticsEvent.ParticipantDrawn(
                        participantsCount = sortedParticipants.size,
                        mode = AnalyticsEvent.DrawMode.GENERAL,
                    )
                )
                updateState { copy(participants = sortedParticipants) }
            } finally {
                loadingRepository.emit(false)
            }
        }
    }

    /**
     * Жеребьевка по группам: внутри каждой группы участники перемешиваются независимо.
     * Стартовое время отсчитывается от начала соревнования отдельно для каждой группы,
     * поэтому участники из разных групп могут стартовать в одно и то же время.
     * Стартовые номера уникальны глобально.
     */
    private fun startGroupDrawOperation() {
        val compId = competitionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            loadingRepository.emit(true)
            try {
                val competition = interactor.getCompetition(compId) ?: return@launch
                val participants = interactor.getParticipants(competitionId = compId).getOrNull()
                    ?: return@launch
                val competitionStartTime = resolveStartTime(competition)
                val intervalMs = (competition.startIntervalSeconds?.toLong() ?: 60L) * 1000L
                val sortedParticipants = drawParticipantsByGroups(
                    participants = participants,
                    punchingSystem = competition.punchingSystem,
                    competitionStartTime = competitionStartTime,
                    intervalMs = intervalMs
                )
                interactor.updateParticipants(sortedParticipants)
                interactor.syncParticipantsAfterDraw(sortedParticipants)
                interactor.setDrawConducted(compId, DrawSettings(DrawMode.GROUP))
                analytics.trackEvent(
                    AnalyticsEvent.ParticipantDrawn(
                        participantsCount = sortedParticipants.size,
                        mode = AnalyticsEvent.DrawMode.GROUP,
                    )
                )
                updateState { copy(participants = sortedParticipants) }
            } finally {
                loadingRepository.emit(false)
            }
        }
    }

    /**
     * Жеребьевка по дистанциям: участники с одинаковой дистанцией не стартуют
     * в одну и ту же минуту. На каждом стартовом слоте — не более [corridors] участников
     * и не более одного из каждой различной дистанции. Старты каждой дистанции разнесены
     * минимум на [gap] интервалов и равномерно растянуты по всему расписанию.
     * Стартовые номера уникальны глобально.
     */
    private fun startDistanceDrawOperation(corridors: Int, gap: Int) {
        val compId = competitionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            loadingRepository.emit(true)
            try {
                val competition = interactor.getCompetition(compId) ?: return@launch
                val participants = interactor.getParticipants(competitionId = compId).getOrNull()
                    ?: return@launch
                val details = interactor.getCompetitionWithDetails(compId).getOrNull()
                    ?: return@launch
                val groupDistanceMap: Map<Long, Long> = details.groupsWithParticipants
                    .associate { it.group.groupId to it.group.distanceId }
                val competitionStartTime = resolveStartTime(competition)
                val intervalMs = (competition.startIntervalSeconds?.toLong() ?: 60L) * 1000L
                val sortedParticipants = drawParticipantsByDistance(
                    participants = participants,
                    groupDistanceMap = groupDistanceMap,
                    punchingSystem = competition.punchingSystem,
                    competitionStartTime = competitionStartTime,
                    intervalMs = intervalMs,
                    corridors = corridors.coerceAtLeast(1),
                    gap = gap.coerceAtLeast(1)
                )
                interactor.updateParticipants(sortedParticipants)
                interactor.syncParticipantsAfterDraw(sortedParticipants)
                interactor.setDrawConducted(
                    compId,
                    DrawSettings(DrawMode.DISTANCE, corridors = corridors.coerceAtLeast(1), gap = gap.coerceAtLeast(1))
                )
                analytics.trackEvent(
                    AnalyticsEvent.ParticipantDrawn(
                        participantsCount = sortedParticipants.size,
                        mode = AnalyticsEvent.DrawMode.DISTANCE,
                        corridors = corridors,
                        gap = gap,
                    )
                )
                updateState { copy(participants = sortedParticipants) }
            } finally {
                loadingRepository.emit(false)
            }
        }
    }

    /**
     * Определяет базовое время старта соревнования для жеребьевки.
     *
     * Приоритет:
     * 1. [OrienteeringCompetition.startTime] — фактическое время старта, если задано и валидно.
     * 2. [Competition.startDate] — дата соревнования, если валидна.
     * 3. Текущее время — крайний запасной вариант.
     */
    private fun resolveStartTime(competition: com.competra.domain.models.orienteering.OrienteeringCompetition): Long {
        return competition.startTime?.takeIf { it >= MIN_VALID_TIMESTAMP_MS }
            ?: competition.competition.startDate.takeIf { it >= MIN_VALID_TIMESTAMP_MS }
            ?: System.currentTimeMillis()
    }

    /**
     * Жеребьевка по группам: внутри каждой группы участники перемешиваются независимо.
     * Стартовое время каждого участника — [competitionStartTime] + позиция_в_группе * intervalMs.
     * Участники из разных групп с одинаковой позицией получают одинаковое стартовое время.
     * Стартовые номера назначаются глобально (уникальны по всем группам).
     */
    private fun drawParticipantsByGroups(
        participants: List<OrienteeringParticipant>,
        punchingSystem: PunchingSystem?,
        competitionStartTime: Long,
        intervalMs: Long
    ): List<OrienteeringParticipant> {
        if (participants.isEmpty()) return emptyList()

        val result = mutableListOf<OrienteeringParticipant>()
        var globalNumber = 1

        participants
            .groupBy { it.groupId }
            .values
            .forEach { groupParticipants ->
                groupParticipants.shuffled().forEachIndexed { indexInGroup, participant ->
                    val number = globalNumber.toString()
                    val startTime = competitionStartTime + indexInGroup * intervalMs
                    result.add(
                        participant.copy(
                            startNumber = number,
                            startTime = startTime,
                            chipNumber = if (punchingSystem == PunchingSystem.SPORTIDUINO) number else participant.chipNumber
                        )
                    )
                    globalNumber++
                }
            }

        return result
    }

    /**
     * Общая жеребьевка: участники перемешиваются с чередованием групп,
     * чтобы подряд не стартовали участники одной группы.
     * Стартовое время каждого участника — [competitionStartTime] + глобальная_позиция * intervalMs.
     */
    private fun drawParticipants(
        participants: List<OrienteeringParticipant>,
        punchingSystem: PunchingSystem?,
        competitionStartTime: Long,
        intervalMs: Long
    ): List<OrienteeringParticipant> {
        if (participants.isEmpty()) return emptyList()

        // Группируем по groupId для чередования
        val groups = participants
            .groupBy { it.groupId }
            .mapValues { it.value.toMutableList() }
            .toMutableMap()

        val mixedResult = mutableListOf<OrienteeringParticipant>()
        var lastGroupId: Long? = null

        // Перемешиваем с чередованием групп
        while (groups.isNotEmpty()) {
            val availableGroups = groups
                .filterKeys { it != lastGroupId }
                .ifEmpty { groups }

            val selectedGroupId = availableGroups.keys.random()
            val groupList = groups[selectedGroupId]!!

            val participant = groupList.random()
            mixedResult.add(participant)

            groupList.remove(participant)
            if (groupList.isEmpty()) {
                groups.remove(selectedGroupId)
            }

            lastGroupId = selectedGroupId
        }

        // Присваиваем стартовые номера и времена по глобальной позиции
        return mixedResult.mapIndexed { index, participant ->
            val number = (index + 1).toString()
            val startTime = competitionStartTime + index * intervalMs
            participant.copy(
                startNumber = number,
                startTime = startTime,
                chipNumber = if (punchingSystem == PunchingSystem.SPORTIDUINO) number else participant.chipNumber
            )
        }
    }
}

/**
 * Жеребьёвка по дистанциям с ограничением коридоров, зазором и равномерным растягиванием.
 *
 * Раскладывает участников по стартовым слотам (минутам), гарантируя инварианты:
 * - в одном слоте дистанция встречается не более одного раза;
 * - участники одной дистанции разнесены минимум на [gap] слотов;
 * - в одном слоте не более [corridors] участников;
 * - старты каждой дистанции равномерно растянуты по всему расписанию.
 *
 * Стартовые номера назначаются сквозно по порядку (слот, дистанция), стартовое время —
 * [competitionStartTime] + slotIndex * [intervalMs]. Для [PunchingSystem.SPORTIDUINO]
 * номер чипа приравнивается к стартовому номеру.
 *
 * Вынесена на верхний уровень (чистая функция без состояния) для удобства юнит-тестов.
 *
 * @param groupDistanceMap соответствие groupId → distanceId; при отсутствии записи ключом
 *   дистанции выступает сам groupId.
 * @param corridors максимум одновременных стартов в одну минуту (приводится к ≥ 1).
 * @param gap минимальный зазор между стартами одной дистанции в слотах (приводится к ≥ 1).
 */
internal fun drawParticipantsByDistance(
    participants: List<OrienteeringParticipant>,
    groupDistanceMap: Map<Long, Long>,
    punchingSystem: PunchingSystem?,
    competitionStartTime: Long,
    intervalMs: Long,
    corridors: Int,
    gap: Int
): List<OrienteeringParticipant> {
    if (participants.isEmpty()) return emptyList()

    val maxCorridors = corridors.coerceAtLeast(1)
    val minGap = gap.coerceAtLeast(1)

    // 1. Группировка по дистанции + случайная жеребьёвка участников внутри дистанции.
    val byDistance: Map<Long, List<OrienteeringParticipant>> = participants
        .groupBy { groupDistanceMap[it.groupId] ?: it.groupId }
        .mapValues { (_, ps) -> ps.shuffled() }

    // 2. Нижняя граница длины расписания (число слотов).
    val corridorBound = (participants.size + maxCorridors - 1) / maxCorridors
    val gapBound = byDistance.values.maxOf { (it.size - 1) * minGap + 1 }
    val slots = maxOf(corridorBound, gapBound, 1)

    // 3. Идеальные целевые слоты для равномерного растягивания каждой дистанции.
    val items = buildList {
        byDistance.forEach { (distanceId, ps) ->
            val count = ps.size
            ps.forEachIndexed { index, participant ->
                val target = ((index + 0.5) * slots / count).toInt().coerceIn(0, slots - 1)
                add(Triple(distanceId, participant, target))
            }
        }
    }
        // Перемешиваем перед стабильной сортировкой — случайный tiebreak при равных целях.
        .shuffled()
        .sortedBy { it.third }

    // 4. Жадная расстановка с жёсткими ограничениями (коридоры, зазор, уникальность дистанции).
    val slotLoad = HashMap<Int, Int>()
    val slotDistances = HashMap<Int, MutableSet<Long>>()
    val lastSlot = HashMap<Long, Int>()
    val placed = ArrayList<Pair<Int, OrienteeringParticipant>>(participants.size)

    items.forEach { (distanceId, participant, target) ->
        val minByGap = lastSlot[distanceId]?.let { it + minGap } ?: 0
        var slot = maxOf(target, minByGap)
        while ((slotLoad[slot] ?: 0) >= maxCorridors ||
            slotDistances[slot]?.contains(distanceId) == true
        ) {
            slot++
        }
        slotLoad[slot] = (slotLoad[slot] ?: 0) + 1
        slotDistances.getOrPut(slot) { mutableSetOf() }.add(distanceId)
        lastSlot[distanceId] = slot
        placed.add(slot to participant)
    }

    // 5. Сквозные номера и стартовые времена по порядку (слот, дистанция).
    return placed
        .sortedWith(
            compareBy(
                { it.first },
                { groupDistanceMap[it.second.groupId] ?: it.second.groupId }
            )
        )
        .mapIndexed { index, (slot, participant) ->
            val number = (index + 1).toString()
            participant.copy(
                startNumber = number,
                startTime = competitionStartTime + slot * intervalMs,
                chipNumber = if (punchingSystem == PunchingSystem.SPORTIDUINO) number else participant.chipNumber
            )
        }
}
