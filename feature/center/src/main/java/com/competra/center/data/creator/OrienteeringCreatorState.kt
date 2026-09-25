package com.competra.center.data.creator

import android.net.Uri
import com.competra.domain.models.Competition
import com.competra.domain.models.Coordinates
import com.competra.domain.models.CropRect
import com.competra.domain.models.KindOfSport
import com.competra.domain.models.club.Club
import com.competra.domain.models.orienteering.*
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.models.orienteering.ResultsStatus
import com.competra.ui.BaseState
import java.time.ZoneId

/**
 * Состояние процесса создания соревнования.
 * 
 * @property competitionId Идентификатор соревнования (локальный).
 * @property title Название.
 * @property startDate Дата начала.
 * @property startTimeStr Время начала (строка HH:mm).
 * @property endDate Дата окончания.
 * @property kindOfSport Вид спорта.
 * @property description Описание.
 * @property address Адрес.
 * @property coordinates Координаты.
 * @property registrationStart Начало регистрации.
 * @property registrationEnd Конец регистрации.
 * @property maxParticipants Лимит участников.
 * @property isFeeEnabled Флаг активности поля взноса.
 * @property feeAmount Сумма взноса.
 * @property feeCurrency Валюта взноса.
 * @property regulationUrl Ссылка на регламент.
 * @property mapUrl Ссылка на карту.
 * @property contactPhone Телефон.
 * @property contactEmail Почта.
 * @property website Сайт.
 * @property competitionDirection Направление.
 * @property punchingSystem Система отметки.
 * @property startTimeMode Режим старта.
 * @property countdownTimer Таймер.
 * @property editGroupIndex Индекс редактируемой группы.
 * @property isShowGroupCreateDialog Флаг отображения диалога создания группы.
 * @property editDistanceIndex Индекс редактируемой дистанции.
 * @property isShowDistanceCreateDialog Флаг отображения диалога создания дистанции.
 * @property errors Ошибки валидации.
 * @property distances Список дистанций.
 * @property participantGroups Список групп.
 * @property stages Список этапов (для многодневных соревнований).
 * @property isLoading Флаг загрузки.
 * @property error Сообщение об ошибке.
 */
data class OrienteeringCreatorState(
    val competitionId: String? = null,
    val title: String = "",
    val startDate: Long = System.currentTimeMillis(),
    val startTimeStr: String = "10:00",
    /** IANA-идентификатор часового пояса соревнования. По умолчанию — зона устройства. */
    val timeZoneId: String = ZoneId.systemDefault().id,
    val endDate: Long? = null,
    val kindOfSport: KindOfSport = KindOfSport.Orienteering,
    val description: String = "",
    val address: String = "",
    val coordinates: Coordinates = Coordinates(0.0, 0.0),
    /** true, если координаты были выбраны пользователем вручную через карту. */
    val isCoordinatesSetByUser: Boolean = false,
    
    val registrationStart: Long? = null,
    val registrationStartTimeStr: String = "10:00",
    val registrationStartOnCreate: Boolean = false,
    val registrationEnd: Long? = null,
    val registrationEndTimeStr: String = "23:59",
    val registrationEndMode: RegistrationEndMode = RegistrationEndMode.AT_COMPETITION_START,
    /**
     * true, если при загрузке регистрация оказалась досрочно закрыта организатором — тогда в форме
     * показывается вариант «Регистрация завершена», чтобы к нему можно было вернуться.
     */
    val isRegistrationClosedByOrganizer: Boolean = false,
    val maxParticipants: Int? = null,
    
    val isFeeEnabled: Boolean = false,
    val feeAmount: Double? = null,
    val feeCurrency: String = "RUB",
    val imageUrl: String? = null,
    val imageCropRect: CropRect? = null,
    /** Uri выбранной, но ещё не подтверждённой в кроппере обложки. */
    val pendingCoverCropUri: Uri? = null,
    val regulationUrl: String = "",

    val mapUrl: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val website: String = "",

    val competitionDirection: OrienteeringDirection = OrienteeringDirection.FORWARD,
    val punchingSystem: PunchingSystem = PunchingSystem.SPORTIDUINO,
    val startTimeMode: StartTimeMode = StartTimeMode.STRICT,
    val countdownTimer: Int? = null,
    val startIntervalSeconds: Int? = null,
    /** Контрольное время соревнования в минутах (null — не задано); умолчание для групп. */
    val controlTimeMinutes: Int? = null,
    /** Что делать с результатами, превысившими КВ. */
    val overtimePolicy: OvertimePolicy = OvertimePolicy.DEFAULT,

    val editGroupIndex: Int = -1,
    val isShowGroupCreateDialog: Boolean = false,
    val editDistanceIndex: Int = -1,
    val isShowDistanceCreateDialog: Boolean = false,
    val errors: OrienteeringCreatorErrors = OrienteeringCreatorErrors(),
    
    val distances: List<Distance> = emptyList(),
    val participantGroups: List<ParticipantGroup> = emptyList(),
    val stages: List<Stage> = emptyList(),
    
    val isLoading: Boolean = false,
    val error: String? = null,

    /** Тестовое соревнование (только debug-сборка): скрыто из публичной ленты, видно лишь владельцу. */
    val isTest: Boolean = false,

    /** Опциональный клуб-организатор. Задать может FOUNDER/ADMIN клуба из [myClubs]. */
    val organizingClubId: String? = null,
    /** Клубы текущего пользователя с ролью FOUNDER/ADMIN — кандидаты для выбора клуба-организатора. */
    val myClubs: List<Club> = emptyList()
) : BaseState {
    
    fun toOrienteeringCompetition(userId: String?): OrienteeringCompetition {
        return OrienteeringCompetition(
            competitionId = competitionId ?: java.util.UUID.randomUUID().toString(),
            competition = Competition(
                title = title,
                startDate = startDate,
                endDate = endDate,
                kindOfSport = kindOfSport,
                description = description,
                address = address,
                mainOrganizerId = userId,
                coordinates = coordinates,
                status = CompetitionStatus.DRAFT,
                registrationStart = registrationStart,
                registrationEnd = registrationEnd,
                maxParticipants = maxParticipants,
                feeAmount = if (isFeeEnabled) feeAmount else null,
                feeCurrency = feeCurrency,
                imageUrl = imageUrl,
                imageCropRect = imageCropRect,
                regulationUrl = regulationUrl,
                mapUrl = mapUrl,
                contactPhone = contactPhone,
                contactEmail = contactEmail,
                website = website,
                resultsStatus = ResultsStatus.NOT_PUBLISHED,
                timeZoneId = timeZoneId,
                isTest = isTest,
                organizingClubId = organizingClubId
            ),
            direction = competitionDirection,
            punchingSystem = punchingSystem,
            startTimeMode = startTimeMode,
            countdownTimer = countdownTimer,
            startIntervalSeconds = startIntervalSeconds,
            controlTimeMinutes = controlTimeMinutes,
            overtimePolicy = overtimePolicy
        )
    }
}
