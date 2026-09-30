package com.competra.domain.models.orienteering

import com.competra.domain.models.Coordinates

/**
 * Модель дистанции соревнований по ориентированию.
 * 
 * @property id Локальный идентификатор дистанции.
 * @property remoteId Идентификатор дистанции на сервере.
 * @property competitionId Идентификатор соревнования, к которому относится дистанция.
 * @property name Название дистанции (например, "Длинная").
 * @property lengthMeters Протяженность в метрах.
 * @property climbMeters Набор высоты в метрах.
 * @property controlsCount Количество контрольных пунктов.
 * @property description Описание параметров дистанции.
 * @property isSynced Флаг синхронизации с сервером.
 * @property lastModified Время последнего изменения.
 * @property isDeleted Флаг пометки на удаление.
 * @property controlPoints Список контрольных пунктов на дистанции.
 * @property finishControlPoint Номер финишного контрольного пункта. Отметка этого КП в чипе используется как
 *                              время финиша участника. Для электронных систем отметки (SPORTIDUINO, SPORTIDENT,
 *                              SFR) поле обязательно к заполнению в UI; для бумажных/механических систем
 *                              может быть `null`.
 * @property startControlPoint Номер стартового контрольного пункта (отдельная физическая старт-станция).
 *                              Отметка этого КП в чипе используется как реальное время старта участника —
 *                              актуально только при [StartTimeMode.BY_START_STATION], где обязательно к
 *                              заполнению в UI; для остальных режимов не используется и может быть `null`.
 * @property startPosition Координаты старта (WGS84) из IOF XML — по ним считается длина первого перегона
 *                      (темп участника до первого КП). `null`, если дистанция создана вручную или
 *                      импортирована до появления поля.
 * @property finishPosition Координаты финиша (WGS84) из IOF XML — длина перегона на финишную станцию.
 * @property map Геопривязанная растровая карта дистанции или `null`, если она не прикреплена. Прикрепляется
 *               через веб, на Android только читается с сервера.
 */
data class Distance(
    val id: Long = 0,
    val remoteId: Long? = null,
    val competitionId: String,
    val name: String? = null,
    val lengthMeters: Int,
    val climbMeters: Int,
    val controlsCount: Int,
    val description: String? = null,
    val isSynced: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val serverUpdatedAt: Long? = null,
    val syncError: String? = null,
    val controlPoints: List<ControlPoint>,
    val finishControlPoint: Int? = null,
    val startControlPoint: Int? = null,
    val startPosition: Coordinates? = null,
    val finishPosition: Coordinates? = null,
    val map: DistanceMap? = null
)

/**
 * Полная ожидаемая последовательность отметок дистанции: [Distance.controlPoints] по порядку
 * плюс синтетический финишный пункт, если задан [Distance.finishControlPoint]. Позиционно
 * совпадает с валидными сплитами участника (см. `checkControlPointOrderPro` в feature:center) —
 * это же позиционное соответствие используется при расчёте длины перегона между КП. Финишный
 * пункт несёт координаты [Distance.finishPosition], если они известны.
 */
fun Distance.expectedSequence(): List<ControlPoint> =
    finishControlPoint?.let {
        controlPoints + ControlPoint(
            number = it,
            role = ControlPointRole.FINISH,
            latitude = finishPosition?.latitude,
            longitude = finishPosition?.longitude
        )
    } ?: controlPoints

/**
 * Точка старта как синтетический КП — начало первого перегона при расчёте его длины. `null`, если
 * координаты старта неизвестны. В последовательность отметок не входит (см. [expectedSequence]).
 */
fun Distance.startPoint(): ControlPoint? = startPosition?.let {
    ControlPoint(number = startControlPoint ?: 0, role = ControlPointRole.START, latitude = it.latitude, longitude = it.longitude)
}

/** Собирает [Coordinates] из плоских полей (DTO сервера, колонки Room); `null`, если хотя бы одного нет. */
fun coordinatesOrNull(latitude: Double?, longitude: Double?): Coordinates? =
    if (latitude != null && longitude != null) Coordinates(latitude, longitude) else null
