package com.competra.center.data.start_grid

import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.ui.BaseState

/**
 * Стартовая минута (slot) — группа участников с одинаковым стартовым временем.
 *
 * @property startTime Абсолютное стартовое время минуты (timestamp, мс).
 * @property participants Участники, стартующие в эту минуту, отсортированы по стартовому номеру.
 */
data class StartSlot(
    val startTime: Long,
    val participants: List<OrienteeringParticipant>
)

/**
 * Состояние экрана «Стартовая решётка».
 *
 * Каждая стартовая минута — отдельная страница горизонтального pager'а. Экран помогает
 * судье на старте видеть, кто стартует сейчас и кто готовится стартовать дальше.
 *
 * @property slots Список стартовых минут, отсортирован по возрастанию стартового времени.
 * @property competition Данные текущего соревнования (для секундомера и режима старта).
 * @property isCompetitionRunning Запущено ли соревнование (status == IN_PROGRESS).
 * @property stopwatchMillis Прошедшее время соревнования в мс, обновляется каждые 16 мс.
 * @property currentSlotIndex Индекс ближайшей стартующей минуты (slot с startTime >= now).
 * @property searchQuery Введённый стартовый номер для перехода на нужную минуту.
 * @property zeroTimeMs «Нулевое» время соревнования (timestamp, мс) — от него отсчитывается
 *   минута старта в шапке страницы; null, пока данные не загружены.
 */
data class StartGridState(
    val slots: List<StartSlot> = emptyList(),
    val competition: OrienteeringCompetition? = null,
    val isCompetitionRunning: Boolean = false,
    val stopwatchMillis: Long = 0L,
    val currentSlotIndex: Int = 0,
    val searchQuery: String = "",
    val zeroTimeMs: Long? = null
) : BaseState
