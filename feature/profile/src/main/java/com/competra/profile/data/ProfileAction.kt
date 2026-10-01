package com.competra.profile.data

/**
 * Действия пользователя на экране профиля.
 */
sealed class ProfileAction {

    /** Переход к экрану авторизации. */
    data object ToAuth: ProfileAction()
    
    /** Переход к экрану регистрации. */
    data object ToRegister: ProfileAction()

    /** Переход к экрану редактирования профиля. */
    data object ToProfileEditor: ProfileAction()

    /** Переход на экран «Предстоящие старты». */
    data object ToUserRegistrations: ProfileAction()

    /** Переход на экран «Мои результаты в протоколах» — привязка вручную внесённых результатов. */
    data object ToResultLinks: ProfileAction()

    /** Выход из аккаунта. */
    data object Logout: ProfileAction()

    /** Повторный показ онбординга. */
    data object ShowOnboarding: ProfileAction()

    /** Переход к настройкам push-уведомлений. */
    data object ToPushPreferences: ProfileAction()

    /** Переход на экран «О приложении». */
    data object ToAboutApp: ProfileAction()

    /** Пользователь нажал «Удалить аккаунт» — открыть диалог подтверждения. */
    data object OpenDeleteAccountConfirm: ProfileAction()

    /** Диалог подтверждения удаления аккаунта закрыт без подтверждения. */
    data object CloseDeleteAccountConfirm: ProfileAction()

    /** Подтверждено безвозвратное удаление аккаунта. */
    data object DeleteAccount: ProfileAction()

}
