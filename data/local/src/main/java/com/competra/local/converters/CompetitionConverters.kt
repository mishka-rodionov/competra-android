package com.competra.local.converters

import androidx.room.TypeConverter
import com.competra.domain.models.KindOfSport
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.ByChoiceMode
import com.competra.domain.models.orienteering.OvertimePolicy
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.models.orienteering.TeamOverallScope
import com.competra.domain.models.orienteering.TeamScoring
import com.competra.domain.models.orienteering.TeamScoringMethod

/**
 * Конвертеры типов для хранения объектов соревнований в Room.
 * 
 * Примечание: Конвертеры для LocalDate удалены, так как дата теперь хранится как Long.
 */
class CompetitionConverters {

    @TypeConverter
    fun fromOrienteeringDirection(direction: OrienteeringDirection?): String? {
        return direction?.name
    }

    @TypeConverter
    fun toOrienteeringDirection(value: String?): OrienteeringDirection? {
        return value?.let { OrienteeringDirection.valueOf(it) }
    }

    @TypeConverter
    fun fromKindOfSport(kindOfSport: KindOfSport?): String? {
        return kindOfSport?.name
    }

    @TypeConverter
    fun toKindOfSport(name: String?): KindOfSport? {
        return name?.let { sportName ->
            KindOfSport.all.find { it.name == sportName }
        }
    }

    @TypeConverter
    fun fromPunchingSystem(punchingSystem: PunchingSystem?): String? {
        return punchingSystem?.name
    }

    @TypeConverter
    fun toPunchingSystem(value: String?): PunchingSystem? {
        return value?.let { PunchingSystem.valueOf(it) }
    }

    @TypeConverter
    fun fromOvertimePolicy(policy: OvertimePolicy?): String? {
        return policy?.name
    }

    /** Неизвестное значение (данные с более новой версии) трактуем как умолчание, а не падаем. */
    @TypeConverter
    fun toOvertimePolicy(value: String?): OvertimePolicy? {
        return value?.let { OvertimePolicy.fromString(it) }
    }

    /** Командный зачёт строкой «метод|N|области»: `POINTS|3|MEN,ALL`. */
    @TypeConverter
    fun fromTeamScoring(settings: TeamScoring?): String? {
        return settings?.let {
            "${it.groupMethod.name}|${it.groupCountedResults}|${it.overallScopes.joinToString(",") { scope -> scope.name }}"
        }
    }

    /** Битое значение — зачёта нет, а не падение при чтении соревнования. */
    @TypeConverter
    fun toTeamScoring(value: String?): TeamScoring? {
        val parts = value?.split("|")?.takeIf { it.size == 3 } ?: return null
        return TeamScoring(
            groupMethod = TeamScoringMethod.fromString(parts[0]),
            groupCountedResults = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: TeamScoring.DEFAULT_COUNTED_RESULTS,
            overallScopes = parts[2].split(",").mapNotNull(TeamOverallScope::fromString).toSet()
        )
    }

    @TypeConverter
    fun fromByChoiceMode(mode: ByChoiceMode?): String? {
        return mode?.name
    }

    /** Неизвестное значение (данные с более новой версии) трактуем как умолчание, а не падаем. */
    @TypeConverter
    fun toByChoiceMode(value: String?): ByChoiceMode? {
        return value?.let { ByChoiceMode.fromString(it) }
    }
}
