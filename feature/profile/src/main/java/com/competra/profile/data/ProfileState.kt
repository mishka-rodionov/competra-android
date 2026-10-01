package com.competra.profile.data

import com.competra.domain.models.user.User
import com.competra.ui.BaseState

data class ProfileState(
    val user: User? = null,
    val showDeleteAccountConfirm: Boolean = false,
    /** Подсказки по имени среди вручную внесённых участников — см. экран «Мои результаты в протоколах». */
    val linkSuggestionsCount: Int = 0,
    /** Заявки на привязку результатов, ожидающие решения организатора. */
    val pendingLinkRequestsCount: Int = 0,
) : BaseState