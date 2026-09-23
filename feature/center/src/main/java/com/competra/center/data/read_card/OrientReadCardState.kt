package com.competra.center.data.read_card

import com.competra.domain.models.orienteering.ControlPoint
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.OvertimePolicy
import com.competra.domain.models.orienteering.SplitTime
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.ui.BaseState

data class OrientReadCardState(
    val participant: OrienteeringParticipant? = null,
    val participantResult: OrienteeringResult? = null,
    val rawSplits: List<SplitTime>? = null,
    val isCompetitionFinished: Boolean = false,
    /** Формат соревнования (FORWARD/BY_CHOICE/MARKING) — определяет алгоритм проверки отметок. */
    val competitionDirection: OrienteeringDirection = OrienteeringDirection.FORWARD,
    /**
     * Режим определения времени старта соревнования. При [StartTimeMode.BY_START_STATION]
     * реальное время старта участника берётся из отметки на стартовом КП его чипа
     * (см. [com.competra.center.presentation.read_card.OrientReadCardViewModel]), а не из
     * [OrienteeringParticipant.startTime], назначенного заранее.
     */
    val startTimeMode: StartTimeMode = StartTimeMode.STRICT,
    /** КВ соревнования в минутах — умолчание для групп без своего значения. */
    val competitionControlTimeMinutes: Int? = null,
    /** Политика применения КВ, выбранная организатором для всего соревнования. */
    val overtimePolicy: OvertimePolicy = OvertimePolicy.DEFAULT,
    val editingSplitIndex: Int? = null,
    val groupRank: Int? = null,
    val groupTotalFinished: Int = 0,
    /** Порядковый список номеров КП дистанции участника (из настроек дистанции). */
    val expectedCpNumbers: List<Int> = emptyList(),
    /**
     * Те же КП, что и [expectedCpNumbers], но полными объектами — с координатами, если дистанция
     * импортирована из геопривязанной карты (IOF XML). Нужны для расчёта темпа на перегоне.
     */
    val expectedControlPoints: List<ControlPoint> = emptyList(),
    /**
     * Номер стартового КП дистанции (см. [com.competra.domain.models.orienteering.Distance.startControlPoint]),
     * если задан. В [expectedControlPoints] не входит — он не часть проверяемой организатором
     * последовательности отметок, только источник реального времени старта при
     * [StartTimeMode.BY_START_STATION]. Нужен экрану отдельно, чтобы не подсвечивать отметку
     * старт-станции как «лишнюю».
     */
    val startControlPoint: Int? = null,
    /** true — DSQ-результат показан организатору, ожидает явного сохранения. */
    val isPendingSave: Boolean = false,
    /**
     * Причина текущего статуса результата (например, почему участник дисквалифицирован),
     * из [com.competra.center.data.read_card.CheckResult.message]. Показывается организатору
     * на экране сканирования — без неё DSQ с нулевым временем выглядит необъяснимым сбоем.
     */
    val statusMessage: String? = null,
) : BaseState
