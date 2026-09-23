package com.competra.domain.models.orienteering

import com.competra.domain.models.Competition

/**
 * Представляет модель соревнований по спортивному ориентированию
 *
 * @property competitionId Глобально-уникальный клиентский UUID соревнования (единый id на всех платформах)
 * @property competition Базовая информация о соревновании
 * @property direction Направление (заданный порядок, по выбору и т.д.)
 * @property punchingSystem Система отметки
 * @property startTimeMode Режим определения времени старта.
 * Строгий - конкретное время,
 * пользовательское - время отсчета до старта,
 * старт по отметке - время старта участника определяется по его отметке на стартовой станции
 * @property countdownTimer Время отсчета перед стартом (в минутах), если [startTimeMode] равен [StartTimeMode.USER_SET]
 * @property startTime Фактическое время старта соревнования (timestamp)
 * @property controlTimeMinutes Контрольное время соревнования в минутах — умолчание для групп
 * без своего КВ (группа переопределяет его через ParticipantGroup.timeLimitMinutes)
 * @property overtimePolicy Что делать с результатами, превысившими КВ
 */
data class OrienteeringCompetition(
    val competitionId: String,
    val competition: Competition,
    val direction: OrienteeringDirection,
    val punchingSystem: PunchingSystem,
    val startTimeMode: StartTimeMode,
    val countdownTimer: Int? = null,
    val startTime: Long? = null,
    val startIntervalSeconds: Int? = null,
    val controlTimeMinutes: Int? = null,
    val overtimePolicy: OvertimePolicy = OvertimePolicy.DEFAULT,
    val isDrawConducted: Boolean = false,
    val serverUpdatedAt: Long? = null
)
