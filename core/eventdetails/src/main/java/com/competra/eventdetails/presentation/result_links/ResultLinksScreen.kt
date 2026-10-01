package com.competra.eventdetails.presentation.result_links

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.competra.designsystem.components.clickRipple
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.participant_link.LinkRequest
import com.competra.domain.models.participant_link.LinkRequestStatus
import com.competra.domain.models.participant_link.LinkSuggestion
import com.competra.resources.R
import com.competra.utils.DateTimeFormat
import org.koin.androidx.compose.koinViewModel

/**
 * Экран «Мои результаты в протоколах»: подсказки по имени среди участников, которых организаторы
 * внесли вручную, и заявки пользователя на привязку этих результатов к своему профилю.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultLinksScreen(viewModel: ResultLinksViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val onAction = viewModel::onAction

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои результаты в протоколах") },
                navigationIcon = {
                    IconButton(onClick = { onAction(ResultLinksAction.Back) }) {
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

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.SIZE_BASE.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
        ) {
            item {
                Text(
                    text = "Если организатор внёс вас в протокол вручную, результат не попадает в ваш профиль " +
                        "и рейтинги. Найдите такие результаты и отправьте заявку — организатор соревнования её проверит.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.SIZE_BASE.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Похожие на ваши", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (state.suggestions.size > 1) {
                        val allSelected = state.selected.size == state.suggestions.size
                        TextButton(onClick = { onAction(ResultLinksAction.ToggleSelectAll) }) {
                            Text(if (allSelected) "Снять все" else "Выбрать все")
                        }
                    }
                }
            }

            if (state.suggestions.isEmpty()) {
                item {
                    Text(
                        text = "Результатов с вашими фамилией и именем не найдено. Если организатор записал вас " +
                            "иначе, откройте свой результат в протоколе соревнования и нажмите «Это мой результат».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.suggestions, key = { "s-${it.participantId}" }) { suggestion ->
                    SuggestionItem(
                        suggestion = suggestion,
                        isSelected = suggestion.participantId in state.selectedIds,
                        onToggle = { onAction(ResultLinksAction.ToggleSelection(suggestion.participantId)) }
                    )
                }
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.selected.isNotEmpty(),
                        onClick = { onAction(ResultLinksAction.ShowConfirm) }
                    ) {
                        Text(
                            if (state.selected.isNotEmpty()) "Отправить заявку (${state.selected.size})"
                            else "Отметьте свои результаты"
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Мои заявки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = Dimens.SIZE_BASE.dp)
                )
            }
            if (state.requests.isEmpty()) {
                item {
                    Text(
                        text = "Вы ещё не подавали заявок",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.requests, key = { "r-${it.id}" }) { request ->
                    MyRequestItem(
                        request = request,
                        isCancelling = state.cancellingId == request.id,
                        onOpen = { onAction(ResultLinksAction.OpenCompetition(request.competitionId)) },
                        onCancel = { onAction(ResultLinksAction.CancelRequest(request)) }
                    )
                }
            }
        }
    }

    if (state.isConfirmShown) {
        LinkRequestConfirmDialog(
            lines = state.selected.map { "${it.competitionTitle} · ${suggestionDetails(it)}" },
            isSending = state.isSending,
            onConfirm = { onAction(ResultLinksAction.SendRequests) },
            onDismiss = { onAction(ResultLinksAction.HideConfirm) }
        )
    }
}

private fun suggestionDetails(s: LinkSuggestion): String =
    linkDetails("${s.lastName} ${s.firstName}".trim(), s.groupName, s.commandName, s.result.label())

@Composable
private fun SuggestionItem(suggestion: LinkSuggestion, isSelected: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickRipple(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.SIZE_HALF.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(suggestion.competitionTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    DateTimeFormat.transformLongToDisplayDate(suggestion.competitionStartDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(suggestionDetails(suggestion), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MyRequestItem(request: LinkRequest, isCancelling: Boolean, onOpen: () -> Unit, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickRipple(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Dimens.SIZE_BASE.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    request.competitionTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    request.status.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = request.status.color()
                )
            }
            Text(
                linkDetails(
                    DateTimeFormat.transformLongToDisplayDate(request.competitionStartDate),
                    "${request.participantLastName} ${request.participantFirstName}".trim(),
                    request.groupName
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (request.status == LinkRequestStatus.REJECTED && !request.comment.isNullOrBlank()) {
                Text(
                    "Комментарий организатора: ${request.comment}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (request.status == LinkRequestStatus.PENDING) {
                TextButton(onClick = onCancel, enabled = !isCancelling) {
                    Text(
                        if (isCancelling) "Отзываем…" else "Отозвать заявку",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
