package com.competra.local.repository.livetrack

import com.competra.domain.models.livetrack.MyStart
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.domain.repository.auth.TokenRepository
import com.competra.domain.repository.livetrack.MyStartsRepository
import com.competra.domain.repository.orienteering.OrienteeringCompetitionRemoteRepository
import com.competra.local.dao.livetrack.MyStartDao
import com.competra.local.entities.livetrack.MyStartEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [MyStartsRepository]: список `competitions/registered` с сервера, копия — в Room. */
class MyStartsRepositoryImpl(
    private val dao: MyStartDao,
    private val remote: OrienteeringCompetitionRemoteRepository,
    private val tokenRepository: TokenRepository
) : MyStartsRepository {

    override fun observe(): Flow<List<MyStart>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(competitionId: String): MyStart? = dao.get(competitionId)?.toDomain()

    override suspend fun refresh(): Result<Unit> {
        // Гость: регистраций нет, запрос ответил бы 401.
        if (tokenRepository.getAccessToken().isNullOrEmpty()) return Result.success(Unit)
        return remote.getRegisteredCompetitions().map { competitions -> dao.replaceAll(competitions.map { it.toEntity() }) }
    }

    override suspend fun clear() = dao.deleteAll()
}

private fun OrienteeringCompetition.toEntity() = MyStartEntity(
    competitionId = competitionId.ifBlank { competition.id },
    title = competition.title,
    startDate = competition.startDate,
    endDate = competition.endDate,
    timeZoneId = competition.timeZoneId,
    address = competition.address,
    status = competition.status.name,
    controlTimeMinutes = controlTimeMinutes
)

private fun MyStartEntity.toDomain() = MyStart(
    competitionId = competitionId,
    title = title,
    startDate = startDate,
    endDate = endDate,
    timeZoneId = timeZoneId,
    address = address,
    status = runCatching { CompetitionStatus.valueOf(status) }.getOrDefault(CompetitionStatus.UNKNOWN),
    controlTimeMinutes = controlTimeMinutes
)
