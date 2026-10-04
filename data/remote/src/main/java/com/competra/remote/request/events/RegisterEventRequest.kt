package com.competra.remote.request.events

import com.google.gson.annotations.SerializedName

/**
 * Запрос на регистрацию пользователя в группу соревнования.
 *
 * @property competitionId Идентификатор соревнования (remoteId).
 * @property groupId Идентификатор группы.
 * @property firstName Имя пользователя.
 * @property lastName Фамилия пользователя.
 * @property commandName Подпись команды для протокола.
 * @property teamId Клубная команда пользователя (сервер проверяет членство).
 */
data class RegisterEventRequest(
    @SerializedName("competitionId")
    val competitionId: String,
    @SerializedName("groupId")
    val groupId: String,
    @SerializedName("firstName")
    val firstName: String,
    @SerializedName("lastName")
    val lastName: String,
    @SerializedName("commandName")
    val commandName: String? = null,
    @SerializedName("teamId")
    val teamId: String? = null
)
