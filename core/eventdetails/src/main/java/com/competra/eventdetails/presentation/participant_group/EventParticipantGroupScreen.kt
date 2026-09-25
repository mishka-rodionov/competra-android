package com.competra.eventdetails.presentation.participant_group

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.domain.models.Gender
import com.competra.domain.models.cyclic_event.EventParticipantGroup
import com.competra.domain.models.cyclic_event.GroupEligibility
import com.competra.domain.models.cyclic_event.birthYearsRange
import com.competra.domain.models.cyclic_event.groupGenderRestriction
import com.competra.domain.models.events.EventStatus
import com.competra.domain.models.orienteering.OrienteeringParticipant
import com.competra.eventdetails.data.participant_group.EventParticipantGroupState
import com.competra.ui.BaseAction
import com.competra.utils.DateTimeFormat
import org.koin.androidx.compose.koinViewModel

/**
 * Экран списка участников группы события.
 * @param eventId Идентификатор события.
 * @param participantGroup Данные группы участников.
 * @param viewModel Вьюмодель экрана.
 */
@Composable
fun EventParticipantGroupScreen(
    eventId: String,
    participantGroup: EventParticipantGroup,
    viewModel: EventParticipantGroupViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(eventId, participantGroup) {
        viewModel.initialize(eventId, participantGroup)
    }

    EventParticipantGroupContent(
        participantGroup = participantGroup,
        state = state,
        onAction = viewModel::onAction
    )
}

/**
 * Контент экрана группы участников события.
 * @param participantGroup Данные группы.
 * @param state Состояние экрана.
 * @param onAction Обработчик действий пользователя.
 */
@Composable
private fun EventParticipantGroupContent(
    participantGroup: EventParticipantGroup,
    state: EventParticipantGroupState,
    onAction: (BaseAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Заголовок группы и кнопка регистрации
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = participantGroup.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )

            // Кнопка скрыта, если пользователь уже зарегистрирован в другой группе этого события
            val showRegistrationButton = state.eventStatus == EventStatus.REGISTRATION
                && (!state.isUserRegisteredInEvent || state.isUserRegistered)
            if (showRegistrationButton) {
                RegistrationButton(
                    isUserRegistered = state.isUserRegistered,
                    isRegistering = state.isRegistering,
                    isEligible = state.eligibility !is GroupEligibility.NotEligible,
                    onAction = onAction
                )
            }
        }

        val notEligible = state.eligibility as? GroupEligibility.NotEligible
        if (state.eventStatus == EventStatus.REGISTRATION && !state.isUserRegisteredInEvent && notEligible != null) {
            NotEligibleBlock(notEligible = notEligible, onAction = onAction)
        }

        RestrictionsInfo(group = participantGroup, competitionYear = state.competitionYear)

        DistanceInfoBlock(group = participantGroup)

        Text(
            text = "Участники (${state.participants.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        StartProtocolHeader()

        HorizontalDivider()

        // Список участников (отсортирован по стартовому времени, затем по стартовому номеру)
        val sorted = state.participants.sortedWith(
            compareBy({ if (it.startTime > 0L) 0 else 1 }, { it.startTime }, { it.startNumber })
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(sorted) { participant ->
                StartProtocolRow(participant = participant)
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Причина, по которой группа не подходит пользователю, и переход в профиль, если её можно устранить там.
 */
@Composable
private fun NotEligibleBlock(notEligible: GroupEligibility.NotEligible, onAction: (BaseAction) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = notEligible.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        if (notEligible.fixInProfile) {
            TextButton(onClick = { onAction(EventParticipantGroupAction.OpenProfile) }) {
                Text(text = "Открыть профиль")
            }
        }
    }
}

/**
 * Ограничения группы по полу и возрасту. Возраст считается по году рождения — поэтому рядом
 * показываем и сами годы. Не отображается, если ограничений нет.
 */
@Composable
private fun RestrictionsInfo(group: EventParticipantGroup, competitionYear: Int?) {
    val minAge = group.minAge?.takeIf { it > 0 }
    val maxAge = group.maxAge?.takeIf { it > 0 }
    val ages = when {
        minAge != null && maxAge != null -> "$minAge–$maxAge лет"
        minAge != null -> "от $minAge лет"
        maxAge != null -> "до $maxAge лет"
        else -> null
    }?.let { ages ->
        competitionYear?.let { birthYearsRange(minAge, maxAge, it) }?.let { "$ages ($it)" } ?: ages
    }
    val gender = when (groupGenderRestriction(group.gender)) {
        Gender.MALE -> "Мужчины"
        Gender.FEMALE -> "Женщины"
        else -> null
    }
    val text = listOfNotNull(gender, ages).joinToString(" • ")
    if (text.isEmpty()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/**
 * Блок с информацией о дистанции группы. Не отображается, если данных нет.
 */
@Composable
private fun DistanceInfoBlock(group: EventParticipantGroup) {
    val hasDistanceInfo = group.distanceName != null
        || group.distanceLengthMeters != null
        || group.distanceClimbMeters != null
        || group.distanceControlsCount != null
        || group.controlTimeMinutes != null

    if (!hasDistanceInfo) return

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        val stats = listOfNotNull(
            group.distanceName,
            group.distanceLengthMeters?.let { formatMeters(it) },
            group.distanceClimbMeters?.let { "набор $it м" },
            group.distanceControlsCount?.let { "КП: $it" },
            group.controlTimeMinutes?.let { minutes ->
                // При IGNORE КВ носит справочный характер — говорим об этом прямо,
                // иначе участник решит, что его снимут.
                when (group.overtimePolicy) {
                    "DISQUALIFY" -> "КВ: $minutes мин"
                    "SCORE_PENALTY" -> "КВ: $minutes мин (штраф очками)"
                    else -> "КВ: $minutes мин (справочно)"
                }
            }
        )
        if (stats.isNotEmpty()) {
            Text(
                text = stats.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val description = group.distanceDescription
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatMeters(meters: Int): String {
    return if (meters >= 1000) {
        val km = meters / 1000.0
        if (km == km.toLong().toDouble()) "${km.toLong()} км" else "${"%.1f".format(km)} км"
    } else {
        "$meters м"
    }
}

/**
 * Кнопка регистрации/отмены регистрации.
 * @param isUserRegistered Зарегистрирован ли пользователь.
 * @param isRegistering Состояние процесса регистрации.
 * @param isEligible Подходит ли группа пользователю по полу и возрасту (иначе регистрация неактивна).
 * @param onAction Обработчик действий.
 */
@Composable
private fun RegistrationButton(
    isUserRegistered: Boolean,
    isRegistering: Boolean,
    isEligible: Boolean,
    onAction: (BaseAction) -> Unit
) {
    if (isUserRegistered) {
        Button(
            onClick = { onAction(EventParticipantGroupAction.CancelRegistration) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            enabled = !isRegistering
        ) {
            if (isRegistering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                Text(text = "Отменить регистрацию")
            }
        }
    } else {
        Button(
            onClick = { onAction(EventParticipantGroupAction.RegisterUser) },
            enabled = !isRegistering && isEligible
        ) {
            if (isRegistering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(text = "Зарегистрироваться")
            }
        }
    }
}

/**
 * Шапка таблицы стартового протокола.
 */
@Composable
private fun StartProtocolHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "№",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.12f)
        )
        Text(
            text = "Участник",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.55f)
        )
        Text(
            text = "Старт",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.33f),
            textAlign = TextAlign.End
        )
    }
}

/**
 * Строка стартового протокола для одного участника.
 * @param participant Данные участника.
 */
@Composable
private fun StartProtocolRow(participant: OrienteeringParticipant) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = participant.startNumber.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.12f)
        )
        Column(modifier = Modifier.weight(0.55f)) {
            Text(
                text = "${participant.lastName} ${participant.firstName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (participant.commandName.isNotBlank()) {
                Text(
                    text = participant.commandName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = formatStartTime(participant.startTime),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.33f),
            textAlign = TextAlign.End
        )
    }
}

