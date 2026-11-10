package com.competra.domain.models.orienteering

import com.competra.domain.models.ResultStatus

/**
 * Модель результата участника в соревнованиях по ориентированию.
 *
 * @property id Уникальный идентификатор.
 * @property competitionId Идентификатор соревнования.
 * @property groupId Идентификатор группы.
 * @property participantId Идентификатор участника.
 * @property startTime Время старта (мс).
 * @property finishTime Время финиша (мс).
 * @property totalTime Общее время (в секундах).
 * @property rank Место.
 * @property status Статус результата (FINISHED, DSQ, DNS, DNF).
 * @property penaltyTime Штрафное время в СЕКУНДАХ, добавляется к totalTime (формат FORWARD/MARKING).
 * @property totalScore Сумма баллов взятых КП (формат BY_CHOICE); null для FORWARD/MARKING.
 * @property scorePenalty Штраф в ОЧКАХ за опоздание сверх лимита (формат BY_CHOICE), уже вычтен из totalScore.
 * @property splits Сплиты (отметки на КП).
 * @property isEditable Флаг возможности редактирования результата.
 * @property isEdited Флаг того, что результат был изменен вручную.
 * @property isStatusManual Статус выставлен организатором вручную («Снят» / «Засчитать») — автоматический
 *                           пересчёт его не перетирает (только в режиме «Регистрация на финише»).
 * @property readAt Время считывания чипа на финише (мс); `null` — неизвестно.
 */
data class OrienteeringResult(
    val id: Long = 0,
    val competitionId: String,
    val groupId: Long,
    val participantId: String,
    val startTime: Long? = null,
    val finishTime: Long? = null,
    val totalTime: Long? = null, // в секундах
    val rank: Int? = null,
    val status: ResultStatus, // FINISHED, DSQ, DNS, DNF
    val penaltyTime: Long = 0, // Штрафное время
    val totalScore: Int? = null, // Баллы (BY_CHOICE), не путать с penaltyTime/totalTime (сек)
    val scorePenalty: Int = 0, // Штраф в очках (BY_CHOICE)
    val splits: List<SplitTime>? = null, // Можно хранить как JSON или отдельной таблицей
    val isEditable: Boolean = true,
    val isEdited: Boolean = false,
    val isStatusManual: Boolean = false,
    val readAt: Long? = null,
    val isSynced: Boolean = false,
    val remoteId: String? = null,
    val isDeleted: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val serverUpdatedAt: Long? = null,
    val syncError: String? = null
)

/**
 * Место для отображения: только у FINISHED и только положительное. Снятым/не финишировавшим
 * проставляется rank = -1 — такие участники вне зачёта, место им не показываем (статус и так виден).
 * Сам [OrienteeringResult.rank] не трогаем — он синхронизируется с сервером как есть.
 */
val OrienteeringResult.displayPlace: Int?
    get() = rank?.takeIf { it > 0 && status == ResultStatus.FINISHED }

/** Текст графы «Место» у финишировавшего участника вне конкурса. */
const val OUT_OF_COMPETITION_PLACE = "ВК"

/**
 * Текст графы «Место» в протоколах: место, «ВК» у финишировавшего участника вне конкурса
 * ([OrienteeringParticipant.isOutOfCompetition]), иначе null — вместо места показывают статус.
 */
fun placeLabel(participant: OrienteeringParticipant, result: OrienteeringResult?): String? = when {
    result == null -> null
    participant.isOutOfCompetition -> OUT_OF_COMPETITION_PLACE.takeIf { result.status == ResultStatus.FINISHED }
    else -> result.displayPlace?.toString()
}

/** Текст графы «Место» для участника с результатом — см. [placeLabel]. */
val ParticipantWithResult.placeLabel: String?
    get() = placeLabel(participant, result)
