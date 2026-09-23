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
