package com.competra.center.data.creator

import com.competra.domain.models.Coordinates
import com.competra.domain.models.Gender
import com.competra.domain.models.KindOfSport
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.ControlPoint
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.models.orienteering.RegistrationEndMode
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.utils.DateTimeFormat
import java.time.ZoneId

/**
 * Преднабор тестовых данных для быстрой отладки создания соревнования.
 *
 * Используется только в debug-сборке: кнопка «Заполнить тестовыми данными» на экране
 * создания подставляет эти значения, после чего разработчик правит нужное. Заполненное
 * соревнование автоматически помечается как тестовое ([OrienteeringCreatorState.isTest] = true)
 * и не попадает в публичную ленту.
 *
 * Помимо полей соревнования преднабор включает две дистанции с разными наборами
 * контрольных пунктов ([distances]) и две группы — М21 и Ж21 ([groups]) — каждая
 * на своей дистанции.
 */
object TestCompetitionFixtures {

    /**
     * Возвращает [current] State, заполненный осмысленными тестовыми значениями.
     *
     * Сохраняет уже присвоенный [OrienteeringCreatorState.competitionId] (если форму
     * открыли для существующего черновика) и текущую зону. Все timestamp'ы считаются
     * относительно «сейчас», чтобы соревнование всегда было в будущем.
     */
    fun fill(current: OrienteeringCreatorState): OrienteeringCreatorState {
        val zone = runCatching { ZoneId.of(current.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
        val dayMillis = 24L * 60 * 60 * 1000

        val startDate = DateTimeFormat.updateTimeInTimestamp(
            System.currentTimeMillis() + 7 * dayMillis, START_TIME, zone
        ) ?: (System.currentTimeMillis() + 7 * dayMillis)

        val registrationStart = DateTimeFormat.updateTimeInTimestamp(
            System.currentTimeMillis(), REGISTRATION_START_TIME, zone
        ) ?: System.currentTimeMillis()

        return current.copy(
            title = "[ТЕСТ] Соревнование ${System.currentTimeMillis() % 100000}",
            startDate = startDate,
            startTimeStr = START_TIME,
            endDate = null,
            kindOfSport = KindOfSport.Orienteering,
            description = "Тестовое соревнование для отладки. Создано автозаполнением.",
            address = "Москва, Лосиный Остров",
            coordinates = Coordinates(55.8400, 37.7900),
            isCoordinatesSetByUser = true,

            registrationStart = registrationStart,
            registrationStartTimeStr = REGISTRATION_START_TIME,
            registrationStartOnCreate = false,
            registrationEnd = null,
            registrationEndTimeStr = "23:59",
            registrationEndMode = RegistrationEndMode.AT_COMPETITION_START,
            maxParticipants = 100,

            isFeeEnabled = true,
            feeAmount = 500.0,
            feeCurrency = "RUB",
            regulationUrl = "https://example.com/regulation",
            mapUrl = "",
            contactPhone = "+79990000000",
            contactEmail = "test@example.com",
            website = "https://example.com",

            competitionDirection = OrienteeringDirection.FORWARD,
            punchingSystem = PunchingSystem.SPORTIDUINO,
            startTimeMode = StartTimeMode.USER_SET,
            startIntervalSeconds = 60,

            // Заполненное автозаполнением соревнование — всегда тестовое.
            isTest = true
        )
    }

    /**
     * Две тестовые дистанции с разными наборами КП. Создаются с `id = 0` —
     * реальный локальный идентификатор присвоит Room при вставке.
     *
     * @param competitionId UUID соревнования, к которому привязываются дистанции.
     */
    fun distances(competitionId: String): List<Distance> = listOf(
        Distance(
            id = 0L,
            competitionId = competitionId,
            name = "М21 — длинная",
            lengthMeters = 5200,
            climbMeters = 180,
            controlsCount = LONG_COURSE_CONTROLS.size,
            description = "Тестовая длинная дистанция",
            controlPoints = LONG_COURSE_CONTROLS.map { ControlPoint(number = it) },
            finishControlPoint = FINISH_CONTROL,
            startControlPoint = START_CONTROL
        ),
        Distance(
            id = 0L,
            competitionId = competitionId,
            name = "Ж21 — короткая",
            lengthMeters = 3400,
            climbMeters = 110,
            controlsCount = SHORT_COURSE_CONTROLS.size,
            description = "Тестовая короткая дистанция",
            controlPoints = SHORT_COURSE_CONTROLS.map { ControlPoint(number = it) },
            finishControlPoint = FINISH_CONTROL,
            startControlPoint = START_CONTROL
        ),
        Distance(
            id = 0L,
            competitionId = competitionId,
            name = "М17 — короткая",
            lengthMeters = 3400,
            climbMeters = 110,
            controlsCount = SHORT_COURSE_CONTROLS.size,
            description = "Тестовая короткая дистанция",
            controlPoints = SHORT_COURSE_CONTROLS.map { ControlPoint(number = it) },
            finishControlPoint = FINISH_CONTROL,
            startControlPoint = START_CONTROL
        ),
        Distance(
            id = 0L,
            competitionId = competitionId,
            name = "Ж17 — короткая",
            lengthMeters = 2400,
            climbMeters = 90,
            controlsCount = SMALL_SHORT_COURSE_CONTROLS.size,
            description = "Тестовая короткая дистанция",
            controlPoints = SMALL_SHORT_COURSE_CONTROLS.map { ControlPoint(number = it) },
            finishControlPoint = FINISH_CONTROL
        )
    )

    /**
     * Две тестовые группы — М21 и Ж21 — каждая на своей дистанции.
     *
     * @param competitionId UUID соревнования.
     * @param distanceIds Локальные id уже сохранённых дистанций в порядке [distances]:
     *   первый — для М21 (длинная), второй — для Ж21 (короткая).
     */
    fun groups(competitionId: String, distanceIds: List<Long>): List<ParticipantGroup> {
        val maleDistanceId = distanceIds.getOrNull(0) ?: 0L
        val femaleDistanceId = distanceIds.getOrNull(1) ?: maleDistanceId
        val maleJuniorDistanceId = distanceIds.getOrNull(2) ?: femaleDistanceId
        val femaleJuniorDistanceId = distanceIds.getOrNull(3) ?: maleJuniorDistanceId
        return listOf(
            ParticipantGroup(
                groupId = 0L,
                competitionId = competitionId,
                title = "М21",
                gender = Gender.MALE,
                minAge = 21,
                maxAge = null,
                distanceId = maleDistanceId,
                maxParticipants = 50
            ),
            ParticipantGroup(
                groupId = 0L,
                competitionId = competitionId,
                title = "Ж21",
                gender = Gender.FEMALE,
                minAge = 21,
                maxAge = null,
                distanceId = femaleDistanceId,
                maxParticipants = 50
            ),
            ParticipantGroup(
                groupId = 0L,
                competitionId = competitionId,
                title = "М17",
                gender = Gender.MALE,
                minAge = 17,
                maxAge = null,
                distanceId = maleJuniorDistanceId,
                maxParticipants = 50
            ),
            ParticipantGroup(
                groupId = 0L,
                competitionId = competitionId,
                title = "Ж17",
                gender = Gender.FEMALE,
                minAge = 17,
                maxAge = null,
                distanceId = femaleJuniorDistanceId,
                maxParticipants = 50
            )
        )
    }

    private const val START_TIME = "11:00"
    private const val REGISTRATION_START_TIME = "10:00"

    /** Номер финишного КП (станция финиша), общий для тестовых дистанций. */
    private const val FINISH_CONTROL = 45

    /**
     * Номер стартового КП (станция старта) для теста BY_START_STATION — 240 совпадает с
     * зарезервированным протоколом Sportiduino кодом старт-станции (см. [com.competra.nfchelper.nfccard.Config.START_STATION]),
     * так что реальная физическая станция даст отметку с этим же номером.
     */
    private const val START_CONTROL = 240

    /** Набор КП длинной дистанции (М21). */
    private val LONG_COURSE_CONTROLS = listOf(36, 37, 42, 46)

    /** Набор КП короткой дистанции (Ж21) — заведомо отличается от длинной. */
    private val SHORT_COURSE_CONTROLS = listOf(46, 42, 37, 36)
    private val SMALL_SHORT_COURSE_CONTROLS = listOf(46, 37, 36)
}
