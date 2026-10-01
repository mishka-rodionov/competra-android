package com.competra.eventdetails.presentation.link_requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.competra.designsystem.components.clickRipple
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.Gender
import com.competra.domain.models.participant_link.CompetitionLinkRequest
import com.competra.domain.models.participant_link.LinkRequestSource
import com.competra.domain.models.participant_link.LinkRequestStatus
import com.competra.eventdetails.presentation.result_links.color
import com.competra.eventdetails.presentation.result_links.label
import com.competra.eventdetails.presentation.result_links.linkDetails
import com.competra.resources.R
import org.koin.androidx.compose.koinViewModel

/**
 * Экран организатора: заявки спортсменов на привязку вручную внесённых результатов к их профилям.
 * Заявки одного человека собраны в одну карточку — организатор проверяет человека, а не строку.
 *
 * @param competitionId Идентификатор соревнования.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionLinkRequestsScreen(
    competitionId: String,
    viewModel: CompetitionLinkRequestsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val onAction = viewModel::onAction

    LaunchedEffect(competitionId) { viewModel.initialize(competitionId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заявки на привязку") },
                navigationIcon = {
                    IconButton(onClick = { onAction(CompetitionLinkRequestsAction.Back) }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back_24px),
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // Порядок заявителей — по первой (самой свежей) заявке.
        val byApplicant = state.pending.groupBy { it.userId }.values.toList()
        var showProcessed by remember { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.SIZE_BASE.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
        ) {
            item {
                Text(
                    text = "Спортсмены, которых вы внесли вручную, просят привязать результаты к своим профилям. " +
                        "После одобрения результат появится в профиле спортсмена и в рейтингах будет засчитан ему.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (byApplicant.isEmpty()) {
                item {
                    Text(
                        text = "Новых заявок нет",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = Dimens.SIZE_BASE.dp)
                    )
                }
            } else {
                items(byApplicant, key = { it.first().userId }) { group ->
                    ApplicantCard(requests = group, state = state, onAction = onAction)
                }
            }

            if (state.processed.isNotEmpty()) {
                item {
                    TextButton(onClick = { showProcessed = !showProcessed }) {
                        Text("${if (showProcessed) "Скрыть" else "Показать"} обработанные (${state.processed.size})")
                    }
                }
                if (showProcessed) {
                    items(state.processed, key = { "p-${it.id}" }) { request ->
                        ProcessedItem(request = request, isBusy = state.isBusy, onAction = onAction)
                    }
                }
            }
        }
    }

    state.unlinkingParticipantId?.let {
        AlertDialog(
            onDismissRequest = { onAction(CompetitionLinkRequestsAction.CancelUnlink) },
            title = { Text("Отвязать от профиля?") },
            text = { Text("Результат пропадёт из профиля спортсмена.") },
            confirmButton = {
                TextButton(onClick = { onAction(CompetitionLinkRequestsAction.ConfirmUnlink) }, enabled = !state.isBusy) {
                    Text("Отвязать", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(CompetitionLinkRequestsAction.CancelUnlink) }) { Text("Отмена") }
            }
        )
    }
}

private fun applicantName(r: CompetitionLinkRequest): String =
    "${r.userLastName} ${r.userFirstName}".trim().ifBlank { "Пользователь без имени" }

private fun participantDetails(r: CompetitionLinkRequest): String = linkDetails(
    "${r.participantLastName} ${r.participantFirstName}".trim(),
    r.groupName,
    r.startNumber.takeIf { it > 0 }?.let { "№$it" },
    r.commandName,
    r.result.label()
)

private fun warningsOf(r: CompetitionLinkRequest): List<String> = buildList {
    if (r.userAlreadyInCompetition) {
        add("Заявитель уже есть в протоколе этого соревнования. Удалите лишнюю запись в стартовом протоколе, затем одобрите заявку")
    }
    if (!r.nameMatches) add("Имя в протоколе отличается от имени в профиле заявителя")
    r.eligibilityWarning?.let(::add)
    if (r.competingRequests > 0) add("На этот результат есть ещё заявки от других пользователей: ${r.competingRequests}")
}

@Composable
private fun ApplicantCard(
    requests: List<CompetitionLinkRequest>,
    state: CompetitionLinkRequestsState,
    onAction: (CompetitionLinkRequestsAction) -> Unit
) {
    val first = requests.first()
    val meta = linkDetails(
        first.userBirthYear?.let { "$it г.р." },
        when (first.userGender) {
            Gender.MALE -> "муж."
            Gender.FEMALE -> "жен."
            else -> null
        }
    )
    val approvable = requests.filterNot { it.userAlreadyInCompetition }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Dimens.SIZE_BASE.dp)) {
            Text(applicantName(first), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            requests.forEach { request ->
                HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SIZE_HALF.dp))
                Text("Результат: ${participantDetails(request)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (request.source == LinkRequestSource.SUGGESTION) "Нашёл по совпадению имени"
                    else "Выбрал результат в протоколе сам",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                warningsOf(request).forEach { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (state.rejectingId == request.id) {
                    OutlinedTextField(
                        value = state.rejectComment,
                        onValueChange = { onAction(CompetitionLinkRequestsAction.UpdateRejectComment(it)) },
                        label = { Text("Причина (необязательно) — спортсмен её увидит") },
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.SIZE_HALF.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.SIZE_HALF.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !state.isBusy,
                            onClick = { onAction(CompetitionLinkRequestsAction.CancelReject) }
                        ) { Text("Отмена") }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !state.isBusy,
                            onClick = { onAction(CompetitionLinkRequestsAction.ConfirmReject) }
                        ) { Text("Отклонить") }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.SIZE_HALF.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !state.isBusy,
                            onClick = { onAction(CompetitionLinkRequestsAction.StartReject(request.id)) }
                        ) { Text("Отклонить") }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !state.isBusy && !request.userAlreadyInCompetition,
                            onClick = { onAction(CompetitionLinkRequestsAction.Approve(listOf(request))) }
                        ) { Text("Одобрить") }
                    }
                }
                state.errors[request.id]?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            if (approvable.size > 1) {
                Button(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.SIZE_HALF.dp),
                    enabled = !state.isBusy,
                    onClick = { onAction(CompetitionLinkRequestsAction.Approve(approvable)) }
                ) { Text("Одобрить все (${approvable.size})") }
            }
        }
    }
}

@Composable
private fun ProcessedItem(
    request: CompetitionLinkRequest,
    isBusy: Boolean,
    onAction: (CompetitionLinkRequestsAction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Dimens.SIZE_BASE.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(applicantName(request), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(request.status.label(), color = request.status.color(), fontWeight = FontWeight.Medium)
            }
            Text(
                participantDetails(request),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            request.comment?.takeIf { it.isNotBlank() }?.let {
                Text("Комментарий: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (request.status == LinkRequestStatus.APPROVED) {
                Text(
                    text = "Отвязать от профиля",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(top = Dimens.SIZE_HALF.dp)
                        .clickRipple(onClick = { if (!isBusy) onAction(CompetitionLinkRequestsAction.AskUnlink(request.participantId)) })
                )
            }
        }
    }
}