private fun formatStartTime(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    return DateTimeFormat.transformLongToTime(timestamp)
}

@Preview(showBackground = true)
@Composable
private fun EventParticipantGroupScreenPreview() {
    MaterialTheme {
        Surface {
            EventParticipantGroupContent(
                participantGroup = EventParticipantGroup(
                    groupId = "1",
                    title = "М21",
                    description = "Мужчины 21 год и старше",
                    maxParticipant = 100,
                    registeredParticipant = 4,
                    distanceName = "Длинная",
                    distanceLengthMeters = 7500,
                    distanceClimbMeters = 280,
                    distanceControlsCount = 12
                ),
                state = EventParticipantGroupState(
                    eventStatus = EventStatus.REGISTRATION,
                    participants = listOf(
                        OrienteeringParticipant(
                            id = "id1", userId = "u1", firstName = "Иван", lastName = "Иванов",
                            groupId = 1, groupName = "М21", competitionId = "1", commandName = "СК Компас",
                            startNumber = "101", startTime = 1700000000000L, chipNumber = "111", comment = "", isChipGiven = true
                        ),
                        OrienteeringParticipant(
                            id = "id2", userId = "u2", firstName = "Пётр", lastName = "Петров",
                            groupId = 1, groupName = "М21", competitionId = "1", commandName = "СК Буссоль",
                            startNumber = "102", startTime = 1700000120000L, chipNumber = "222", comment = "", isChipGiven = true
                        ),
                        OrienteeringParticipant(
                            id = "id3", userId = "u3", firstName = "Сидор", lastName = "Сидоров",
                            groupId = 1, groupName = "М21", competitionId = "1", commandName = "",
                            startNumber = "103", startTime = 1700000240000L, chipNumber = "333", comment = "", isChipGiven = false
                        ),
                        OrienteeringParticipant(
                            id = "id4", userId = "u4", firstName = "Алексей", lastName = "Алексеев",
                            groupId = 1, groupName = "М21", competitionId = "1", commandName = "СК Азимут",
                            startNumber = "", startTime = 0L, chipNumber = "", comment = "", isChipGiven = false
                        )
                    )
                ),
                onAction = {}
            )
        }
    }
}
