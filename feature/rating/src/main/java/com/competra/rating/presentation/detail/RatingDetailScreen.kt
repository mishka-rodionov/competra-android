package com.competra.rating.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.rating.RatingCompetition
import com.competra.domain.models.rating.RatingStanding
import com.competra.rating.data.detail.RatingDetailAction
import com.competra.rating.data.detail.mergeProblem
import com.competra.resources.R
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingDetailScreen(ratingId: String, viewModel: RatingDetailViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(ratingId) { viewModel.initialize(ratingId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.rating?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onAction(RatingDetailAction.BackClick) }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back_24px),
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    if (state.isAdmin) {
                        IconButton(onClick = { viewModel.onAction(RatingDetailAction.AddCompetitionClick) }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_add_24px),
                                contentDescription = stringResource(R.string.rating_detail_add_competition_action)
                            )
                        }
                        IconButton(onClick = { viewModel.onAction(RatingDetailAction.OpenDeleteConfirm) }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.delete),
                                contentDescription = stringResource(R.string.rating_detail_delete_action)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val rating = state.rating
        if (state.isLoading && rating == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (rating == null) return@Scaffold

        if (rating.groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.rating_detail_no_groups))
            }
            return@Scaffold
        }

        // Единый скроллящийся список: иначе при длинной таблице результатов раздел
        // "Соревнования рейтинга" уходит за пределы экрана и становится недостижим.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Dimens.SIZE_BASE.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_QUARTER.dp)
        ) {
            item {
                ScrollableTabRow(selectedTabIndex = rating.groups.indexOfFirst { it.id == state.selectedGroupId }.coerceAtLeast(0)) {
                    rating.groups.forEach { group ->
                        Tab(
                            selected = group.id == state.selectedGroupId,
                            onClick = { viewModel.onAction(RatingDetailAction.SelectGroup(group.id)) },
                            text = { Text(group.title) }
                        )
                    }
                }
            }

            if (state.isAdmin && state.duplicateGroups.isNotEmpty() && !state.isSelectingForMerge) {
                item {
                    DuplicatesCard(
                        groups = state.duplicateGroups,
                        onMerge = { keys -> viewModel.onAction(RatingDetailAction.OpenMerge(keys)) }
                    )
                }
            }
            if (state.isAdmin && state.standings.size > 1) {
                item {
                    MergeSelectionBar(
                        isSelecting = state.isSelectingForMerge,
                        selectedCount = state.selectedKeys.size,
                        onStart = { viewModel.onAction(RatingDetailAction.StartMergeSelection) },
                        onCancel = { viewModel.onAction(RatingDetailAction.CancelMergeSelection) },
                        onMerge = { viewModel.onAction(RatingDetailAction.OpenMergeForSelected) }
                    )
                }
            }

            if (state.isStandingsLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.padding(Dimens.SIZE_BASE.dp))
                    }
                }
            } else if (state.standings.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(Dimens.SIZE_BASE.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.rating_detail_standings_empty))
                    }
                }
            } else {
                items(state.standings, key = { "standing_${it.participantKey}" }) { standing ->
                    Box(modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp)) {
                        StandingRow(
                            standing = standing,
                            selected = if (state.isSelectingForMerge) standing.participantKey in state.selectedKeys else null,
                            onClick = { viewModel.onAction(RatingDetailAction.StandingClick(standing.participantKey)) }
                        )
                    }
                }
            }

            if (state.competitions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.rating_detail_competitions_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(Dimens.SIZE_BASE.dp)
                    )
                }
                items(state.competitions, key = { "competition_${it.id}" }) { competition ->
                    Box(modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp)) {
                        CompetitionRow(
                            competition = competition,
                            isAdmin = state.isAdmin,
                            onRemove = { viewModel.onAction(RatingDetailAction.RemoveCompetitionClick(competition.competitionId)) },
                            onEditMapping = { viewModel.onAction(RatingDetailAction.EditMappingClick(competition.competitionId)) }
                        )
                    }
                }
            }
        }
    }

    state.mergeEntries?.let { entries ->
        MergeDialog(
            entries = entries,
            targetKey = state.mergeTargetKey,
            isMerging = state.isMerging,
            error = state.mergeError,
            onSelectTarget = { viewModel.onAction(RatingDetailAction.SelectMergeTarget(it)) },
            onDismiss = { viewModel.onAction(RatingDetailAction.CloseMerge) },
            onConfirm = { viewModel.onAction(RatingDetailAction.ConfirmMerge) }
        )
    }

    if (state.isDeleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(RatingDetailAction.CloseDeleteConfirm) },
            title = { Text(stringResource(R.string.rating_detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.rating_detail_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(RatingDetailAction.ConfirmDelete) }) {
                    Text(stringResource(R.string.rating_detail_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(RatingDetailAction.CloseDeleteConfirm) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DuplicatesCard(groups: List<List<RatingStanding>>, onMerge: (List<String>) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(Dimens.SIZE_HALFER.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_QUARTER.dp)
        ) {
            Text(stringResource(R.string.rating_detail_duplicates_title), style = MaterialTheme.typography.titleSmall)
            groups.forEach { group ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.joinToString(" · ") { entry ->
                            "${entry.lastName} ${entry.firstName}" + if (entry.userId != null) " (аккаунт)" else ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { onMerge(group.map { it.participantKey }) }) {
                        Text(stringResource(R.string.rating_detail_merge_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeSelectionBar(
    isSelecting: Boolean,
    selectedCount: Int,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onMerge: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SIZE_BASE.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelecting) {
            Text(
                text = stringResource(R.string.rating_detail_merge_select_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
            TextButton(onClick = onMerge, enabled = selectedCount >= 2) {
                Text(stringResource(R.string.rating_detail_merge_selected_action, selectedCount))
            }
        } else {
            TextButton(onClick = onStart) { Text(stringResource(R.string.rating_detail_merge_manual_action)) }
        }
    }
}

@Composable
private fun MergeDialog(
    entries: List<RatingStanding>,
    targetKey: String?,
    isMerging: Boolean,
    error: String?,
    onSelectTarget: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val target = entries.firstOrNull { it.participantKey == targetKey } ?: entries.first()
    val problem = mergeProblem(target, entries)
    val targetName = "${target.lastName} ${target.firstName}".trim()
    AlertDialog(
        onDismissRequest = { if (!isMerging) onDismiss() },
        title = { Text(stringResource(R.string.rating_detail_merge_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_QUARTER.dp)) {
                Text(stringResource(R.string.rating_detail_merge_dialog_hint), style = MaterialTheme.typography.bodySmall)
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectTarget(entry.participantKey) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = entry.participantKey == target.participantKey,
                            onClick = { onSelectTarget(entry.participantKey) }
                        )
                        Column {
                            Text("${entry.lastName} ${entry.firstName}", style = MaterialTheme.typography.bodyMedium)
                            val kind = stringResource(
                                if (entry.userId != null) R.string.rating_detail_merge_account else R.string.rating_detail_merge_no_account
                            )
                            Text(
                                text = "$kind · ${entry.breakdown.size} ${startsCountLabel(entry.breakdown.size)} · ${entry.totalPoints}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = problem ?: stringResource(
                        if (target.userId != null) R.string.rating_detail_merge_link_explanation
                        else R.string.rating_detail_merge_rename_explanation,
                        targetName
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = problem == null && !isMerging) {
                Text(stringResource(if (isMerging) R.string.rating_detail_merging else R.string.rating_detail_merge_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isMerging) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
private fun StandingRow(standing: RatingStanding, selected: Boolean?, onClick: () -> Unit) {
    val startsCount = standing.breakdown.size
    val isTopThree = standing.rank in 1..3
    val badgeContainer = when (standing.rank) {
        1 -> Color(0xFFE0B00A)
        2 -> Color(0xFFA9B0B8)
        3 -> Color(0xFFC17A3E)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeContent = if (isTopThree) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (isTopThree) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.SIZE_HALFER.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected != null) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            Box(
                modifier = Modifier.size(Dimens.SIZE_DOUBLE_QUARTER.dp).clip(CircleShape).background(badgeContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "${standing.rank}", style = MaterialTheme.typography.titleSmall, color = badgeContent)
            }
            Spacer(modifier = Modifier.width(Dimens.SIZE_HALFER.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = standing.displayName, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(Dimens.SIZE_QUARTER.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_directions_run_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.SIZE_TEN_QUARTER.dp)
                    )
                    Spacer(modifier = Modifier.width(Dimens.SIZE_QUARTER.dp))
                    Text(
                        text = "$startsCount ${startsCountLabel(startsCount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${standing.totalPoints}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.rating_detail_points_column),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun startsCountLabel(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "стартов"
        mod10 == 1 -> "старт"
        mod10 in 2..4 -> "старта"
        else -> "стартов"
    }
}

@Composable
private fun CompetitionRow(
    competition: RatingCompetition,
    isAdmin: Boolean,
    onRemove: () -> Unit,
    onEditMapping: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.SIZE_QUARTER.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = competition.competitionTitle, modifier = Modifier.weight(1f))
        if (isAdmin) {
            IconButton(onClick = onEditMapping) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.edit),
                    contentDescription = stringResource(R.string.rating_detail_edit_mapping_action)
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.delete),
                    contentDescription = stringResource(R.string.rating_detail_remove_competition_action)
                )
            }
        }
    }
}
