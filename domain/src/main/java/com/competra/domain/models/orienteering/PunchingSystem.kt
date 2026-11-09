package com.competra.domain.models.orienteering

/**
 * Система отметки соревнования.
 *
 * У электронных систем есть стандартные коды стартовой и финишной станций — они используются,
 * когда у дистанции не задан стартовый/финишный КП или у группы нет дистанции (например, в режиме
 * [OrienteeringCompetition.isFinishRegistration]).
 *
 * **При добавлении новой системы отметки задай ей [defaultStartStationCode] и [defaultFinishStationCode].**
 *
 * @property defaultStartStationCode Код стартовой станции по умолчанию; `null` — не задан.
 * @property defaultFinishStationCode Код финишной станции по умолчанию; `null` — не задан.
 */
enum class PunchingSystem(
    val defaultStartStationCode: Int? = null,
    val defaultFinishStationCode: Int? = null,
) {
    PENCIL,
    PUNCH,
    SPORTIDUINO(defaultStartStationCode = 240, defaultFinishStationCode = 245),
    // TODO: задать стандартные коды стартовой/финишной станций SFR и SPORTIDENT.
    SFR,
    SPORTIDENT
}
