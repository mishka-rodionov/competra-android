package com.competra.domain.models.orienteering

/**
 * Считывание незнакомого чипа в режиме «Регистрация на финише»
 * ([OrienteeringCompetition.isFinishRegistration]), ещё не привязанное к участнику.
 *
 * Хранится только на телефоне (очередь организатора на финише) и удаляется после привязки.
 *
 * @property competitionId Соревнование.
 * @property chipNumber Номер чипа (в соревновании не повторяется).
 * @property readAt Время считывания (мс) — используется для «Засчитать по времени считывания».
 * @property clearTime Время очистки чипа (мс).
 * @property splits Отметки с чипа.
 */
data class UnassignedChipRead(
    val competitionId: String,
    val chipNumber: Int,
    val readAt: Long,
    val clearTime: Long,
    val splits: List<SplitTime>,
)

/** Превращает считывание из очереди обратно в данные чипа для подсчёта результата. */
fun UnassignedChipRead.toRawResult(): ReadChipData.RawResult =
    ReadChipData.RawResult(chipNumber = chipNumber, clearTime = clearTime, splits = splits)
