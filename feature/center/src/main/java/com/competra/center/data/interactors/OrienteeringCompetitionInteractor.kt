package com.competra.center.data.interactors

import android.util.Log
import com.competra.center.data.creator.OrienteeringCreatorAction
import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.GroupWithParticipantsAndResults
import com.competra.domain.models.orienteering.OrienteeringCompetitionDetails
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.ParticipantWithResult
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.domain.models.orienteering.applyOvertimePolicy
import com.competra.domain.models.orienteering.effectiveControlTimeMinutes
import com.competra.domain.repository.orienteering.OrienteeringCompetitionLocalRepository
import com.competra.domain.repository.orienteering.OrienteeringCompetitionRemoteRepository
import com.competra.domain.sync.SyncTrigger

/**
 * Интерактор для работы с соревнованиями по спортивному ориентированию.
 * Обеспечивает синхронизацию данных между локальным и удалённым репозиториями,
 * реализует основную бизнес-логику приложения.
 *
 * @param localRepository Репозиторий для работы с локальной базой данных
 * @param remoteRepository Репозиторий для работы с удалённым сервером
 * @param syncTrigger Триггер немедленной «бесшумной» синхронизации после локальной мутации
 */
class OrienteeringCompetitionInteractor(
    private val localRepository: OrienteeringCompetitionLocalRepository,
    private val remoteRepository: OrienteeringCompetitionRemoteRepository,
    private val syncTrigger: SyncTrigger,
) {

    private fun touch() = syncTrigger.requestImmediateSync()

    /**
     * Явно запускает синхронизацию накопленных локальных изменений с сервером.
     * Используется в мастере создания соревнования, где промежуточные мутации
     * сохраняются с `silent = true`, а единственная синхронизация выполняется
     * в конце флоу.
     */
    fun commitPendingChanges() {
        syncTrigger.requestImmediateSync()
    }

    suspend fun saveCompetitionNew(
        orienteeringCompetition: OrienteeringCompetition,
        silent: Boolean = false
    ): Result<OrienteeringCompetition> {
        return localRepository.saveCompetition(orienteeringCompetition).also { if (!silent) touch() }
    }

    /**
     * Сохраняет соревнование локально с пометкой isSynced=false.
     * Фактическая выгрузка на сервер выполняется фоновым SyncOrchestrator/SyncCenterWorker
     * при появлении сети.
     */
    suspend fun publishCompetitionToServer(competition: OrienteeringCompetition): Result<OrienteeringCompetition> {
        return localRepository.updateCompetition(competition).mapCatching { competition }.also { touch() }
    }

    /**
     * Помечает группы как несинхронизированные. Worker подхватит и выгрузит на сервер.
     */
    suspend fun publishGroupsToServer(
        competitionId: String,
        groups: List<ParticipantGroup>
    ): Result<Unit> {
        return runCatching {
            groups.forEach { localRepository.updateParticipantGroup(it) }
        }.also { touch() }
    }

    suspend fun updateCompetitionNew(
        orienteeringCompetition: OrienteeringCompetition,
        silent: Boolean = false
    ): Result<OrienteeringCompetition> {
        return localRepository.updateCompetition(orienteeringCompetition).mapCatching { orienteeringCompetition }
            .also { applyControlTimeToStoredResults(orienteeringCompetition.competitionId) }
            .also { if (!silent) touch() }
    }

    suspend fun updateCompetition(
        orienteeringCompetition: OrienteeringCompetition,
        participantGroups: List<ParticipantGroup>? = null
    ): OrienteeringCreatorAction {
        val competitionId = orienteeringCompetition.competitionId

        // 1. Пытаемся обновить на сервере
//        remoteRepository.updateCompetition(orienteeringCompetition).onSuccess {
//            participantGroups?.let {
//                remoteRepository.updateCompetitionParticipantsGroups(competitionId, participantGroups)
//            }
//        }

        // 2. Всегда обновляем локально
        localUpdate(orienteeringCompetition, participantGroups)

        return OrienteeringCreatorAction.FailedCompetitionCreate("Ошибка")
    }

    suspend fun localUpdate(
        orienteeringCompetition: OrienteeringCompetition,
        participantGroups: List<ParticipantGroup>?,
        silent: Boolean = false
    ) {
        localRepository.updateCompetition(orienteeringCompetition).onSuccess {
            participantGroups?.let {
                localRepository.updateParticipantsGroups(orienteeringCompetition.competitionId, participantGroups)
            }
            applyControlTimeToStoredResults(orienteeringCompetition.competitionId)
        }.onFailure {

        }
        if (!silent) touch()
    }

    /**
     * Получает соревнование по его идентификатору из локального хранилища.
     *
     * @param competitionId Идентификатор соревнования
     * @return Данные соревнования или null, если соревнование не найдено
     */
    suspend fun getCompetition(competitionId: String): OrienteeringCompetition? {
        return localRepository.getCompetition(competitionId).getOrNull()
    }

    suspend fun getCompetitionWithDetails(competitionId: String): Result<OrienteeringCompetitionDetails> {
        return localRepository.getCompetitionWithDetails(competitionId)
    }

    /**
     * Получает список соревнований пользователя.
     *
     * Реализует стратегию:
     * 1. Сначала пытается получить данные из сети
     * 2. При успехе - обновляет локальное хранилище
     * 3. При неудаче - возвращает данные из локального хранилища
     *
     * @param userId Идентификатор пользователя
     * @return Result со списком соревнований или ошибкой
     */
    suspend fun getCompetitionsByUserId(userId: String): Result<List<OrienteeringCompetition>> {
        // 1. Сначала пробуем получить из сети
        val remoteResult = remoteRepository.getCompetitionsByUserid()

        remoteResult.onSuccess { competitions ->
            // 2. Если сеть успешна — обновляем локальное хранилище
            localRepository.saveCompetitions(competitions, markUnsynced = false)
            val compet = localRepository.getCompetitionsByUserid(userId).getOrNull()
            Log.d("LOG_TAG", "getCompetitionsByUserId: size = ${compet?.size}")
            return Result.success(compet ?: competitions)
        }

        // 3. Если сеть упала — достаём локальные данные
        val localResult = localRepository.getCompetitionsByUserid(userId)

        localResult.onSuccess { localCompetitions ->
            return Result.success(localCompetitions)
        }

        // 4. Если вообще всё сломалось — возвращаем ошибку сети
        return Result.failure(remoteResult.exceptionOrNull() ?: Exception("Unknown error"))
    }

    /**
     * Сохраняет участника соревнования локально, затем синхронизирует с сервером.
     * При успешной синхронизации обновляет флаг isSynced в локальной БД.
     *
     * @param participant Данные участника для сохранения
     * @return Сохранённый участник или null в случае ошибки
     */
    suspend fun saveParticipant(participant: OrienteeringParticipant): OrienteeringParticipant? {
        return localRepository.saveParticipant(participant).getOrNull().also { touch() }
    }

    /**
     * Возвращает существующий результат участника по его ID, или null если записи нет.
     */
    suspend fun getResultByParticipantId(participantId: String): OrienteeringResult? {
        return localRepository.getResultByParticipant(participantId).getOrNull()
    }

    /**
     * Применяет конфликтующий результат: перезаписывает локальную запись (сохраняя её ID)
     * и синхронизирует с сервером.
     *
     * @param existing  Существующая запись в БД (сохраняем её локальный id и remoteId).
     * @param newResult Новые данные результата.
     */
    suspend fun applyConflictResult(existing: OrienteeringResult, newResult: OrienteeringResult) {
        val updated = newResult.copy(id = existing.id, remoteId = newResult.remoteId ?: existing.remoteId)
        localRepository.updateResults(listOf(updated)).onSuccess {
            updateResultsAndRanks(updated)
        }
        touch()
    }

    /**
     * Обновляет список участников в локальном хранилище.
     *
     * @param participants Список участников для обновления
     */
    suspend fun updateParticipants(participants: List<OrienteeringParticipant>) {
        localRepository.updateParticipants(participants = participants)
        touch()
    }

    suspend fun syncParticipantsAfterDraw(participants: List<OrienteeringParticipant>) {
        if (participants.isEmpty()) return
        // Локально пометить участников как несинхронизированные.
        // SyncCenterWorker сам выгрузит их на сервер с актуальными remoteId групп/соревнования.
        localRepository.updateParticipants(participants)
        touch()
    }

    /**
     * Помечает соревнование на удаление (soft-delete) и запускает синхронизацию.
     *
     * Запись остаётся в локальной БД с isDeleted=true и исчезает из списков пользователя.
     * SyncCenterWorker отправит DELETE на сервер и после успеха физически удалит запись
     * вместе со всеми связанными данными. Если соревнование ни разу не было на сервере,
     * Worker удалит его локально сразу.
     *
     * @param competitionId Идентификатор соревнования.
     * @return Result операции пометки на удаление.
     */
    suspend fun deleteCompetition(competitionId: String): Result<Unit> {
        return localRepository.markCompetitionDeleted(competitionId).also { touch() }
    }

    /**
     * Удаляет участника из локальной базы данных.
     *
     * @param participantId Идентификатор участника
     * @return Result операции удаления
     */
    suspend fun deleteParticipant(participantId: String): Result<Unit> {
        return localRepository.deleteParticipant(participantId).also { touch() }
    }

    /**
     * Обновляет участника только в локальной базе данных.
     *
     * @param participant Участник для обновления
     * @return Result операции локального сохранения
     */
    suspend fun updateParticipantLocally(participant: OrienteeringParticipant): Result<Any> {
        return localRepository.updateParticipants(listOf(participant)).also { touch() }
    }

    /**
     * Синхронизирует участника с сервером.
     * При успехе обновляет флаг isSynced в локальной БД.
     *
     * @param participant Участник для синхронизации
     */
    /**
     * Локально помечает участника как несинхронизированного. Worker подхватит и выгрузит на сервер.
     */
    suspend fun syncParticipantWithServer(participant: OrienteeringParticipant) {
        localRepository.updateParticipants(listOf(participant))
        touch()
    }

    /**
     * Создаёт информацию о группах участников для соревнования.
     *
     * Внутренняя функция, которая:
     * 1. Пытается сохранить группы на сервере
     * 2. При успехе - сохраняет их локально
     * 3. При неудаче - сохраняет только локально
     *
     * @param competitionId Идентификатор соревнования
     * @param participantGroups Список групп участников для сохранения
     */
    /**
     * Сохраняет группы локально. Worker позже выгрузит их на сервер.
     */
    suspend fun createParticipantsGroupsInfo(
        competitionId: String,
        participantGroups: List<ParticipantGroup>
    ) {
        localSaveParticipantGroups(participantGroups)
    }

    suspend fun localSaveParticipantGroups(
        participantGroups: List<ParticipantGroup>,
        silent: Boolean = false
    ) {
        localRepository.saveParticipantsGroups(participantGroups)
        participantGroups.firstOrNull()?.let { applyControlTimeToStoredResults(it.competitionId) }
        if (!silent) touch()
    }

    suspend fun updateParticipantGroup(
        participantGroup: ParticipantGroup,
        silent: Boolean = false
    ): Result<Any> {
        return localRepository.updateParticipantGroup(participantGroup)
            .also { applyControlTimeToStoredResults(participantGroup.competitionId) }
            .also { if (!silent) touch() }
    }

    /**
     * Приводит уже сохранённые результаты соревнования в соответствие с текущим контрольным
     * временем и политикой его применения, пересчитывая места.
     *
     * Сервер делает то же самое у себя при изменении настроек (ResultRanking в проекте eSport);
     * локальный пересчёт нужен, чтобы судья видел актуальные статусы сразу и офлайн.
     * Статусы выводятся заново, поэтому снятие обратимо: вернули IGNORE — вернулись места.
     */
    suspend fun applyControlTimeToStoredResults(competitionId: String) {
        val competition = localRepository.getCompetition(competitionId).getOrNull() ?: return
        val groups = localRepository.getResultByGroups(competitionId).getOrNull() ?: return

        val updated = groups.flatMap { groupData ->
            val controlTimeMinutes = effectiveControlTimeMinutes(groupData.group, competition)
            val results = groupData.participants.mapNotNull { it.result }
            val derived = results.map { result ->
                val status = applyOvertimePolicy(
                    status = result.status,
                    totalTimeSeconds = result.totalTime,
                    controlTimeMinutes = controlTimeMinutes,
                    policy = competition.overtimePolicy
                )
                if (status == result.status) {
                    result
                } else {
                    result.copy(
                        status = status,
                        rank = if (status == ResultStatus.OVERTIME) null else result.rank
                    )
                }
            }
            // Ничего не поменялось — не трогаем БД: функция вызывается при каждом сохранении настроек.
            if (derived == results) emptyList() else derived.withRecalculatedRanks(competition.direction)
        }

        if (updated.isNotEmpty()) {
            localRepository.updateResults(updated)
        }
    }

    /**
     * Получает список участников соревнования из локального хранилища.
     *
     * @param competitionId Идентификатор соревнования
     * @return Result со списком участников или ошибкой
     */
    suspend fun getParticipants(competitionId: String): Result<List<OrienteeringParticipant>> {
        return localRepository.getParticipants(competitionId = competitionId)
    }

    /**
     * Получает участника соревнования по номеру чипа.
     *
     * @param competitionId Идентификатор соревнования
     * @param chipNumber Номер чипа участника
     * @return Result с данными участника или ошибкой
     */
    suspend fun getParticipantByChipNumber(
        competitionId: String,
        chipNumber: Int
    ): Result<OrienteeringParticipant> {
        return localRepository.getParticipantByChipNumber(
            competitionId = competitionId,
            chipNumber = chipNumber
        )
    }

    /**
     * Получает группу участников по её идентификатору.
     *
     * @param groupId Идентификатор группы
     * @return Result с данными группы или ошибкой
     */
    suspend fun getParticipantGroup(groupId: Long): Result<ParticipantGroup> {
        return localRepository.getParticipantGroup(groupId)
    }

    /**
     * Сохраняет результат участника локально, пересчитывает места,
     * затем синхронизирует результат с сервером.
     * При успешной синхронизации обновляет флаг isSynced в локальной БД.
     */
    suspend fun saveParticipantResult(orienteeringResult: OrienteeringResult) {
        localRepository.saveParticipantResult(orienteeringResult).onSuccess { savedId ->
            val savedResult = orienteeringResult.copy(id = savedId as Long)
            updateResultsAndRanks(newResults = savedResult)
        }.onFailure {
            Log.d("LOG_TAG", "saveParticipantResult: ${it.message}")
        }
        touch()
    }

    /**
     * Пересчитывает места (rank) для списка результатов соревнования.
     *
     * Алгоритм работы:
     * 1. Фильтрует только финишировавших участников (status = FINISHED)
     * 2. Сортирует их по возрастанию общего времени (totalTime + penaltyTime)
     * 3. Присваивает места, начиная с 1
     * 4. Обрабатывает участников с одинаковым временем - присваивает одинаковые места
     * 5. Сохраняет исходные места для не финишировавших участников
     *
     * @param results Исходный список результатов
     * @return Новый список результатов с пересчитанными местами
     */
    fun recalculateRanks(results: List<OrienteeringResult>): List<OrienteeringResult> {
        // Разделяем финишировавших и остальных
        val (finished, others) = results.partition { it.status == ResultStatus.FINISHED }

        if (finished.isEmpty()) return results

        // Сортируем финишировавших по общему времени (totalTime + penaltyTime)
        val sortedFinished = finished.sortedBy { result ->
            (result.totalTime ?: Long.MAX_VALUE) + (result.penaltyTime)
        }

        // Пересчитываем места с учетом возможных совпадений времени
        var currentRank = 1
        var previousTime: Long? = null
        var skipCount = 0

        val rankedFinished = sortedFinished.mapIndexed { index, result ->
            val totalTimeWithPenalty = (result.totalTime ?: Long.MAX_VALUE) + result.penaltyTime

            when {
                // Если это первый участник или время отличается от предыдущего
                previousTime == null || totalTimeWithPenalty != previousTime -> {
                    currentRank = index + 1 - skipCount
                    previousTime = totalTimeWithPenalty
                    result.copy(rank = currentRank)
                }
                // Если время совпадает с предыдущим - присваиваем то же место
                else -> {
                    skipCount++
                    result.copy(rank = currentRank)
                }
            }
        }

        // Возвращаем все результаты (финишировавшие с новыми местами, остальные без изменений)
        return rankedFinished + others
    }

    /**
     * Пересчитывает места с учётом формата соревнования: для BY_CHOICE (score-О) — по сумме
     * баллов убыв. с тай-брейком по времени прохождения дистанции, для остальных направлений —
     * как раньше, по общему времени с учётом штрафа (возрастание).
     *
     * Тай-брейк BY_CHOICE использует [OrienteeringResult.totalTime] (finish - start участника),
     * а не [OrienteeringResult.finishTime] (абсолютное время по часам) — при разных/интервальных
     * стартах абсолютное время финиша не отражает, кто пробежал быстрее.
     */
    fun recalculateRanksV2(
        results: List<OrienteeringResult>,
        direction: OrienteeringDirection = OrienteeringDirection.FORWARD
    ): List<OrienteeringResult> {
        val (finished, others) = results.partition { it.status == ResultStatus.FINISHED }

        if (finished.isEmpty()) return results

        val sortedFinished = if (direction == OrienteeringDirection.BY_CHOICE) {
            finished.sortedWith(
                compareByDescending<OrienteeringResult> { it.totalScore ?: 0 }
                    .thenBy { it.totalTime ?: Long.MAX_VALUE }
            )
        } else {
            finished.sortedBy { (it.totalTime ?: Long.MAX_VALUE) + it.penaltyTime }
        }

        // Для BY_CHOICE ключ должен включать totalTime — иначе два участника с одинаковыми
        // очками, но разным временем (тай-брейк уже учтён сортировкой выше), получат одно и то
        // же место вместо разных.
        fun rankKey(result: OrienteeringResult): Any = if (direction == OrienteeringDirection.BY_CHOICE) {
            (result.totalScore ?: 0) to (result.totalTime ?: Long.MAX_VALUE)
        } else {
            (result.totalTime ?: Long.MAX_VALUE) + result.penaltyTime
        }

        var rank = 1
        var prevKey: Any? = null
        var skipCount = 0

        val rankedFinished = sortedFinished.mapIndexed { index, result ->
            val key = rankKey(result)
            if (prevKey != null && key == prevKey) {
                skipCount++
            } else {
                rank = index + 1 - skipCount
                prevKey = key
            }
            result.copy(rank = rank)
        }

        return rankedFinished + others
    }

    /**
     * Расширение для списка OrienteeringResult - обновляет ранги и возвращает отсортированный список
     */
    fun List<OrienteeringResult>.withRecalculatedRanks(
        direction: OrienteeringDirection = OrienteeringDirection.FORWARD
    ): List<OrienteeringResult> {
        return recalculateRanksV2(this, direction).sortedBy { it.rank ?: Int.MAX_VALUE }
    }

    /**
     * Пример использования в Interactor/Repository:
     */
    suspend fun updateResultsAndRanks(
        newResults: OrienteeringResult
    ): List<OrienteeringResult> {
        // Получаем текущие результаты
        val currentResults = localRepository.getResultForGroup(
            competitionId = newResults.competitionId,
            groupId = newResults.groupId
        ).getOrNull() ?: emptyList()

        // Объединяем с новыми
        val allResults = (currentResults + newResults).distinctBy { it.participantId }

        val direction = localRepository.getCompetition(newResults.competitionId).getOrNull()?.direction
            ?: OrienteeringDirection.FORWARD

        // Пересчитываем места
        val updatedResults = allResults.withRecalculatedRanks(direction)

        // Сохраняем обновленные результаты
        localRepository.updateResults(updatedResults)
        touch()

        return updatedResults
    }

    suspend fun getResultsByGroups(competitionId: String): Result<List<GroupWithParticipantsAndResults>> {
        return localRepository.getResultByGroups(competitionId)
    }

    /**
     * Результаты с сервера, а не из локальной БД. Нужны для завершённых соревнований: результаты
     * могли быть внесены не с этого устройства (другой судейский телефон, веб), фоновая
     * синхронизация только выгружает локальные изменения и не скачивает чужие результаты обратно.
     *
     * У серверных [ParticipantGroup] не бывает осмысленного локального groupId (см. комментарий
     * в ParticipantGroupResponse.toDomain() — "устанавливается при zip-сопоставлении"), поэтому
     * здесь groupId явно проставляется равным remoteId — иначе у всех групп groupId=0 и экраны
     * группового просмотра (таблица сплитов, график гонки) не могут найти нужную группу.
     */
    suspend fun getResultsByGroupsRemote(competitionId: String): Result<List<GroupWithParticipantsAndResults>> {
        return runCatching {
            val groups = remoteRepository.getCompetitionParticipantsGroups(competitionId).getOrThrow()
            val participants = remoteRepository.getParticipantsForCompetition(competitionId).getOrThrow()
            val results = remoteRepository.getResultsByCompetition(competitionId).getOrThrow()

            val resultsByParticipant = results.associateBy { it.participantId }
            val participantsByGroup = participants.groupBy { it.groupId }

            groups.map { group ->
                val groupParticipants = participantsByGroup[group.remoteId] ?: emptyList()
                GroupWithParticipantsAndResults(
                    group = group.copy(groupId = group.remoteId ?: 0L),
                    participants = groupParticipants.map { participant ->
                        ParticipantWithResult(participant = participant, result = resultsByParticipant[participant.id])
                    }
                )
            }
        }
    }

    /**
     * Результаты для отображения: для завершённых соревнований — с сервера (см.
     * [getResultsByGroupsRemote], с fallback на локальные при ошибке сети), для остальных —
     * из локальной БД без сетевых запросов. Общая точка входа для всех экранов, показывающих
     * результаты/сплиты/график гонки (экран результатов, таблица сплитов группы, график гонки,
     * сплиты участника).
     */
    suspend fun getResultsByGroupsAuto(competitionId: String): List<GroupWithParticipantsAndResults> {
        val isFinished = getCompetition(competitionId)?.competition?.status in
            listOf(CompetitionStatus.FINISHED, CompetitionStatus.ARCHIVED)
        return if (isFinished) {
            getResultsByGroupsRemote(competitionId).getOrNull()
                ?: getResultsByGroups(competitionId).getOrNull()
                ?: emptyList()
        } else {
            getResultsByGroups(competitionId).getOrNull() ?: emptyList()
        }
    }

    /**
     * Утверждает результаты соревнования, делая их недоступными для редактирования.
     *
     * @param competitionId Идентификатор соревнования.
     * @return Результат операции.
     */
    suspend fun approveResults(competitionId: String): Result<Any> {
        return localRepository.updateIsEditableForCompetition(competitionId, false).also { touch() }
    }

    /**
     * Обновляет результат участника и пересчитывает ранги в группе.
     *
     * @param orienteeringResult Результат для обновления.
     */
    suspend fun updateParticipantResult(orienteeringResult: OrienteeringResult) {
        localRepository.updateResults(listOf(orienteeringResult)).onSuccess {
            updateResultsAndRanks(orienteeringResult)
        }
        touch()
    }

    /**
     * Батчево накатывает результаты, распознанные при импорте из HTML-протокола (восстановление
     * после сбоя): bulk-upsert через [OrienteeringCompetitionLocalRepository.saveResults]
     * (в отличие от updateResults, корректно создаёт и отсутствующие строки), затем пересчитывает
     * места по каждой затронутой группе — аналогично [updateResultsAndRanks], но одним проходом.
     */
    suspend fun importResults(results: List<OrienteeringResult>): Result<Any> {
        if (results.isEmpty()) return Result.success(Unit)

        return localRepository.saveResults(results).onSuccess {
            val affectedGroups = results.map { it.competitionId to it.groupId }.distinct()
            affectedGroups.forEach { (competitionId, groupId) ->
                val currentResults = localRepository.getResultForGroup(competitionId, groupId).getOrNull() ?: emptyList()
                val direction = localRepository.getCompetition(competitionId).getOrNull()?.direction
                    ?: OrienteeringDirection.FORWARD
                localRepository.updateResults(currentResults.withRecalculatedRanks(direction))
            }
            touch()
        }
    }

    /**
     * Загружает дистанции и группы участников с сервера и синхронизирует их в локальную БД.
     *
     * Алгоритм:
     * 1. Получает дистанции с сервера, делает upsert (update если remoteId совпадает, insert иначе)
     * 2. Перезагружает дистанции из локальной БД, строит карту remoteId → localId
     * 3. Получает группы с сервера, конвертирует distanceId (remote → local), сохраняет в БД
     *
     * Локальная дистанция с несинхронизированными изменениями (isSynced == false) серверной копией
     * не перезаписывается — иначе ещё не запушенная правка (например, номера финишного КП) тихо
     * затиралась бы устаревшими серверными данными и навсегда помечалась бы как синхронизированная.
     *
     * @param remoteCompetitionId Серверный ID соревнования.
     * @param localCompetitionId Локальный ID соревнования в Room.
     */
    suspend fun fetchAndSyncFromServer(competitionId: String) {
        val serverDistances = remoteRepository
            .getDistancesForCompetition(competitionId)
            .getOrNull() ?: return

        val existingByRemoteId = localRepository.getDistances(competitionId)
            .getOrNull().orEmpty()
            .associateBy { it.remoteId }

        serverDistances.forEach { serverDist ->
            val existing = serverDist.remoteId?.let { existingByRemoteId[it] }
            if (existing != null) {
                if (existing.isSynced) {
                    localRepository.updateDistance(serverDist.copy(id = existing.id), markUnsynced = false)
                }
            } else {
                localRepository.saveDistance(serverDist, markUnsynced = false)
            }
        }

        val remoteToLocalDistanceId = localRepository.getDistances(competitionId)
            .getOrNull().orEmpty()
            .filter { it.remoteId != null }
            .associate { it.remoteId!! to it.id }

        val serverGroups = remoteRepository
            .getCompetitionParticipantsGroups(competitionId)
            .getOrNull() ?: return

        val fixedGroups = serverGroups.map { group ->
            group.copy(
                competitionId = competitionId,
                distanceId = remoteToLocalDistanceId[group.distanceId] ?: group.distanceId
            )
        }
        localRepository.updateParticipantsGroups(competitionId, fixedGroups, markUnsynced = false)
    }

    /**
     * Загружает участников с сервера и синхронизирует их в локальную БД.
     *
     * Алгоритм:
     * 1. Получает участников с сервера по remoteCompetitionId
     * 2. Строит карту server groupId → local groupId (по remoteId группы)
     * 3. Для каждого участника: update если remoteId совпадает, insert если новый
     *
     * Вызывать ПОСЛЕ fetchAndSyncFromServer, чтобы группы уже были синхронизированы.
     *
     * @param remoteCompetitionId Серверный ID соревнования.
     * @param localCompetitionId Локальный ID соревнования в Room.
     */
    suspend fun fetchAndSyncParticipantsFromServer(competitionId: String) {
        val serverParticipants = remoteRepository
            .getParticipantsForCompetition(competitionId)
            .getOrNull() ?: return

        // Карта serverGroupId → localGroupId для корректной привязки участника к группе
        val remoteToLocalGroupId = localRepository.getCompetitionWithDetails(competitionId)
            .getOrNull()
            ?.groupsWithParticipants
            ?.map { it.group }
            ?.filter { it.remoteId != null }
            ?.associate { it.remoteId!! to it.groupId }
            ?: emptyMap()

        // Сохраняем локальные isChipGiven — сервер не является источником истины для этого поля
        val existingChipGivenById = localRepository.getParticipants(competitionId)
            .getOrNull().orEmpty()
            .associate { it.id to it.isChipGiven }

        serverParticipants.forEach { serverParticipant ->
            val localGroupId = remoteToLocalGroupId[serverParticipant.groupId] ?: serverParticipant.groupId
            val preservedIsChipGiven = existingChipGivenById[serverParticipant.id] ?: serverParticipant.isChipGiven
            val participantToSave = serverParticipant.copy(
                competitionId = competitionId,
                groupId = localGroupId,
                isChipGiven = preservedIsChipGiven
            )
            if (serverParticipant.id in existingChipGivenById) {
                // Обновляем без DELETE+INSERT, чтобы не триггерить CASCADE удаление результатов
                localRepository.updateParticipants(listOf(participantToSave), markUnsynced = false)
            } else {
                localRepository.saveParticipant(participantToSave, markUnsynced = false)
            }
        }
    }

    /**
     * Публикует дистанции соревнования на сервер.
     * После успешной публикации обновляет remoteId и isSynced для каждой дистанции локально.
     *
     * @param remoteCompetitionId Серверный ID соревнования.
     * @param localCompetitionId Локальный ID соревнования в Room.
     * @param distances Список дистанций для публикации.
     * @return Список дистанций с проставленными remoteId или ошибка.
     */
    /**
     * Помечает дистанции как несинхронизированные. Worker выгрузит их на сервер при появлении сети.
     */
    suspend fun publishDistancesToServer(
        competitionId: String,
        distances: List<Distance>
    ): Result<List<Distance>> {
        return runCatching {
            distances.forEach { localRepository.updateDistance(it) }
            distances
        }.also { touch() }
    }

    /**
     * Сохраняет новую дистанцию для соревнования.
     *
     * @param distance Модель дистанции.
     * @return Результат операции с ID сохраненной записи.
     */
    suspend fun saveDistance(distance: Distance, silent: Boolean = false): Result<Long> {
        return localRepository.saveDistance(distance).also { if (!silent) touch() }
    }

    /**
     * Обновляет существующую дистанцию.
     *
     * @param distance Модель дистанции.
     * @return Результат операции.
     */
    suspend fun updateDistance(distance: Distance, silent: Boolean = false): Result<Any> {
        return localRepository.updateDistance(distance).also { if (!silent) touch() }
    }

    suspend fun getDistanceById(distanceId: Long): Result<Distance?> {
        return localRepository.getDistanceById(distanceId)
    }

    /**
     * Получает список дистанций соревнования.
     */
    suspend fun getDistances(competitionId: String): Result<List<Distance>> {
        return localRepository.getDistances(competitionId)
    }

    /**
     * Помечает соревнование как «жеребьёвка проведена» и сохраняет в локальной БД.
     */
    suspend fun setDrawConducted(competitionId: String) {
        val competition = localRepository.getCompetition(competitionId).getOrNull() ?: return
        localRepository.updateCompetition(competition.copy(isDrawConducted = true))
        touch()
    }

    /**
     * Возвращает true, если все участники соревнования имеют терминальный статус результата.
     * Не изменяет никакие данные.
     */
    suspend fun areAllParticipantsFinished(competitionId: String): Boolean {
        val participants = localRepository.getParticipants(competitionId).getOrNull() ?: return false
        if (participants.isEmpty()) return false
        val terminalStatuses = setOf(
            ResultStatus.FINISHED, ResultStatus.OVERTIME, ResultStatus.DSQ, ResultStatus.DNS, ResultStatus.DNF
        )
        return participants.all { p ->
            localRepository.getResultByParticipant(p.id).getOrNull()?.status in terminalStatuses
        }
    }

    /**
     * Если регистрация настроена закрываться в момент старта соревнования
     * (т.е. `registrationEnd == startDate`) и время старта уже наступило, а статус
     * всё ещё [CompetitionStatus.REGISTRATION_OPEN] — переводит соревнование
     * в [CompetitionStatus.IN_PROGRESS] и синхронизирует с сервером.
     *
     * Работает только для [StartTimeMode.STRICT]. Для [StartTimeMode.USER_SET] и
     * [StartTimeMode.BY_START_STATION] время старта в форме создания — лишь информация,
     * фактический старт инициирует организатор (USER_SET — ручной запуск таймера,
     * BY_START_STATION — отметка на стартовой станции), поэтому автоматически менять
     * статус по `startDate` нельзя.
     *
     * @param competitionId Идентификатор соревнования.
     * @return true если статус был автоматически переключён.
     */
    suspend fun tryAutoStartFromRegistration(competitionId: String): Boolean {
        val competition = localRepository.getCompetition(competitionId).getOrNull() ?: return false
        val base = competition.competition
        val regEnd = base.registrationEnd
        val isAtStartMode = regEnd != null && regEnd == base.startDate
        val timeReached = System.currentTimeMillis() >= base.startDate
        val isStrictMode = competition.startTimeMode == StartTimeMode.STRICT
        if (!isStrictMode || !isAtStartMode || !timeReached ||
            base.status != CompetitionStatus.REGISTRATION_OPEN
        ) {
            return false
        }
        val started = competition.copy(
            competition = base.copy(status = CompetitionStatus.IN_PROGRESS)
        )
        localRepository.updateCompetition(started)
        touch()
        return true
    }

    /**
     * Проверяет, завершили ли все участники соревнование (имеют терминальный статус результата).
     * Если да — меняет статус соревнования на FINISHED и синхронизирует с сервером.
     *
     * @param competitionId Идентификатор соревнования.
     * @return true если автоматически перевёл соревнование в FINISHED.
     */
    suspend fun tryAutoFinish(competitionId: String): Boolean {
        val participants = localRepository.getParticipants(competitionId).getOrNull() ?: return false
        if (participants.isEmpty()) return false

        val terminalStatuses = setOf(
            ResultStatus.FINISHED, ResultStatus.OVERTIME, ResultStatus.DSQ, ResultStatus.DNS, ResultStatus.DNF
        )
        val allDone = participants.all { p ->
            localRepository.getResultByParticipant(p.id).getOrNull()?.status in terminalStatuses
        }
        if (!allDone) return false

        val competition = localRepository.getCompetition(competitionId).getOrNull() ?: return false
        if (competition.competition.status == CompetitionStatus.FINISHED) return false

        val finished = competition.copy(
            competition = competition.competition.copy(status = CompetitionStatus.FINISHED)
        )
        localRepository.updateCompetition(finished)
        touch()
        return true
    }

    /**
     * Проставляет статус DNF всем участникам, которые не имеют результата
     * со статусом FINISHED, DSQ или DNS.
     *
     * Вызывается при завершении соревнования.
     */
    suspend fun markNonFinishedAsDNF(competitionId: String) {
        val participants = localRepository.getParticipants(competitionId).getOrNull() ?: return
        val terminalStatuses = setOf(
            ResultStatus.FINISHED, ResultStatus.OVERTIME, ResultStatus.DSQ, ResultStatus.DNS
        )

        participants.forEach { participant ->
            val existing = localRepository.getResultByParticipant(participant.id).getOrNull()
            when {
                existing == null -> {
                    val dnf = OrienteeringResult(
                        competitionId = competitionId,
                        participantId = participant.id,
                        groupId = participant.groupId,
                        startTime = participant.startTime.takeIf { it > 0 },
                        finishTime = null,
                        totalTime = null,
                        rank = null,
                        status = ResultStatus.DNF,
                        penaltyTime = 0,
                        splits = null
                    )
                    localRepository.saveParticipantResult(dnf)
                }
                existing.status !in terminalStatuses -> {
                    localRepository.updateResults(listOf(existing.copy(status = ResultStatus.DNF)))
                }
            }
        }
        touch()
    }

}
