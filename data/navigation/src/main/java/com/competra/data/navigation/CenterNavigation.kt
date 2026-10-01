package com.competra.data.navigation

import androidx.navigation.NavOptionsBuilder
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Навигация для раздела "Центр соревнований".
 * Содержит маршруты для управления событиями, создания соревнований и работы с участниками.
 */
@Serializable
sealed class CenterNavigation: BaseNavigation {

    @Transient
    @Contextual
    override var navOptionsBuilder: (NavOptionsBuilder.() -> Unit)? = null

    @Serializable
    data object CenterBaseRoute: CenterNavigation()
    @Serializable
    data object CenterRoute: CenterNavigation()
    @Serializable
    data object KindOfSportRoute: CenterNavigation()
    
    // Новые маршруты для пошагового создания соревнования
    @Serializable
    data class CommonCompetitionFieldRoute(val competitionId: String? = null): CenterNavigation()
    @Serializable
    data class RegistrationCompetitionFieldRoute(val competitionId: String): CenterNavigation()
    @Serializable
    data class OrganizatorCompetitionFieldRoute(val competitionId: String): CenterNavigation()
    @Serializable
    data class CreateDistanceRoute(val competitionId: String): CenterNavigation()
    @Serializable
    data class CreateParticipantGroupRoute(val competitionId: String): CenterNavigation()

    @Serializable
    data object OrienteeringEventControlRoute: CenterNavigation()
    @Serializable
    data object OrientReadCardRoute: CenterNavigation()
    @Serializable
    data object ParticipantList: CenterNavigation()
    @Serializable
    data object DrawParticipants: CenterNavigation()
    @Serializable
    data object StartGridRoute: CenterNavigation()
    @Serializable
    data object ParticipantResults: CenterNavigation()
    @Serializable
    data class GetOrienteeringChipRoute(val competitionId: String): CenterNavigation()

    /**
     * Запись/очистка чипов и настройка станций мастер-картами.
     *
     * @param tab имя вкладки, открываемой первой (`CLEAR`, `WRITE_NUMBER`, `STATION`); `null` — по умолчанию.
     */
    @Serializable
    data class WriteChipRoute(val tab: String? = null) : CenterNavigation()

    /** Хаб «Станции и чипы» — работа с оборудованием Sportiduino вне соревнования. */
    @Serializable
    data object StationToolsRoute : CenterNavigation()

    /** Чтение и проверка чипа без привязки к соревнованию. */
    @Serializable
    data object ChipInspectRoute : CenterNavigation()

    @Serializable
    data class ParticipantSplitsRoute(val participantId: String, val competitionId: String): CenterNavigation()

    @Serializable
    data class GroupSplitsTableRoute(val groupId: Long, val competitionId: String): CenterNavigation()

    @Serializable
    data class GroupRaceGraphRoute(val groupId: Long, val competitionId: String): CenterNavigation()

    @Serializable
    data class GroupScoreGraphRoute(val groupId: Long, val competitionId: String): CenterNavigation()

    @Serializable
    data class MapPickerRoute(val initLatE6: Long = 0L, val initLonE6: Long = 0L): CenterNavigation()
}
