package com.competra.center.presentation.orientiring_competition_create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.center.presentation.common.GroupsWithoutDistanceBanner
import com.competra.center.presentation.common.NoDistanceBadge
import com.competra.designsystem.theme.Dimens
import com.competra.center.data.creator.OrienteeringCreatorAction
import com.competra.center.data.creator.OrienteeringCreatorState
import com.competra.domain.models.Gender
import com.competra.domain.models.ParticipantGroup
import com.competra.domain.models.orienteering.withoutDistance
import com.competra.resources.R
import org.koin.androidx.compose.koinViewModel

/**
 * Пятый экран создания соревнования: Настройка групп участников.
 * 
 * @param competitionId Идентификатор соревнования.
 * @param viewModel Вьюмодель процесса создания.
 */
@Composable
fun CreateParticipantGroupScreen(
    competitionId: String,
    viewModel: OrienteeringCreatorViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize(competitionId)
    }

    CreateParticipantGroupContent(
        state = state,
        onBack = viewModel::back,
        onNext = viewModel::finishCreation,
        onAction = viewModel::onAction
    )

    // Отображение диалога создания/редактирования группы
    if (state.isShowGroupCreateDialog) {
        ParticipantGroupEditor(
            userAction = viewModel::onAction,
            state = state
        )
    }

    if (state.isShowStandardGroupsDialog) {
        StandardGroupsDialog(
            distances = state.distances,
            existingTitles = state.participantGroups.map { it.title },
            onDismiss = { viewModel.onAction(OrienteeringCreatorAction.HideStandardGroupsDialog) },
            onConfirm = { titles, distanceId ->
                viewModel.onAction(OrienteeringCreatorAction.AddStandardGroups(titles, distanceId))
            }
        )
    }
}

/**
 * Контент экрана настройки групп участников.
 * Выделен отдельно для поддержки Preview.
 */
@Composable
private fun CreateParticipantGroupContent(
    state: OrienteeringCreatorState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onAction: (OrienteeringCreatorAction) -> Unit
) {
    val snackbarHostState = rememberWizardSnackbar(state.message) { onAction(OrienteeringCreatorAction.MessageShown) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationButtons(
                onBack = onBack,
                onNext = onNext,
                nextText = "Завершить",
                nextEnabled = state.participantGroups.isNotEmpty()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Dimens.SIZE_BASE.dp)
        ) {
            Text(
                text = "Шаг 5: Группы участников",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            // Список созданных групп
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
            ) {
                itemsIndexed(state.participantGroups) { index, group ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        onClick = { 
                            onAction(OrienteeringCreatorAction.EditGroupDialog(index))
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = group.title, fontWeight = FontWeight.Bold)
                                if (group.minAge != null || group.maxAge != null) {
                                    Text(
                                        text = "Возраст: ${group.minAge ?: 0} - ${group.maxAge ?: "∞"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                val distance = state.distances.firstOrNull { it.id == group.distanceId }
                                if (distance != null) {
                                    Text(
                                        text = "Дистанция: ${distance.name ?: "без названия"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    NoDistanceBadge(modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            IconButton(onClick = { onAction(OrienteeringCreatorAction.DeleteGroup(index)) }) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.delete),
                                    contentDescription = "Delete group",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            GroupsWithoutDistanceBanner(titles = state.participantGroups.withoutDistance(state.distances).map { it.title })

            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

            OutlinedButton(
                onClick = { onAction(OrienteeringCreatorAction.ShowStandardGroupsDialog) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Добавить стандартные группы")
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

            Button(
                onClick = { onAction(OrienteeringCreatorAction.ShowGroupCreateDialog) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Добавить группу")
            }
        }
    }
}

@Preview(showBackground = true, name = "Empty list")
@Composable
private fun CreateParticipantGroupEmptyPreview() {
    MaterialTheme {
        CreateParticipantGroupContent(
            state = OrienteeringCreatorState(),
            onBack = {},
            onNext = {},
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "With groups")
@Composable
private fun CreateParticipantGroupFilledPreview() {
    MaterialTheme {
        CreateParticipantGroupContent(
            state = OrienteeringCreatorState(
                participantGroups = listOf(
                    ParticipantGroup(
                        groupId = 1, competitionId = "1", title = "М21", distanceId = 1, minAge = 21,
                        gender = Gender.MALE,
                    ),
                    ParticipantGroup(groupId = 2, competitionId = "1", title = "Ж18", distanceId = 2, minAge = 16, maxAge = 18, gender = Gender.FEMALE)
                )
            ),
            onBack = {},
            onNext = {},
            onAction = {}
        )
    }
}
