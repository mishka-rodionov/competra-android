package com.competra.eventdetails.presentation.details

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import com.competra.designsystem.components.NetworkImage
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.competra.domain.models.cyclic_event.CyclicEventDetails
import com.competra.domain.models.cyclic_event.EventParticipantGroup
import com.competra.domain.models.cyclic_event.GroupEligibility
import com.competra.domain.models.events.EventStatus
import com.competra.domain.models.events.EventType
import com.competra.domain.models.orienteering.ResultsStatus
import com.competra.resources.R
import com.competra.eventdetails.data.details.EventDetailsState
import com.competra.eventdetails.data.details.LiveTrackEntry
import androidx.core.content.ContextCompat
import com.competra.ui.components.toFractionalRect
import com.competra.utils.DateTimeFormat
import org.koin.androidx.compose.koinViewModel

/**
 * Экран деталей события.
 * @param idEvent Идентификатор события.
 * @param viewModel Вьюмодель экрана.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsScreen(
    idEvent: String,
    viewModel: EventDetailsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    // Настраиваем состояние шторки так, чтобы она сразу открывалась полностью
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    LaunchedEffect(idEvent) {
        viewModel.initialize(idEvent)
    }

    LiveTrackPermissionsHandler(viewModel)

    if (state.isLiveTrackConsentVisible) {
        LiveTrackConsentDialog(
            onAccept = { viewModel.onAction(EventDetailsAction.LiveTrackConsentAccepted) },
            onDismiss = { viewModel.onAction(EventDetailsAction.LiveTrackConsentDismissed) }
        )
    }

    ScrollableColumnScreenWithImageAnimation(
        state = state,
        onAction = viewModel::onAction
    )

    // BottomSheet для выбора группы при регистрации
    if (state.isRegistrationSheetVisible) {
        RegistrationBottomSheet(
            state = state,
            sheetState = sheetState,
            onAction = viewModel::onAction,
            onDismiss = { viewModel.onAction(EventDetailsAction.HideRegistrationDialog) }
        )
    }
}

/**
 * Основной контент экрана деталей события с анимацией изображения при прокрутке.
 * @param state Состояние экрана.
 * @param onAction Обработчик действий.
 */
@Composable
fun ScrollableColumnScreenWithImageAnimation(
    state: EventDetailsState,
    onAction: (EventDetailsAction) -> Unit
) {
    val scrollState = rememberScrollState()
    val imageHeightPx = with(LocalDensity.current) { 250.dp.toPx() }

    // Анимация прозрачности изображения
    val imageAlpha by animateFloatAsState(
        targetValue = (1f - (scrollState.value / imageHeightPx)).coerceIn(0f, 1f),
        label = "Image Alpha Animation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Text(
            text = state.eventDetails?.title ?: "",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        NetworkImage(
            url = state.eventDetails?.imageUrl,
            cropRect = state.eventDetails?.imageCropRect?.toFractionalRect(),
            contentDescription = "Header Image",
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .graphicsLayer {
                    alpha = imageAlpha
                }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = state.eventDetails?.city ?: "", modifier = Modifier.weight(1f))
            Text(
                text = buildDateRangeText(
                    state.eventDetails?.startDate,
                    state.eventDetails?.endDate,
                    state.eventDetails?.startTime
                ),
                modifier = Modifier.weight(1f)
            )
        }

        state.eventDetails?.let { EventMetaInfo(it) }

        // Логика отображения кнопок в зависимости от статуса события
        EventActionButtons(
            state = state,
            onAction = onAction
        )

        Text(
            text = state.eventDetails?.description ?: "",
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        state.eventDetails?.let { EventOrganizerInfo(it, state.organizerClubName) }

        state.eventDetails?.participantGroups?.let { groups ->
            ParticipantGroupsList(groups = groups, onGroupClick = { group ->
                onAction(EventDetailsAction.OnGroupClick(group))
            })
        }
    }
}

/**
 * Отображает кнопки действия в зависимости от статуса события.
 * @param state Состояние экрана.
 * @param onAction Обработчик действий.
 */
