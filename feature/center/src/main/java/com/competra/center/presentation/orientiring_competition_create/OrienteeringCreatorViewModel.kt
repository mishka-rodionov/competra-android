package com.competra.center.presentation.orientiring_competition_create

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.center.data.creator.OrienteeringCreatorAction
import com.competra.center.data.creator.OrienteeringCreatorState
import com.competra.center.data.creator.TestCompetitionFixtures
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.data.navigation.CenterNavigation
import com.competra.data.navigation.Navigation
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.Coordinates
import com.competra.domain.models.CropRect
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.models.orienteering.RegistrationEndMode
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.domain.models.user.User
import com.competra.domain.repository.LoadingRepository
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.UploadRepository
import com.competra.domain.repository.clubs.ClubRepository
import com.competra.domain.repository.user.UserRepository
import com.competra.resources.ResourceProvider
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import com.competra.utils.DateTimeFormat
import com.competra.utils.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ViewModel для управления процессом пошагового создания соревнования.
 * 
 * Обеспечивает сохранение данных на каждом этапе и навигацию между экранами мастера.
 */
class OrienteeringCreatorViewModel(
    val navigation: Navigation,
    private val resourceProvider: ResourceProvider,
    private val orienteeringCompetitionInteractor: OrienteeringCompetitionInteractor,
    private val userRepository: UserRepository,
    private val networkErrorRepository: NetworkErrorRepository,
    private val uploadRepository: UploadRepository,
    private val context: Context,
    private val loadingRepository: LoadingRepository,
    private val analytics: AnalyticsTracker,
    private val clubRepository: ClubRepository,
) : BaseViewModel<OrienteeringCreatorState>(OrienteeringCreatorState()) {

    var user: User? = null

    init {
        viewModelScope.launch {
            user = userRepository.retrieveUser().getOrNull()
        }
    }

    fun loadMyClubs() {
        viewModelScope.launch {
            clubRepository.listMine().onSuccess { clubs ->
                updateState { copy(myClubs = clubs) }
            }
        }
    }

    fun updateOrganizingClubId(clubId: String?) = updateState { copy(organizingClubId = clubId) }


    override fun onAction(action: BaseAction) {
        when (action) {
            OrienteeringCreatorAction.ShowDistanceCreateDialog -> updateState {
                copy(
                    isShowDistanceCreateDialog = true,
                    editDistanceIndex = -1
                )
            }

            OrienteeringCreatorAction.HideDistanceCreateDialog -> updateState {
                copy(
                    isShowDistanceCreateDialog = false
                )
            }

            is OrienteeringCreatorAction.CreateDistance -> {
                val updatedDistances = stateValue.distances.toMutableList()
                if (action.index == -1) {
                    updatedDistances.add(action.distance)
                    viewModelScope.launch {
                        orienteeringCompetitionInteractor.saveDistance(action.distance, silent = true)
                            .onFailure { error ->
                                Log.e("OrienteeringCreatorVM", "saveDistance failed", error)
                                handleFailure(error)
                            }
                    }
                } else {
                    updatedDistances[action.index] = action.distance
                    viewModelScope.launch {
                        orienteeringCompetitionInteractor.updateDistance(action.distance, silent = true)
                            .onFailure { error ->
                                Log.e("OrienteeringCreatorVM", "updateDistance failed", error)
                                handleFailure(error)
                            }
                    }
                }
                updateState {
                    copy(
                        distances = updatedDistances,
                        isShowDistanceCreateDialog = false
                    )
                }
            }

            /**
             * Обработка экшена показа диалога создания группы участников.
             * Устанавливает флаг isShowGroupCreateDialog в true.
             */
            OrienteeringCreatorAction.ShowGroupCreateDialog -> updateState {
                copy(
                    isShowGroupCreateDialog = true,
                    editGroupIndex = -1
                )
            }

            /**
             * Скрытие диалога создания группы.
             */
            OrienteeringCreatorAction.HideGroupCreateDialog -> updateState {
                copy(
                    isShowGroupCreateDialog = false
                )
            }

            /**
             * Обработка создания/добавления новой группы участников.
             * Добавляет группу в список и закрывает диалог.
             */
            is OrienteeringCreatorAction.CreateParticipantGroup -> {
                if (action.index == -1) {
                    val updatedGroups = stateValue.participantGroups.toMutableList().apply { add(action.participantGroup) }
                    updateState { copy(participantGroups = updatedGroups, isShowGroupCreateDialog = false) }
                    viewModelScope.launch {
                        orienteeringCompetitionInteractor.localSaveParticipantGroups(
                            participantGroups = listOf(action.participantGroup),
                            silent = true
                        )
                        // Перезагружаем группы из БД, чтобы получить реальный groupId
                        // вместо 0L, который приходит из диалога для новых групп.
                        // Без этого updateParticipantsGroups в finishCreation вставит группу повторно.
                        stateValue.competitionId?.let { compId ->
                            orienteeringCompetitionInteractor.getCompetitionWithDetails(compId)
                                .getOrNull()
                                ?.groupsWithParticipants
                                ?.map { it.group }
                                ?.let { freshGroups -> updateState { copy(participantGroups = freshGroups) } }
                        }
                    }
                } else {
                    val updatedGroups = stateValue.participantGroups.toMutableList().apply { set(action.index, action.participantGroup) }
                    updateState { copy(participantGroups = updatedGroups, isShowGroupCreateDialog = false) }
                    viewModelScope.launch {
                        orienteeringCompetitionInteractor.updateParticipantGroup(action.participantGroup, silent = true)
                    }
                }
            }

            is OrienteeringCreatorAction.UpdateCompetitionDate -> {
                updateStartDate(action.competitionDate)
            }

            is OrienteeringCreatorAction.UpdateCompetitionTime -> {
                val combined = DateTimeFormat.updateTimeInTimestamp(stateValue.startDate, action.competitionTime, competitionZone())
                updateState { copy(startTimeStr = action.competitionTime, startDate = combined ?: stateValue.startDate) }
            }

            is OrienteeringCreatorAction.UpdateTimeZone -> handleTimeZoneChange(action.zoneId)

            is OrienteeringCreatorAction.UpdateRegistrationStartDate -> {
                val combined = DateTimeFormat.updateTimeInTimestamp(action.date, stateValue.registrationStartTimeStr, competitionZone()) ?: action.date
                updateState { copy(registrationStart = combined, errors = errors.copy(isEmptyRegistrationStart = false)) }
            }

            is OrienteeringCreatorAction.UpdateRegistrationStartTime -> {
                val combined = DateTimeFormat.updateTimeInTimestamp(stateValue.registrationStart, action.time, competitionZone())
                updateState { copy(registrationStartTimeStr = action.time, registrationStart = combined ?: stateValue.registrationStart) }
            }

            is OrienteeringCreatorAction.UpdateRegistrationStartOnCreate -> updateState {
                copy(
                    registrationStartOnCreate = action.enabled,
                    errors = errors.copy(isEmptyRegistrationStart = false)
                )
            }

            is OrienteeringCreatorAction.UpdateRegistrationEndDate -> {
                val combined = DateTimeFormat.updateTimeInTimestamp(action.date, stateValue.registrationEndTimeStr, competitionZone()) ?: action.date
                updateState { copy(registrationEnd = combined, errors = errors.copy(isEmptyRegistrationEnd = false)) }
            }

            is OrienteeringCreatorAction.UpdateRegistrationEndTime -> {
                val combined = DateTimeFormat.updateTimeInTimestamp(stateValue.registrationEnd, action.time, competitionZone())
                updateState { copy(registrationEndTimeStr = action.time, registrationEnd = combined ?: stateValue.registrationEnd) }
            }

            is OrienteeringCreatorAction.UpdateRegistrationEndMode -> updateState {
                copy(
                    registrationEndMode = action.mode,
                    errors = errors.copy(isEmptyRegistrationEnd = false)
                )
            }

            is OrienteeringCreatorAction.UpdateCompetitionDirection -> updateState {
                copy(competitionDirection = action.direction)
            }

            is OrienteeringCreatorAction.UpdateStartTimeMode -> updateState {
                // При переходе на "по стартовой станции" механическая/бумажная отметка (PENCIL/PUNCH)
                // теряет смысл — стартовая станция требует электронной системы. Если организатор уже
                // выбрал такую систему (PunchingSystemSelector отрисовывается раньше StartTimeModeSelector
                // на экране), сбрасываем на дефолтную электронную, чтобы не остался невалидный выбор.
                val punching = if (
                    action.startTimeMode == StartTimeMode.BY_START_STATION &&
                    punchingSystem !in setOf(PunchingSystem.SPORTIDUINO, PunchingSystem.SPORTIDENT, PunchingSystem.SFR)
                ) {
                    PunchingSystem.SPORTIDUINO
                } else {
                    punchingSystem
                }
                copy(startTimeMode = action.startTimeMode, punchingSystem = punching)
            }

            is OrienteeringCreatorAction.UpdatePunchingSystem -> updateState {
                copy(punchingSystem = action.punchingSystem)
            }

            is OrienteeringCreatorAction.UpdateStartInterval -> updateState {
                copy(startIntervalSeconds = action.seconds)
            }

            is OrienteeringCreatorAction.EditDistanceDialog -> updateState {
                copy(isShowDistanceCreateDialog = true, editDistanceIndex = action.index)
            }

            is OrienteeringCreatorAction.EditGroupDialog -> updateState {
                copy(isShowGroupCreateDialog = true, editGroupIndex = action.index)
            }

            is OrienteeringCreatorAction.UpdateCoordinates -> updateState {
                copy(coordinates = Coordinates(action.latitude, action.longitude))
            }

            is OrienteeringCreatorAction.CoverImagePicked -> updateState {
                copy(pendingCoverCropUri = action.uri)
            }

            is OrienteeringCreatorAction.CancelCoverCrop -> updateState {
                copy(pendingCoverCropUri = null)
            }

            is OrienteeringCreatorAction.ConfirmCoverCrop -> confirmCoverCrop(action.cropRect)

            is OrienteeringCreatorAction.UploadCompetitionMap -> uploadFile(
                uri = action.uri,
                type = "competition_map",
                onSuccess = { url -> updateState { copy(mapUrl = url) } }
            )

            is OrienteeringCreatorAction.UpdateIsTest -> updateState {
                copy(isTest = action.isTest)
            }

            OrienteeringCreatorAction.FillWithTestData -> fillWithTestData()
        }
    }

    /**
     * Debug-инструмент: заполняет форму преднабором тестовых данных и сразу создаёт
     * локально соревнование, две дистанции (с разными КП) и две группы — М21 и Ж21,
     * каждая на своей дистанции.
     *
     * Соревнование сохраняется немедленно (а не на шаге «Далее»), потому что дистанции
     * и группы ссылаются на него по FK и требуют присвоенных Room локальных id дистанций.
     * Все мутации `silent = true` — единственная синхронизация выполняется в конце мастера.
     */
    private fun fillWithTestData() {
        viewModelScope.launch(Dispatchers.IO) {
            if (user == null) {
                user = userRepository.retrieveUser().getOrNull()
            }

            // Собираем заполненный State ЛОКАЛЬНО. Нельзя полагаться на updateState→stateValue:
            // updateState применяется асинхронно (Main.immediate), и чтение stateValue сразу после
            // вернуло бы старое состояние — соревнование сохранилось бы под другим UUID, а дистанции
            // и группы повисли бы на несуществующем FK.
            val current = stateValue
            val wasNew = current.competitionId == null
            val compId = current.competitionId ?: java.util.UUID.randomUUID().toString()
            val filled = TestCompetitionFixtures.fill(current).copy(competitionId = compId)

            // 1. Сохраняем соревнование локально — нужно для FK дистанций и групп.
            val competition = filled.toOrienteeringCompetition(user?.id)
            val saved = if (wasNew) {
                orienteeringCompetitionInteractor.saveCompetitionNew(competition, silent = true)
            } else {
                orienteeringCompetitionInteractor.updateCompetitionNew(competition, silent = true)
            }
            saved.onFailure {
                handleFailure(it)
                return@launch
            }

            // 2. Сохраняем дистанции и запоминаем присвоенные Room локальные id (в порядке фикстуры).
            val distanceIds = TestCompetitionFixtures.distances(compId).map { distance ->
                orienteeringCompetitionInteractor.saveDistance(distance, silent = true).getOrNull() ?: 0L
            }

            // 3. Сохраняем группы М21/Ж21, привязав каждую к своей дистанции.
            orienteeringCompetitionInteractor.localSaveParticipantGroups(
                participantGroups = TestCompetitionFixtures.groups(compId, distanceIds),
                silent = true
            )

            // 4. Перечитываем дистанции и группы из БД (с реальными id) и одним апдейтом
            //    кладём всё заполненное состояние в форму.
            val freshDistances = orienteeringCompetitionInteractor.getDistances(compId)
                .getOrDefault(emptyList())
            val freshGroups = orienteeringCompetitionInteractor.getCompetitionWithDetails(compId)
                .getOrNull()?.groupsWithParticipants?.map { it.group } ?: emptyList()

            updateState { filled.copy(distances = freshDistances, participantGroups = freshGroups) }
        }
    }

    /**
     * Инициализирует состояние данными существующего соревнования для редактирования.
     * Загружает детали соревнования, список дистанций и список групп.
     * 
     * @param competitionId Идентификатор соревнования.
     */
    fun initialize(competitionId: String?) {
        if (competitionId == null) return
        if (stateValue.competitionId == competitionId) return

        viewModelScope.launch {
            if (user == null) {
                user = userRepository.retrieveUser().getOrNull()
            }

            // Загрузка основных деталей соревнования
            val comp =
                orienteeringCompetitionInteractor.getCompetition(competitionId) ?: return@launch

            val loadedZone = runCatching { ZoneId.of(comp.competition.timeZoneId) }
                .getOrDefault(ZoneId.systemDefault())
            updateState {
                copy(
                    competitionId = competitionId,
                    title = comp.competition.title,
                    imageUrl = comp.competition.imageUrl,
                    imageCropRect = comp.competition.imageCropRect,
                    timeZoneId = comp.competition.timeZoneId,
                    startDate = comp.competition.startDate,
                    startTimeStr = DateTimeFormat.transformLongToTime(comp.competition.startDate, loadedZone),
                    endDate = comp.competition.endDate,
                    kindOfSport = comp.competition.kindOfSport,
                    description = comp.competition.description ?: "",
                    address = comp.competition.address ?: "",
                    coordinates = if (isCoordinatesSetByUser) coordinates
                                 else comp.competition.coordinates ?: coordinates,
                    registrationStart = comp.competition.registrationStart,
                    registrationStartTimeStr = DateTimeFormat.transformLongToTime(comp.competition.registrationStart, loadedZone).ifEmpty { "10:00" },
                    registrationStartOnCreate = comp.competition.registrationStart == null,
                    registrationEnd = comp.competition.registrationEnd,
                    registrationEndTimeStr = DateTimeFormat.transformLongToTime(comp.competition.registrationEnd, loadedZone).ifEmpty { "23:59" },
                    registrationEndMode = if (comp.competition.registrationEnd != null &&
                        comp.competition.registrationEnd == comp.competition.startDate - 24L * 60 * 60 * 1000
                    ) {
                        RegistrationEndMode.DAY_BEFORE_START
                    } else {
                        RegistrationEndMode.AT_COMPETITION_START
                    },
                    maxParticipants = comp.competition.maxParticipants,
                    isFeeEnabled = comp.competition.feeAmount != null,
                    feeAmount = comp.competition.feeAmount,
                    feeCurrency = comp.competition.feeCurrency ?: "RUB",
                    regulationUrl = comp.competition.regulationUrl ?: "",
                    mapUrl = comp.competition.mapUrl ?: "",
                    contactPhone = comp.competition.contactPhone?.ifEmpty { user?.phoneNumber ?: "" } ?: user?.phoneNumber ?: "",
                    contactEmail = comp.competition.contactEmail?.ifEmpty { user?.email ?: "" } ?: user?.email ?: "",
                    website = comp.competition.website ?: "",
                    competitionDirection = comp.direction,
                    punchingSystem = comp.punchingSystem,
                    startTimeMode = comp.startTimeMode,
                    countdownTimer = comp.countdownTimer,
                    startIntervalSeconds = comp.startIntervalSeconds,
                    isTest = comp.competition.isTest,
                    organizingClubId = comp.competition.organizingClubId,
                )
            }
            
            // Синхронизируем дистанции и группы с сервером только при первом входе в визард.
            // Если локальные дистанции уже есть — пользователь мог внести правки на предыдущем
            // шаге (saved silent=true без отправки на сервер). Повторный sync перезаписал бы их
            // серверными данными, уничтожив несохранённые изменения.
            // Соревнование подтверждено сервером (serverUpdatedAt != null) — подтягиваем дистанции/группы.
            if (comp.serverUpdatedAt != null) {
                val hasLocalDistances = orienteeringCompetitionInteractor
                    .getDistances(competitionId).getOrNull().orEmpty().isNotEmpty()
                if (!hasLocalDistances) {
                    orienteeringCompetitionInteractor.fetchAndSyncFromServer(competitionId)
                }
            }

            // Загрузка дистанций из локальной БД (уже актуальных после синхронизации)
            orienteeringCompetitionInteractor.getDistances(competitionId).onSuccess { list ->
                updateState { copy(distances = list) }
            }

            // Загрузка групп из локальной БД (уже актуальных после синхронизации)
            orienteeringCompetitionInteractor.getCompetitionWithDetails(competitionId).onSuccess { details ->
                updateState {
                    copy(participantGroups = details.groupsWithParticipants.map { it.group })
                }
            }
        }
    }

    /**
     * Сохраняет данные первого шага (Общая информация) и переходит ко второму.
     */
    fun saveStepOne() {
        val isNew = stateValue.competitionId == null
        if (isNew) analytics.trackEvent(AnalyticsEvent.CreateCompetitionStarted(KIND_ORIENTEERING))
        viewModelScope.launch(Dispatchers.IO) {
            loadingRepository.emit(true)
            val competition = stateValue.toOrienteeringCompetition(user?.id)
            val result = if (isNew) {
                // Создание нового
                orienteeringCompetitionInteractor.saveCompetitionNew(competition, silent = true)
            } else {
                // Обновление существующего
                orienteeringCompetitionInteractor.updateCompetitionNew(competition, silent = true)
            }
            result.onSuccess {
                updateState {
                    copy(competitionId = it.competitionId)
                }
                analytics.trackEvent(
                    AnalyticsEvent.CreateCompetitionStepCompleted(AnalyticsEvent.CreateCompetitionStep.COMMON)
                )
                viewModelScope.launch(Dispatchers.Main) {
                    navigation.navigate(
                        CenterNavigation.RegistrationCompetitionFieldRoute(
                            competitionId = it.competitionId
                        )
                    )
                }
            }
                .onFailure {
                    handleFailure(it)
                }
            loadingRepository.emit(false)
        }
    }

    /**
     * Сохраняет данные второго шага (Регистрация) и переходит к третьему.
     * Перед сохранением валидирует поля регистрации и вычисляет итоговые значения
     * в зависимости от состояния свитчей.
     */
    fun saveStepTwo() {
        val startEmpty = !stateValue.registrationStartOnCreate && stateValue.registrationStart == null
        if (startEmpty) {
            updateState {
                copy(errors = errors.copy(isEmptyRegistrationStart = startEmpty))
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val actualRegistrationStart = if (stateValue.registrationStartOnCreate) null else stateValue.registrationStart
            val actualRegistrationEnd = when (stateValue.registrationEndMode) {
                RegistrationEndMode.DAY_BEFORE_START -> stateValue.startDate - 24L * 60 * 60 * 1000
                RegistrationEndMode.AT_COMPETITION_START -> stateValue.startDate
            }

            val competition = stateValue.copy(
                registrationStart = actualRegistrationStart,
                registrationEnd = actualRegistrationEnd
            ).toOrienteeringCompetition(user?.id)

            orienteeringCompetitionInteractor.localUpdate(
                competition,
                stateValue.participantGroups,
                silent = true
            )

            analytics.trackEvent(
                AnalyticsEvent.CreateCompetitionStepCompleted(AnalyticsEvent.CreateCompetitionStep.REGISTRATION)
            )
            viewModelScope.launch(Dispatchers.Main) {
                navigation.navigate(
                    CenterNavigation.OrganizatorCompetitionFieldRoute(
                        competitionId = stateValue.competitionId.orEmpty()
                    )
                )
            }
        }
    }

    /**
     * Сохраняет данные третьего шага (Организатор) и переходит к четвертому.
     * Валидирует обязательное поле контактного телефона.
     */
    fun saveStepThree() {
        if (stateValue.contactPhone.isBlank()) {
            updateState { copy(errors = errors.copy(isEmptyContactPhone = true)) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val competition = stateValue.toOrienteeringCompetition(user?.id)
            orienteeringCompetitionInteractor.localUpdate(
                competition,
                stateValue.participantGroups,
                silent = true
            )

            analytics.trackEvent(
                AnalyticsEvent.CreateCompetitionStepCompleted(AnalyticsEvent.CreateCompetitionStep.ORGANIZATOR)
            )
            viewModelScope.launch(Dispatchers.Main) {
                navigation.navigate(
                    CenterNavigation.CreateDistanceRoute(
                        competitionId = stateValue.competitionId.orEmpty()
                    )
                )
            }
        }
    }

    /**
     * Сохраняет данные четвертого шага (Дистанции) и переходит к пятому.
     */
    fun saveStepFour() {
        // Логика сохранения дистанций
        analytics.trackEvent(
            AnalyticsEvent.CreateCompetitionStepCompleted(AnalyticsEvent.CreateCompetitionStep.DISTANCE)
        )
        viewModelScope.launch(Dispatchers.Main) {
            navigation.navigate(
                CenterNavigation.CreateParticipantGroupRoute(
                    competitionId = stateValue.competitionId.orEmpty()
                )
            )
        }
    }

    /**
     * Финальное сохранение, публикация на сервере и выход из мастера.
     * Обновляет локальные данные, затем отправляет соревнование на сервер.
     * Навигация выполняется вне зависимости от результата серверного запроса —
     * данные уже сохранены локально.
     * Выполняет переход на главный экран раздела "Центр" с очисткой навигационного стека.
     */
    fun finishCreation() {
        updateState { copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            loadingRepository.emit(true)
            val competition = stateValue.toOrienteeringCompetition(user?.id)
            val groups = stateValue.participantGroups

            orienteeringCompetitionInteractor.localUpdate(
                competition,
                groups
            )

            // Перезагружаем группы из БД, чтобы получить актуальные локальные ID
            // (новые группы с groupId=0 получают реальный ID при вставке)
            val freshGroups = stateValue.competitionId?.let { id ->
                orienteeringCompetitionInteractor.getCompetitionWithDetails(id)
                    .getOrNull()?.groupsWithParticipants?.map { it.group }
            } ?: groups

            analytics.trackEvent(
                AnalyticsEvent.CreateCompetitionStepCompleted(AnalyticsEvent.CreateCompetitionStep.GROUPS)
            )
            orienteeringCompetitionInteractor.publishCompetitionToServer(competition)
                .onSuccess { serverCompetition ->
                    analytics.trackEvent(
                        AnalyticsEvent.CreateCompetitionFinished(
                            competitionId = competition.competitionId,
                            kindOfSport = KIND_ORIENTEERING,
                        )
                    )
                    // Загружаем дистанции из локальной БД и помечаем их unsynced —
                    // worker сам выгрузит их при готовности соревнования на сервере.
                    val distances = orienteeringCompetitionInteractor
                        .getDistances(competition.competitionId)
                        .getOrDefault(emptyList())
                    if (distances.isNotEmpty()) {
                        orienteeringCompetitionInteractor.publishDistancesToServer(
                            competitionId = competition.competitionId,
                            distances = distances
                        )
                    }

                    // Группы передаём с ЛОКАЛЬНЫМ distanceId — маппинг local→remote
                    // делает SyncOrchestrator.syncGroups перед отправкой. Если подменять
                    // distanceId здесь, worker не найдёт дистанцию по локальному PK и
                    // отфильтрует группу как «не готовую к синку» — запрос на сервер не уйдёт.
                    orienteeringCompetitionInteractor.publishGroupsToServer(competition.competitionId, freshGroups)
                }
                .onFailure { error ->
                    updateState { copy(error = error.message) }
                    handleFailure(error)
                }

            updateState { copy(isLoading = false) }
            loadingRepository.emit(false)

            viewModelScope.launch(Dispatchers.Main) {
                val destination = CenterNavigation.CenterRoute
                destination.navOptionsBuilder = {
                    popUpTo(CenterNavigation.CenterRoute) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
                navigation.navigate(destination)
            }
        }
    }

    fun back() {
        viewModelScope.launch(Dispatchers.Main) {
            navigation.back()
        }
    }

    private fun handleFailure(throwable: Throwable) {
        viewModelScope.launch {
            val code = (throwable as? NetworkException)?.code
            networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
        }
    }

    fun updateTitle(title: String) = updateState { copy(title = title) }
    fun updateAddress(address: String) = updateState { copy(address = address) }
    fun updateDescription(description: String) = updateState { copy(description = description) }
    fun updateCoordinates(lat: Double, lon: Double) = updateState {
        copy(coordinates = Coordinates(lat, lon), isCoordinatesSetByUser = true)
    }

    private fun confirmCoverCrop(cropRect: CropRect) {
        val uri = stateValue.pendingCoverCropUri ?: return
        updateState { copy(pendingCoverCropUri = null) }
        uploadFile(
            uri = uri,
            type = "competition_image",
            compressPreset = ImageCompressor.Preset.COMPETITION_COVER,
            onSuccess = { url -> updateState { copy(imageUrl = url, imageCropRect = cropRect) } }
        )
    }

    /**
     * Читает файл по [uri] и загружает его на сервер.
     * [compressPreset] задаётся только для изображений (например, обложки соревнования) —
     * файл карты соревнования выбирается через пикер с любым MIME-типом и может быть не изображением,
     * поэтому для него сжатие не применяется.
     */
    private fun uploadFile(
        uri: android.net.Uri,
        type: String,
        compressPreset: ImageCompressor.Preset? = null,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            loadingRepository.emit(true)
            val bytes = if (compressPreset != null) {
                ImageCompressor.compress(context, uri, compressPreset.maxWidthPx, compressPreset.quality)
            } else {
                context.contentResolver.openInputStream(uri)?.readBytes()
            } ?: run {
                loadingRepository.emit(false)
                return@launch
            }
            val fileName = if (compressPreset != null) "image.jpg" else (uri.lastPathSegment ?: "file")
            uploadRepository.uploadFile(bytes, fileName, type)
                .onSuccess { url -> onSuccess(url) }
                .onFailure { handleFailure(it) }
            loadingRepository.emit(false)
        }
    }

    /** Открывает экран выбора координат на карте. */
    fun openMapPicker() {
        viewModelScope.launch(Dispatchers.Main) {
            navigation.navigate(
                CenterNavigation.MapPickerRoute(
                    initLatE6 = (stateValue.coordinates.latitude * 1_000_000).toLong(),
                    initLonE6 = (stateValue.coordinates.longitude * 1_000_000).toLong()
                )
            )
        }
    }
    
    fun updateStartDate(date: Long) {
        val updatedTimestamp = DateTimeFormat.updateTimeInTimestamp(date, stateValue.startTimeStr, competitionZone()) ?: date
        updateState { copy(startDate = updatedTimestamp) }
    }

    private fun competitionZone(): ZoneId =
        runCatching { ZoneId.of(stateValue.timeZoneId) }.getOrDefault(ZoneId.systemDefault())

    /**
     * Меняет часовой пояс соревнования и пересчитывает все timestamp'ы так,
     * чтобы локально-отображаемое время (HH:mm, выбранная дата) осталось тем же.
     * Поведение: пользователь видит «10:00 1 июня» — после смены на NSK тот же «10:00 1 июня»
     * по новосибирскому времени.
     */
    private fun handleTimeZoneChange(newZoneIdRaw: String) {
        val newZone = runCatching { ZoneId.of(newZoneIdRaw) }.getOrNull() ?: return
        val oldZone = competitionZone()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun recompute(ts: Long?, timeStr: String): Long? {
            if (ts == null || ts == 0L) return ts
            val localDate = java.time.Instant.ofEpochMilli(ts).atZone(oldZone).toLocalDate()
            val localTime = runCatching { LocalTime.parse(timeStr, timeFormatter) }
                .getOrNull() ?: java.time.Instant.ofEpochMilli(ts).atZone(oldZone).toLocalTime()
            return localDate.atTime(localTime).atZone(newZone).toInstant().toEpochMilli()
        }

        updateState {
            copy(
                timeZoneId = newZoneIdRaw,
                startDate = recompute(startDate, startTimeStr) ?: startDate,
                registrationStart = recompute(registrationStart, registrationStartTimeStr),
                registrationEnd = recompute(registrationEnd, registrationEndTimeStr)
            )
        }
    }

    fun updateEndDate(date: Long?) = updateState { copy(endDate = date) }

    fun updateRegistrationStart(date: Long?) = updateState { copy(registrationStart = date) }
    fun updateRegistrationEnd(date: Long?) = updateState { copy(registrationEnd = date) }
    fun updateMaxParticipants(max: String) =
        updateState { copy(maxParticipants = max.toIntOrNull()) }

    /**
     * Переключает возможность ввода взноса.
     * @param enabled true, если взнос включен.
     */
    fun updateFeeEnabled(enabled: Boolean) = updateState { copy(isFeeEnabled = enabled) }

    fun updateFeeAmount(amount: String) = updateState { copy(feeAmount = amount.toDoubleOrNull()) }
    fun updateRegulationUrl(url: String) = updateState { copy(regulationUrl = url) }

    fun updateMapUrl(url: String) = updateState { copy(mapUrl = url) }
    fun updateContactPhone(phone: String) = updateState { copy(contactPhone = phone, errors = errors.copy(isEmptyContactPhone = false)) }
    fun updateContactEmail(email: String) = updateState { copy(contactEmail = email) }
    fun updateWebsite(site: String) = updateState { copy(website = site) }

    private companion object {
        const val KIND_ORIENTEERING = "orienteering"
    }
}
