package com.competra.domain.repository.orienteering

import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.GroupWithParticipantsAndResults
import com.competra.domain.models.orienteering.OrienteeringCompetitionDetails
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.Distance

/**
 * Интерфейс локального репозитория для работы с данными соревнований по ориентированию.
 *
 * Параметр [markUnsynced] на мутирующих методах:
 * - true (дефолт) — путь UI/Interactor: при сохранении проставляются isSynced=false,
 *   lastModified=now, syncError=null. Worker позже выгрузит запись на сервер.
 * - false — путь Worker и server→local pull: запись пишется как есть, isSynced/serverUpdatedAt
 *   устанавливаются вызывающим явно.
 */
interface OrienteeringCompetitionLocalRepository {

    suspend fun saveCompetition(orienteeringCompetition: OrienteeringCompetition, markUnsynced: Boolean = true): Result<OrienteeringCompetition>
    suspend fun saveCompetitions(orienteeringCompetition: List<OrienteeringCompetition>, markUnsynced: Boolean = true): Result<List<OrienteeringCompetition>>
    suspend fun updateCompetition(orienteeringCompetition: OrienteeringCompetition, markUnsynced: Boolean = true): Result<Any>
    suspend fun getCompetition(competitionId: String): Result<OrienteeringCompetition?>

    suspend fun saveParticipantsGroups(participantGroups: List<ParticipantGroup>, markUnsynced: Boolean = true): Result<Any>
    suspend fun updateParticipantsGroups(competitionId: String, participantGroups: List<ParticipantGroup>, markUnsynced: Boolean = true): Result<Any>

    /**
     * Применяет серверный снимок групп соревнования при загрузке с сервера (pull), не затирая
     * группы с неотправленными локальными изменениями (правка или пометка на удаление).
     * Правила слияния — см. [com.competra.domain.sync.planServerMerge].
     */
    suspend fun mergeParticipantGroupsFromServer(competitionId: String, serverGroups: List<ParticipantGroup>): Result<Any>
    suspend fun updateParticipantGroup(participantGroup: ParticipantGroup, markUnsynced: Boolean = true): Result<Any>
    suspend fun getCompetitionWithDetails(competitionId: String): Result<OrienteeringCompetitionDetails>
    suspend fun getCompetitionsByUserid(userId: String): Result<List<OrienteeringCompetition>>

    suspend fun deleteCompetition(competitionId: String): Result<Unit>

    /**
     * Помечает соревнование на удаление (soft-delete) для последующей синхронизации с сервером.
     * Запись остаётся в БД с isDeleted=true до тех пор, пока SyncCenterWorker не отправит DELETE
     * на сервер и не удалит её физически.
     */
    suspend fun markCompetitionDeleted(competitionId: String): Result<Unit>

    suspend fun saveParticipant(participant: OrienteeringParticipant, markUnsynced: Boolean = true): Result<OrienteeringParticipant?>

    suspend fun getParticipants(competitionId: String): Result<List<OrienteeringParticipant>>
    suspend fun updateParticipants(participants: List<OrienteeringParticipant>, markUnsynced: Boolean = true) : Result<Any>
    suspend fun deleteParticipant(participantId: String): Result<Unit>

    suspend fun getParticipantByChipNumber(competitionId: String, chipNumber: Int) : Result<OrienteeringParticipant>
    suspend fun getParticipantGroup(groupId: Long) : Result<ParticipantGroup>
    suspend fun saveParticipantResult(orienteeringResult: OrienteeringResult, markUnsynced: Boolean = true): Result<Any>
    suspend fun getResultByParticipant(participantId: String): Result<OrienteeringResult?>
    suspend fun getResultForGroup(competitionId: String, groupId: Long): Result<List<OrienteeringResult>>
    suspend fun updateResults(orienteeringResult: List<OrienteeringResult>, markUnsynced: Boolean = true): Result<Any>

    /**
     * Bulk-upsert результатов (INSERT ... REPLACE по id). В отличие от [updateResults] (Room `@Update`,
     * молча игнорирует записи без PK-совпадения), корректно создаёт как новые строки (id = 0), так и
     * обновляет существующие — нужен для импорта/восстановления, когда часть результатов участников
     * могла быть полностью потеряна.
     */
    suspend fun saveResults(orienteeringResult: List<OrienteeringResult>, markUnsynced: Boolean = true): Result<Any>

    suspend fun getResultByGroups(competitionId: String): Result<List<GroupWithParticipantsAndResults>>

    /**
     * Обновляет флаг возможности редактирования для всех результатов соревнования.
     *
     * @param competitionId Идентификатор соревнования.
     * @param isEditable Значение флага.
     */
    suspend fun updateIsEditableForCompetition(competitionId: String, isEditable: Boolean): Result<Any>

    /**
     * Сохраняет новую дистанцию для соревнования.
     */
    suspend fun saveDistance(distance: Distance, markUnsynced: Boolean = true): Result<Long>

    /**
     * Получает дистанцию по её локальному идентификатору.
     */
    suspend fun getDistanceById(distanceId: Long): Result<Distance?>

    /**
     * Получает список дистанций соревнования.
     */
    suspend fun getDistances(competitionId: String): Result<List<Distance>>

    /**
     * Обновляет данные существующей дистанции.
     */
    suspend fun updateDistance(distance: Distance, markUnsynced: Boolean = true): Result<Any>

    // ====== Запросы для SyncCenterWorker ======

    /** Соревнования, ожидающие выгрузки на сервер. */
    suspend fun getUnsyncedCompetitions(): List<OrienteeringCompetition>

    /** Соревнования, помеченные на удаление и ещё не синхронизированные. */
    suspend fun getCompetitionsMarkedForDeletion(): List<OrienteeringCompetition>

    /** Группы участников, ожидающие выгрузки. */
    suspend fun getUnsyncedGroups(): List<ParticipantGroup>

    /** Группы, помеченные на удаление. */
    suspend fun getGroupsMarkedForDeletion(): List<ParticipantGroup>

    /** Дистанции, ожидающие выгрузки. */
    suspend fun getUnsyncedDistances(): List<Distance>

    /** Дистанции, помеченные на удаление. */
    suspend fun getDistancesMarkedForDeletion(): List<Distance>

    /** Участники, ожидающие выгрузки. */
    suspend fun getUnsyncedParticipants(): List<OrienteeringParticipant>

    /** Участники, помеченные на удаление. */
    suspend fun getParticipantsMarkedForDeletion(): List<OrienteeringParticipant>

    /** Результаты, ожидающие выгрузки. */
    suspend fun getUnsyncedResults(): List<OrienteeringResult>

    /** Результаты, помеченные на удаление. */
    suspend fun getResultsMarkedForDeletion(): List<OrienteeringResult>

    // ====== Physical delete после успешной DELETE-выгрузки ======

    /** Физически удаляет группу из локальной БД (вызывается Worker'ом после успешного DELETE на сервере). */
    suspend fun purgeGroupLocally(groupId: Long)

    /** Физически удаляет дистанцию из локальной БД. */
    suspend fun purgeDistanceLocally(distanceId: Long)

    /** Физически удаляет результат из локальной БД. */
    suspend fun purgeResultLocally(resultId: Long)
}
