package com.competra.domain.models

enum class ResultStatus {
    REGISTERED, STARTED, FINISHED, DSQ, DNS, DNF,

    /**
     * Превышено контрольное время (КВ). Отдельный статус, а не DSQ: снятие за КВ обратимо
     * (организатор может сменить политику на [com.competra.domain.models.orienteering.OvertimePolicy.IGNORE]
     * или увеличить КВ), и в протоколе видна причина. Аналог OverTime в стандарте IOF.
     */
    OVERTIME
}

/**
 * true, если участнику с таким статусом результата (null — результата ещё нет) можно вручную
 * поставить «Не стартовал» (DNS). Участник со стартом, финишем или снятием уже стартовал —
 * для него DNS некорректен. DNF допускается: при завершении соревнования его автоматически
 * получают все участники без результата, в т.ч. неявившиеся.
 */
val ResultStatus?.canBeMarkedDns: Boolean
    get() = this == null || this == ResultStatus.REGISTERED || this == ResultStatus.DNF
