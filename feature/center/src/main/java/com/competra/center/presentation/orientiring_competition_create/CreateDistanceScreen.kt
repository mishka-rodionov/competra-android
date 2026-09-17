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
import com.competra.designsystem.theme.Dimens
import com.competra.center.data.creator.OrienteeringCreatorAction
import com.competra.center.data.creator.OrienteeringCreatorState
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.resources.R
import org.koin.androidx.compose.koinViewModel

/**
 * Четвертый экран создания соревнования: Настройка дистанций.
 * 
 * Позволяет добавлять, редактировать и просматривать список дистанций для соревнования.
 * 
 * @param competitionId Идентификатор соревнования.
 * @param viewModel Вьюмодель процесса создания.
 */
@Composable
fun CreateDistanceScreen(
    competitionId: String,
    viewModel: OrienteeringCreatorViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize(competitionId)
    }

    CreateDistanceContent(
        state = state,
        onBack = viewModel::back,
        onNext = viewModel::saveStepFour,
        onAction = viewModel::onAction
    )
}

/**
 * Контент экрана настройки дистанций.
 * Выделен отдельно для поддержки Preview.
 */
@Composable
private fun CreateDistanceContent(
    state: OrienteeringCreatorState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onAction: (OrienteeringCreatorAction) -> Unit
) {
    // Дистанция может быть создана до того, как организатор выбрал текущий режим старта/систему
    // отметки (например, автозаполнением тестовыми данными или после смены режима на уже созданных
    // дистанциях) — тогда обязательное поле останется незаполненным и чтение чипа на старте/финише
    // потом уйдёт в DSQ. Не даём продолжить мастер, пока такие дистанции не исправлены.
    val isStartCpRequired = state.startTimeMode == StartTimeMode.BY_START_STATION
    val isFinishCpRequired = state.punchingSystem in setOf(
        PunchingSystem.SPORTIDUINO,
        PunchingSystem.SPORTIDENT,
        PunchingSystem.SFR
    )
    val incompleteDistances = state.distances.filter { d ->
        (isFinishCpRequired && d.finishControlPoint == null) || (isStartCpRequired && d.startControlPoint == null)
    }

    Scaffold(
        bottomBar = {
            Column {
                if (incompleteDistances.isNotEmpty()) {
                    Text(
                        text = "У дистанций (${incompleteDistances.joinToString { it.name ?: "без названия" }}) " +
                            "не заполнено обязательное поле — отредактируйте их перед тем, как продолжить",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp)
                    )
                }
                NavigationButtons(
                    onBack = onBack,
                    onNext = onNext,
                    nextEnabled = state.distances.isNotEmpty() && incompleteDistances.isEmpty()
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Dimens.SIZE_BASE.dp)
        ) {
            Text(
                text = "Шаг 4: Дистанции",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            // Список созданных дистанций
            if (state.distances.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Добавьте хотя бы одну дистанцию",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
                ) {
                    itemsIndexed(state.distances) { index, distance ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = distance.name ?: "Без названия", fontWeight = FontWeight.Bold)
                                    Text(text = "Длина: ${distance.lengthMeters} м, КП: ${distance.controlsCount}")
                                }
                                
                                IconButton(onClick = { onAction(OrienteeringCreatorAction.EditDistanceDialog(index)) }) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.edit),
                                        contentDescription = "Edit distance",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            Button(
                onClick = { onAction(OrienteeringCreatorAction.ShowDistanceCreateDialog) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Добавить дистанцию")
            }
        }
    }

    // Диалог создания/редактирования дистанции
    if (state.isShowDistanceCreateDialog) {
        DistanceEditor(
            userAction = onAction,
            state = state
        )
    }
}

@Preview(showBackground = true, name = "Empty list")
@Composable
private fun CreateDistanceEmptyPreview() {
    MaterialTheme {
        CreateDistanceContent(
            state = OrienteeringCreatorState(),
            onBack = {},
            onNext = {},
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "With distances")
@Composable
private fun CreateDistanceFilledPreview() {
    MaterialTheme {
        CreateDistanceContent(
            state = OrienteeringCreatorState(
                distances = listOf(
                    Distance(
                        remoteId = 1,
                        competitionId = "1",
                        name = "Длинная",
                        lengthMeters = 5000,
                        climbMeters = 150,
                        controlsCount = 15,
                        controlPoints = emptyList()
                    ),
                    Distance(
                        remoteId = 2,
                        competitionId = "1",
                        name = "Короткая",
                        lengthMeters = 2000,
                        climbMeters = 40,
                        controlsCount = 8,
                        controlPoints = emptyList()
                    )
                )
            ),
            onBack = {},
            onNext = {},
            onAction = {}
        )
    }
}
