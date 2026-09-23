package com.competra.local.converters

import androidx.room.TypeConverter
import com.competra.domain.models.KindOfSport
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.OvertimePolicy
import com.competra.domain.models.orienteering.PunchingSystem

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
}