@Composable
private fun EventActionButtons(
    state: EventDetailsState,
    onAction: (EventDetailsAction) -> Unit
) {
    LiveTrackButton(state = state, onAction = onAction)

    if (state.isLiveTracksButtonVisible) {
        OutlinedButton(
            onClick = { onAction(EventDetailsAction.ToLiveTracks) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Онлайн-треки участников")
        }
    }

    val status = state.eventDetails?.status
    when (status) {
        EventStatus.CREATED, EventStatus.REGISTRATION -> {
            if (state.isUserRegistered) {
                Button(
                    onClick = { onAction(EventDetailsAction.CancelRegistration) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    enabled = !state.isRegistering
                ) {
                    if (state.isRegistering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        Text("Отменить регистрацию")
                    }
                }
            } else {
                Button(
                    onClick = { onAction(EventDetailsAction.ShowRegistrationDialog) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    enabled = !state.isRegistering
                ) {
                    Text("Зарегистрироваться")
                }
            }
        }

        EventStatus.STARTED -> {
            Button(
                onClick = { onAction(EventDetailsAction.ToLiveResults) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Онлайн результаты")
            }
        }

        EventStatus.FINISHED -> {
            if (state.eventDetails?.resultsStatus == ResultsStatus.PRELIMINARY) {
                Text(
                    text = "Результаты предварительные",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Button(
                onClick = { onAction(EventDetailsAction.ToResults) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Результаты")
            }
        }

        else -> {
            // Для других статусов кнопки не отображаем или добавляем иную логику
        }
    }
}

/**
 * Кнопка онлайн-трека бегуна: включить запись или открыть экран уже идущей записи.
 */
@Composable
private fun LiveTrackButton(
    state: EventDetailsState,
    onAction: (EventDetailsAction) -> Unit
) {
    when (state.liveTrackEntry) {
        LiveTrackEntry.HIDDEN -> Unit
        LiveTrackEntry.START -> {
            OutlinedButton(
                onClick = { onAction(EventDetailsAction.LiveTrackClick) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                enabled = !state.isStartingLiveTrack
            ) {
                if (state.isStartingLiveTrack) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Включить онлайн-трек")
                }
            }
            Text(
                text = "Зрители увидят ваш трек на карте соревнования",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        LiveTrackEntry.RECORDING -> {
            Button(
                onClick = { onAction(EventDetailsAction.LiveTrackClick) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Text("Онлайн-трек: идёт запись")
            }
        }
    }
}

/**
 * Согласие на публикацию трека — один раз, перед первым включением трекинга.
 */
@Composable
private fun LiveTrackConsentDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Онлайн-трек") },
        text = {
            Text(
                "Пока вы на дистанции, телефон будет передавать ваше местоположение. Трек увидят " +
                    "все зрители соревнования в приложении и на сайте Competra, после финиша он " +
                    "останется в архиве соревнования.\n\nКарту и своё положение во время забега вы " +
                    "не увидите — это требование правил. Остановить запись можно в любой момент."
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Согласен, включить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/**
 * Проверяет и при необходимости запрашивает разрешения по просьбе ViewModel (перед стартом трека):
 * точная геолокация обязательна, уведомления (Android 13+) — чтобы было видно, что запись идёт.
 */
@Composable
private fun LiveTrackPermissionsHandler(viewModel: EventDetailsViewModel) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onAction(EventDetailsAction.LiveTrackPermissionsResult(context.hasFineLocation()))
    }
    LaunchedEffect(viewModel) {
        viewModel.liveTrackPermissionRequests.collect {
            val missing = liveTrackPermissions().filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) {
                viewModel.onAction(EventDetailsAction.LiveTrackPermissionsResult(locationGranted = true))
            } else {
                launcher.launch(missing.toTypedArray())
            }
        }
    }
}

private fun liveTrackPermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

private fun Context.hasFineLocation(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Строит строку с диапазоном дат события: одна дата или "старт – финиш",
 * если событие многодневное.
 */
private fun buildDateRangeText(startDate: Long?, endDate: Long?, startTime: Long?): String {
    val start = DateTimeFormat.transformLongToDisplayDate(startDate)
    val end = DateTimeFormat.transformLongToDisplayDate(endDate)
    val dateText = if (end.isNotEmpty() && end != start) "$start – $end" else start
    val timeText = startTime?.let { DateTimeFormat.transformLongToTime(it) }?.takeIf { it.isNotEmpty() }
    return if (timeText != null) "$dateText, $timeText" else dateText
}

/**
 * Блок с дедлайном регистрации и заполненностью события.
 * @param eventDetails Данные события.
 */
@Composable
private fun EventMetaInfo(eventDetails: CyclicEventDetails) {
    val now = System.currentTimeMillis()
    val registrationNotOpenYet = eventDetails.registrationStartDate > now
    val registrationClosed = eventDetails.endRegistrationDate in 1 until now
    val totalRegistered = eventDetails.participantGroups.sumOf { it.registeredParticipant }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        when {
            registrationNotOpenYet -> Text(
                text = "Регистрация откроется ${DateTimeFormat.transformLongToDisplayDate(eventDetails.registrationStartDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            eventDetails.endRegistrationDate > 0 -> Text(
                text = if (registrationClosed) {
                    "Регистрация закрыта"
                } else {
                    "Регистрация до ${DateTimeFormat.transformLongToDisplayDate(eventDetails.endRegistrationDate)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (registrationClosed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (eventDetails.maxParticipants > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Участников: $totalRegistered/${eventDetails.maxParticipants}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (totalRegistered.toFloat() / eventDetails.maxParticipants).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Блок с информацией об организаторе, взносе и полезными ссылками/контактами.
 * @param eventDetails Данные события.
 * @param organizerClubName Название клуба-организатора (резолвится отдельным запросом).
 */
@Composable
private fun EventOrganizerInfo(
    eventDetails: CyclicEventDetails,
    organizerClubName: String?
) {
    val uriHandler = LocalUriHandler.current
    val feeAmount = eventDetails.feeAmount
    val mapUrl = eventDetails.mapUrl
    val regulationUrl = eventDetails.regulationUrl
    val website = eventDetails.website
    val contactPhone = eventDetails.contactPhone
    val contactEmail = eventDetails.contactEmail
    val organizerLine = buildOrganizerLine(organizerClubName, eventDetails)

    val hasContent = organizerLine != null || feeAmount != null ||
        !mapUrl.isNullOrBlank() || !regulationUrl.isNullOrBlank() || !website.isNullOrBlank() ||
        !contactPhone.isNullOrBlank() || !contactEmail.isNullOrBlank()
    if (!hasContent) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        if (organizerLine != null) {
            Text(
                text = "Организатор: $organizerLine",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        if (feeAmount != null) {
            Text(
                text = "Взнос: ${formatFee(feeAmount, eventDetails.feeCurrency)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        if (!mapUrl.isNullOrBlank()) {
            Text(
                text = "Как добраться",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { uriHandler.openUri(mapUrl) }
                    .padding(bottom = 4.dp)
            )
        }
        if (!regulationUrl.isNullOrBlank()) {
            Text(
                text = "Регламент соревнования",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { uriHandler.openUri(regulationUrl) }
                    .padding(bottom = 4.dp)
            )
        }
        if (!website.isNullOrBlank()) {
            Text(
                text = website,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { uriHandler.openUri(website) }
                    .padding(bottom = 4.dp)
            )
        }
        if (!contactPhone.isNullOrBlank()) {
            Text(
                text = contactPhone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { uriHandler.openUri("tel:$contactPhone") }
                    .padding(bottom = 4.dp)
            )
        }
        if (!contactEmail.isNullOrBlank()) {
            Text(
                text = contactEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { uriHandler.openUri("mailto:$contactEmail") }
            )
        }
    }
}

/**
 * Строка организатора: название клуба и/или ФИО контактного лица.
 * Формат: "Клуб · Фамилия Имя Отчество" — части, которых нет, опускаются.
 */
private fun buildOrganizerLine(organizerClubName: String?, eventDetails: CyclicEventDetails): String? {
    val personalName = listOfNotNull(
        eventDetails.organizerLastName,
        eventDetails.organizerFirstName,
        eventDetails.organizerMiddleName
    ).filter { it.isNotBlank() }.joinToString(" ")

    val parts = listOfNotNull(organizerClubName, personalName.takeIf { it.isNotBlank() })
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun formatFee(amount: Double, currency: String?): String {
    val amountText = if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        "%.2f".format(amount)
    }
    return if (!currency.isNullOrBlank()) "$amountText $currency" else amountText
}

/**
 * Диалог (BottomSheet) регистрации на событие.
 * @param state Состояние экрана.
 * @param sheetState Состояние BottomSheet.
 * @param onAction Обработчик действий.
 * @param onDismiss Обработчик закрытия диалога.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegistrationBottomSheet(
    state: EventDetailsState,
    sheetState: SheetState,
    onAction: (EventDetailsAction) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { Spacer(modifier = Modifier.height(16.dp)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Выберите группу",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Box(modifier = Modifier.height(300.dp)) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.eventDetails?.participantGroups ?: emptyList()) { group ->
                        // Неподходящую по полу/возрасту группу выбрать нельзя — показываем причину.
                        val notEligible = state.groupEligibility[group.groupId] as? GroupEligibility.NotEligible
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = notEligible == null) { onAction(EventDetailsAction.SelectGroup(group)) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.selectedGroup?.groupId == group.groupId,
                                onClick = { onAction(EventDetailsAction.SelectGroup(group)) },
                                enabled = notEligible == null
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = group.title,
                                    color = if (notEligible == null) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (notEligible != null) {
                                    Text(
                                        text = notEligible.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val canFixInProfile = state.groupEligibility.values.any {
                it is GroupEligibility.NotEligible && it.fixInProfile
            }
            if (canFixInProfile) {
                TextButton(onClick = { onAction(EventDetailsAction.OpenProfile) }) {
                    Text("Открыть профиль")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.commandName,
                onValueChange = { onAction(EventDetailsAction.CommandNameChanged(it)) },
                label = { Text("Клуб/команда (необязательно)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isRegistering
                ) {
                    Text("Отмена")
                }
                Button(
                    onClick = { onAction(EventDetailsAction.ConfirmRegistration) },
                    modifier = Modifier.weight(1f),
                    enabled = state.selectedGroup != null && !state.isRegistering
                ) {
                    if (state.isRegistering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Готово")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Блок с отображением списка групп участников.
 * @param groups Список групп.
 * @param onGroupClick Обработчик клика на группу.
 */
@Composable
private fun ParticipantGroupsList(
    groups: List<EventParticipantGroup>,
    onGroupClick: (EventParticipantGroup) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Группы участников",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        groups.forEach { group ->
            ParticipantGroupItem(group = group, onClick = { onGroupClick(group) })
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Айтем группы участников.
 * @param group Данные группы.
 * @param onClick Обработчик клика.
 */
@Composable
private fun ParticipantGroupItem(
    group: EventParticipantGroup,
    onClick: () -> Unit
) {
    val distanceSummary = buildDistanceSummary(group)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Участников: ${group.registeredParticipant}/${group.maxParticipant}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (distanceSummary != null) {
            Text(
                text = distanceSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        val distanceDescription = group.distanceDescription
        if (!distanceDescription.isNullOrBlank()) {
            Text(
                text = distanceDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun buildDistanceSummary(group: EventParticipantGroup): String? {
    val parts = listOfNotNull(
        group.distanceName,
        group.distanceLengthMeters?.let { formatMeters(it) },
        group.distanceClimbMeters?.takeIf { it > 0 }?.let { "набор ${it} м" },
        group.distanceControlsCount?.takeIf { it > 0 }?.let { "$it КП" },
        group.controlTimeMinutes?.let { "КВ $it мин" }
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

private fun formatMeters(meters: Int): String {
    return if (meters >= 1000) {
        val km = meters / 1000.0
        val formatted = if (km == km.toLong().toDouble()) "${km.toLong()} км" else "${"%.1f".format(km)} км"
        formatted
    } else {
        "$meters м"
    }
}

@Composable
private fun Spacer(modifier: Modifier) {
    Spacer(modifier = modifier)
}

@Preview(showBackground = true, name = "Регистрация")
@Composable
private fun EventDetailsRegistrationPreview() {
    MaterialTheme {
        Surface {
            ScrollableColumnScreenWithImageAnimation(
                state = EventDetailsState(
                    eventDetails = mockEvent(EventStatus.REGISTRATION)
                ),
                onAction = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Результаты")
@Composable
private fun EventDetailsResultsPreview() {
    MaterialTheme {
        Surface {
            ScrollableColumnScreenWithImageAnimation(
                state = EventDetailsState(
                    eventDetails = mockEvent(EventStatus.FINISHED, resultsStatus = ResultsStatus.PRELIMINARY)
                ),
                onAction = {}
            )
        }
    }
}

/**
 * Вспомогательная функция для создания мока события.
 */
private fun mockEvent(
    status: EventStatus,
    resultsStatus: ResultsStatus = ResultsStatus.NOT_PUBLISHED
) = CyclicEventDetails(
    eventId = "1",
    organizationId = "org_1",
    title = "Марафон \"Путь к успеху\"",
    description = "Большой забег через весь город.",
    startDate = System.currentTimeMillis(),
    endDate = System.currentTimeMillis() + 86400000L,
    endRegistrationDate = System.currentTimeMillis() - 3600000L,
    maxParticipants = 500,
    city = "Москва",
    status = status,
    participantGroups = listOf(
        EventParticipantGroup(
            groupId = "1",
            title = "М21",
            description = "Профессионалы",
            maxParticipant = 100,
            registeredParticipant = 45,
            distanceName = "Дистанция А",
            distanceLengthMeters = 5200,
            distanceClimbMeters = 180,
            distanceControlsCount = 12,
            distanceDescription = "Сложная дистанция с преимущественно лесным ориентированием"
        )
    ),
    eventType = EventType.CyclicEvent.Orienteering,
    resultsStatus = resultsStatus
)
