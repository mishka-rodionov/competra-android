package com.competra.analytics

/**
 * Типобезопасный словарь продуктовых событий приложения.
 *
 * Каждый кейс — отдельное событие со своим именем и параметрами.
 * Имя события в AppMetrica совпадает с `eventName`, параметры передаются
 * картой `params` (snake_case-ключи).
 */
sealed class AnalyticsEvent(
    val eventName: String,
    val params: Map<String, Any?> = emptyMap(),
) {

    // region Auth / Registration

    /** Пользователь запросил отправку кода авторизации (param: домен email). */
    class AuthLoginRequested(emailDomain: String) :
        AnalyticsEvent("auth_login_requested", mapOf("email_domain" to emailDomain))

    /** Пользователь отправил полученный код на проверку. */
    data object AuthCodeSubmitted : AnalyticsEvent("auth_code_submitted")

    /** Успешная авторизация. */
    data object AuthLoginSuccess : AnalyticsEvent("auth_login_success")

    enum class AuthFailureReason { INVALID_CODE, NETWORK, SERVER, UNKNOWN }

    /** Авторизация завершилась ошибкой. */
    class AuthLoginFailed(reason: AuthFailureReason) :
        AnalyticsEvent("auth_login_failed", mapOf("reason" to reason.name.lowercase()))

    /** Пользователь отправил форму регистрации. */
    data object RegistrationSubmitted : AnalyticsEvent("registration_submitted")

    /** Регистрация завершилась успехом. */
    data object RegistrationSuccess : AnalyticsEvent("registration_success")

    /** Пользователь вышел из аккаунта. */
    data object Logout : AnalyticsEvent("logout")

    // endregion

    // region Events (просмотр / участие)

    enum class EventSource { LIST, PUSH, DEEPLINK }

    /** Открыта карточка соревнования. */
    class EventOpened(eventId: String, source: EventSource) : AnalyticsEvent(
        "event_opened",
        mapOf("event_id" to eventId, "source" to source.name.lowercase()),
    )

    /** Применён фильтр в списке соревнований. */
    class EventFilterApplied(filters: Map<String, Any?>) :
        AnalyticsEvent("event_filter_applied", filters)

    /** Нажата кнопка «зарегистрироваться» в карточке соревнования. */
    class EventRegisterClicked(eventId: String) :
        AnalyticsEvent("event_register_clicked", mapOf("event_id" to eventId))

    /** Открыт экран live-результатов. */
    class EventLiveResultsOpened(eventId: String) :
        AnalyticsEvent("event_live_results_opened", mapOf("event_id" to eventId))

    // endregion

    // region Live track (онлайн-трекинг бегуна)

    /** Бегун включил онлайн-трекинг (или возобновил уже идущую на сервере сессию). */
    class LiveTrackStarted(competitionId: String, isResumed: Boolean) : AnalyticsEvent(
        "live_track_started",
        mapOf("competition_id" to competitionId, "is_resumed" to isResumed),
    )

    /** Зритель открыл онлайн-треки соревнования (кнопка на деталях события). */
    class EventLiveTracksOpened(eventId: String) :
        AnalyticsEvent("event_live_tracks_opened", mapOf("event_id" to eventId))

    /** В каком режиме открыта карта треков: пока идут участники или архив после финиша. */
    enum class LiveTrackMapMode { LIVE, ARCHIVE }

    /** Зритель открыл карту треков дистанции. */
    class LiveTrackMapOpened(competitionId: String, distanceId: Long, mode: LiveTrackMapMode) : AnalyticsEvent(
        "live_track_map_opened",
        mapOf("competition_id" to competitionId, "distance_id" to distanceId, "mode" to mode.name.lowercase()),
    )

    /** Почему закончилась запись трека. */
    enum class LiveTrackStopReason { MANUAL, RESULT_SAVED, CONTROL_TIME, INACTIVITY, SESSION_LOST }

    /** Запись онлайн-трека закончилась (бегун остановил, сервер закрыл по результату/таймауту). */
    class LiveTrackStopped(competitionId: String, reason: LiveTrackStopReason, durationMin: Int) : AnalyticsEvent(
        "live_track_stopped",
        mapOf("competition_id" to competitionId, "reason" to reason.name.lowercase(), "duration_min" to durationMin),
    )

    // endregion

    // region Create competition (центр)

    /** Начато создание соревнования. */
    class CreateCompetitionStarted(kindOfSport: String) :
        AnalyticsEvent("create_competition_started", mapOf("kind_of_sport" to kindOfSport))

    enum class CreateCompetitionStep { COMMON, REGISTRATION, ORGANIZATOR, DISTANCE, GROUPS }

    /** Завершён очередной шаг мастера создания. */
    class CreateCompetitionStepCompleted(step: CreateCompetitionStep) : AnalyticsEvent(
        "create_competition_step_completed",
        mapOf("step" to step.name.lowercase()),
    )

    /** Мастер создания соревнования прошёл до конца. */
    class CreateCompetitionFinished(competitionId: String, kindOfSport: String) : AnalyticsEvent(
        "create_competition_finished",
        mapOf("competition_id" to competitionId, "kind_of_sport" to kindOfSport),
    )

    /** Пользователь подтвердил удаление соревнования. */
    class CompetitionDeleted(competitionId: String) :
        AnalyticsEvent("competition_deleted", mapOf("competition_id" to competitionId))

    /** Организатор подтвердил досрочное завершение регистрации на соревнование. */
    class RegistrationCloseClicked(competitionId: String) :
        AnalyticsEvent("registration_close_clicked", mapOf("competition_id" to competitionId))

    /** Открыт экран «Стартовая решётка» (помощь судье на старте). */
    class CenterStartGridOpened(competitionId: String) :
        AnalyticsEvent("center_start_grid_opened", mapOf("competition_id" to competitionId))

    // endregion

    // region NFC / чипы

    enum class NfcCardType { MIFARE_CLASSIC, MIFARE_ULTRALIGHT, PARTICIPANT, MASTER, UNKNOWN }

    /** Зафиксирована попытка чтения NFC-чипа. */
    class NfcChipReadAttempted(cardType: NfcCardType) : AnalyticsEvent(
        "nfc_chip_read_attempted",
        mapOf("card_type" to cardType.name.lowercase()),
    )

    /** Успешное чтение чипа. */
    data object NfcChipReadSuccess : AnalyticsEvent("nfc_chip_read_success")

    /** Чтение чипа завершилось ошибкой. */
    class NfcChipReadFailed(reason: String) :
        AnalyticsEvent("nfc_chip_read_failed", mapOf("reason" to reason))

    /** Чип привязан к участнику. */
    data object NfcChipAssigned : AnalyticsEvent("nfc_chip_assigned")

    enum class ParticipantAddMethod { MANUAL, NFC, IMPORT }

    /** Добавлен участник в соревнование. */
    class ParticipantAdded(method: ParticipantAddMethod) :
        AnalyticsEvent("participant_added", mapOf("method" to method.name.lowercase()))

    /** Режим проведённой жеребьёвки. */
    enum class DrawMode { GENERAL, GROUP, DISTANCE }

    /**
     * Выполнена жеребьёвка.
     *
     * @param participantsCount число распределённых участников.
     * @param mode режим жеребьёвки.
     * @param corridors число стартовых коридоров (только для [DrawMode.DISTANCE], иначе `null`).
     * @param gap минимальный зазор между стартами одной дистанции в интервалах
     *   (только для [DrawMode.DISTANCE], иначе `null`).
     */
    class ParticipantDrawn(
        participantsCount: Int,
        mode: DrawMode,
        corridors: Int? = null,
        gap: Int? = null,
    ) : AnalyticsEvent(
        "participant_drawn",
        mapOf(
            "participants_count" to participantsCount,
            "mode" to mode.name.lowercase(),
            "corridors" to corridors,
            "gap" to gap,
        ),
    )

    // endregion

    // region Results

    /** Просмотр результатов соревнования. */
    class ResultsViewed(eventId: String) :
        AnalyticsEvent("results_viewed", mapOf("event_id" to eventId))

    /** Время отметки на КП отредактировано вручную при считывании чипа. */
    data object SplitEditSaved : AnalyticsEvent("split_edit_saved")

    /** Отметка на КП удалена вручную при считывании чипа. */
    data object SplitDeleted : AnalyticsEvent("split_deleted")

    /** Открыта таблица сплитов всей группы. */
    class GroupSplitsTableOpened(groupId: Long, competitionId: String) : AnalyticsEvent(
        "group_splits_table_opened",
        mapOf("group_id" to groupId, "competition_id" to competitionId),
    )

    /** Открыт график отставания от лидера (race graph) по группе. */
    class RaceGraphOpened(groupId: Long, competitionId: String) : AnalyticsEvent(
        "race_graph_opened",
        mapOf("group_id" to groupId, "competition_id" to competitionId),
    )

    /** Открыт график набора очков во времени (score graph, BY_CHOICE) по группе. */
    class ScoreGraphOpened(groupId: Long, competitionId: String) : AnalyticsEvent(
        "score_graph_opened",
        mapOf("group_id" to groupId, "competition_id" to competitionId),
    )

    /** Организатор подтвердил публикацию результатов участникам (resultsStatus → OFFICIAL). */
    class ResultsPublishClicked(competitionId: String) :
        AnalyticsEvent("results_publish_clicked", mapOf("competition_id" to competitionId))

    // endregion

    // region Profile

    /** Открыт редактор профиля. */
    data object ProfileEditStarted : AnalyticsEvent("profile_edit_started")

    /** Сохранены изменения в профиле. */
    data object ProfileEditSaved : AnalyticsEvent("profile_edit_saved")

    enum class PushCategory { RESULTS_PUBLISHED, COMPETITION_START, DAY_BEFORE_REMINDER }

    /** Пользователь переключил тумблер категории push-уведомлений в профиле. */
    class ProfilePushCategoryToggled(category: PushCategory, enabled: Boolean) : AnalyticsEvent(
        "profile_push_category_toggled",
        mapOf("category" to category.name.lowercase(), "enabled" to enabled),
    )

    /** Пользователь подтвердил удаление аккаунта. */
    data object AccountDeletionRequested : AnalyticsEvent("account_deletion_requested")

    /** Аккаунт успешно удалён на сервере. */
    data object AccountDeletionSucceeded : AnalyticsEvent("account_deletion_succeeded")

    /** Сервер отклонил удаление аккаунта (например, пользователь — организатор соревнований). */
    class AccountDeletionFailed(reason: String) :
        AnalyticsEvent("account_deletion_failed", mapOf("reason" to reason))

    // endregion

    // region Onboarding

    enum class OnboardingSource { FIRST_LAUNCH, SETTINGS }

    /** Показан онбординг. */
    class OnboardingStarted(source: OnboardingSource) : AnalyticsEvent(
        "onboarding_started",
        mapOf("source" to source.name.lowercase()),
    )

    /** Онбординг пропущен пользователем. */
    class OnboardingSkipped(slideIndex: Int) :
        AnalyticsEvent("onboarding_skipped", mapOf("slide_index" to slideIndex))

    /** Онбординг пройден до конца. */
    data object OnboardingCompleted : AnalyticsEvent("onboarding_completed")

    // endregion

    // region Diary (тренировочный дневник)

    enum class DiarySportType { RUNNING, CYCLING, SKIING }
    enum class DiaryWorkoutStatus { PLANNED, COMPLETED }

    /**
     * Сохранена тренировка (создание или редактирование, ручной ввод или планирование).
     *
     * @param isNew true — создание новой записи, false — редактирование существующей.
     */
    class DiaryWorkoutSaved(
        sportType: DiarySportType,
        status: DiaryWorkoutStatus,
        isNew: Boolean,
    ) : AnalyticsEvent(
        "diary_workout_saved",
        mapOf(
            "sport_type" to sportType.name.lowercase(),
            "status" to status.name.lowercase(),
            "is_new" to isNew,
        ),
    )

    /** Тренировка удалена из дневника. */
    class DiaryWorkoutDeleted(sportType: DiarySportType) :
        AnalyticsEvent("diary_workout_deleted", mapOf("sport_type" to sportType.name.lowercase()))

    // endregion

    // region Clubs

    /** Создан новый клуб. */
    class ClubCreated(clubId: String) :
        AnalyticsEvent("club_created", mapOf("club_id" to clubId))

    /** Изменены настройки клуба (название/описание/приём заявок). */
    class ClubUpdated(clubId: String) :
        AnalyticsEvent("club_updated", mapOf("club_id" to clubId))

    /** Клуб удалён (только FOUNDER). */
    class ClubDeleted(clubId: String) :
        AnalyticsEvent("club_deleted", mapOf("club_id" to clubId))

    /** Подана заявка на вступление в клуб. */
    class ClubJoinRequested(clubId: String) :
        AnalyticsEvent("club_join_requested", mapOf("club_id" to clubId))

    /** Заявка на вступление рассмотрена FOUNDER/ADMIN. */
    class ClubJoinRequestReviewed(clubId: String, approved: Boolean) : AnalyticsEvent(
        "club_join_request_reviewed",
        mapOf("club_id" to clubId, "approved" to approved),
    )

    /** Участник покинул клуб или был удалён (FOUNDER/ADMIN). */
    class ClubMemberRemoved(clubId: String, isSelf: Boolean) : AnalyticsEvent(
        "club_member_removed",
        mapOf("club_id" to clubId, "is_self" to isSelf),
    )

    /** Изменена роль участника клуба (включая передачу роли Founder). */
    class ClubMemberRoleChanged(clubId: String, newRole: String) : AnalyticsEvent(
        "club_member_role_changed",
        mapOf("club_id" to clubId, "new_role" to newRole.lowercase()),
    )

    /** Создана команда внутри клуба. */
    class ClubTeamCreated(clubId: String) :
        AnalyticsEvent("club_team_created", mapOf("club_id" to clubId))

    /** Команда удалена. */
    class ClubTeamDeleted(teamId: String) :
        AnalyticsEvent("club_team_deleted", mapOf("team_id" to teamId))

    // endregion

    // region Rating

    /** Создан новый рейтинг соревнований для клуба. */
    class RatingCreated(ratingId: String, clubId: String) : AnalyticsEvent(
        "rating_created",
        mapOf("rating_id" to ratingId, "club_id" to clubId),
    )

    /** Соревнование добавлено в состав рейтинга. */
    class RatingCompetitionAdded(ratingId: String, competitionId: String) : AnalyticsEvent(
        "rating_competition_added",
        mapOf("rating_id" to ratingId, "competition_id" to competitionId),
    )

    /** Маппинг групп соревнования на канонические группы рейтинга подтверждён/сохранён. */
    class RatingGroupMappingConfirmed(ratingId: String, competitionId: String) : AnalyticsEvent(
        "rating_group_mapping_confirmed",
        mapOf("rating_id" to ratingId, "competition_id" to competitionId),
    )

    /** Рейтинг удалён (FOUNDER/ADMIN клуба-владельца). */
    class RatingDeleted(ratingId: String) :
        AnalyticsEvent("rating_deleted", mapOf("rating_id" to ratingId))

    // endregion
}
