package com.competra.center.presentation.main

import androidx.compose.foundation.background
import com.competra.designsystem.components.NetworkImage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.competra.designsystem.components.clickRipple
import com.competra.designsystem.theme.Dimens
import com.competra.center.data.CenterEffects
import com.competra.center.data.main.CenterState
import com.competra.domain.models.Competition
import com.competra.domain.models.Coordinates
import com.competra.domain.models.KindOfSport
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.models.orienteering.ResultsStatus
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.resources.R
import com.competra.ui.components.toFractionalRect
import com.competra.utils.DateTimeFormat
import org.koin.androidx.compose.koinViewModel

/**
 * Главный экран раздела "Центр" для управления событиями пользователя.
 */
@Composable
fun CenterScreen(viewModel: CenterViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val handleEffects = remember { viewModel::handleEffects }

    LaunchedEffect(null) {
        viewModel.initialize()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        CenterScreenContent(state, handleEffects)
    }

    state.deletingCompetition?.let { competition ->
        DeleteCompetitionDialog(
            competition = competition,
            onDismiss = { handleEffects(CenterEffects.HideDeleteCompetitionDialog) },
            onConfirm = { handleEffects(CenterEffects.DeleteCompetition(competition)) }
        )
    }
}

/**
 * Контент экрана в зависимости от авторизации.
 */
@Composable
private fun CenterScreenContent(state: CenterState, handleEffects: (CenterEffects) -> Unit) {
    if (state.isAuthed) {
        AuthorizedCenterContent(state, handleEffects)
    } else {
        UnauthorizedCenterView(handleEffects)
    }
}

/**
 * Вид экрана для авторизованного пользователя.
 */
@Composable
private fun AuthorizedCenterContent(state: CenterState, handleEffects: (CenterEffects) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.SIZE_BASE.dp)
    ) {
        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

        Text(
            text = "Управление событиями",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

        // Кнопка создания нового события
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            onClick = { handleEffects(CenterEffects.OpenKindOfSports) },
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_add_24px),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Создать новое событие", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
        StationToolsEntryCard(onClick = { handleEffects(CenterEffects.OpenStationTools) })

        Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))

        if (state.controlledEvents.isEmpty()) {
            EmptyControlledEventsView()
        } else {
            Text(
                text = "Мои старты (${state.controlledEvents.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ControlledEventsList(state, handleEffects)
        }
    }
}

/**
 * Список событий, которыми управляет пользователь.
 */
@Composable
private fun ControlledEventsList(state: CenterState, userAction: (CenterEffects) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp),
        contentPadding = PaddingValues(bottom = Dimens.SIZE_BASE.dp)
    ) {
        itemsIndexed(state.controlledEvents) { _, item ->
            EventControlCard(item.competition, item.competitionId, item, userAction)
        }
    }
}

/**
 * Карточка управляемого события.
 */
@Composable
private fun EventControlCard(
    competition: Competition,
    eventId: String,
    orienteeringCompetition: OrienteeringCompetition,
    userAction: (CenterEffects) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickRipple {
                userAction.invoke(CenterEffects.OpenOrienteeringEventControl(eventId))
            },
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(Dimens.SIZE_BASE.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Миниатюра события
            NetworkImage(
                url = competition.imageUrl,
                cropRect = competition.imageCropRect?.toFractionalRect(),
                contentDescription = null,
                fullscreenOnClick = false,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(Dimens.SIZE_HALF.dp))
            )

            Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = competition.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_location_on_24px),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = competition.address ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_date_range_24px), // Исправлено на правильный ресурс если доступен, иначе ic_date_range
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = DateTimeFormat.transformLongToDisplayDate(competition.startDate),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            if (competition.status != CompetitionStatus.FINISHED) {
                // Кнопка редактирования настроек события
                IconButton(
                    onClick = { userAction.invoke(CenterEffects.OpenOrienteeringEditor(eventId)) },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.edit),
                        contentDescription = "Edit event",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))
            }

            // Кнопка удаления события. Для завершённых соревнований обычно скрыта,
            // но тестовые соревнования можно удалять в любом статусе.
            if (competition.status != CompetitionStatus.FINISHED || competition.isTest) {
                IconButton(
                    onClick = {
                        userAction.invoke(
                            CenterEffects.ShowDeleteCompetitionDialog(
                                orienteeringCompetition
                            )
                        )
                    },
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.delete),
                        contentDescription = "Delete event",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Экран для неавторизованного пользователя.
 */
@Composable
private fun UnauthorizedCenterView(handleEffects: (CenterEffects) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SIZE_DOUBLE.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_build_24px),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
        Text(
            text = "Станьте организатором",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Войдите в аккаунт, чтобы создавать свои соревнования и управлять участниками.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))
        // Инструменты оборудования не требуют аккаунта — судья может настроить станции без входа.
        StationToolsEntryCard(onClick = { handleEffects(CenterEffects.OpenStationTools) })
    }
}

/**
 * Вход в инструменты «Станции и чипы»: чтение/проверка, очистка и запись чипов, настройка станций.
 */
@Composable
private fun StationToolsEntryCard(onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickRipple(onClick = onClick),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SIZE_BASE.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_build_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Станции и чипы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Проверка, очистка и запись чипов, настройка станций",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_chevron_forward_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Заглушка, если у организатора еще нет событий.
 */
@Composable
private fun EmptyControlledEventsView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "У вас пока нет созданных событий.\nНажмите кнопку выше, чтобы начать.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteCompetitionDialog(
    competition: OrienteeringCompetition,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.SIZE_BASE.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Удалить соревнование?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

            Text(
                text = competition.competition.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                onClick = onConfirm
            ) {
                Text(text = "Удалить", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
                onClick = onDismiss
            ) {
                Text(text = "Отмена", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
        }
    }
}

// Поправка для ресурса иконки даты, если в предыдущих шагах использовался ic_date_range_24px
private val R.datasource_ic_date_range_24px: Int get() = R.drawable.ic_date_range_24px

@Preview(showBackground = true)
@Composable
private fun CenterScreenAuthPreview() {
    CenterScreenContent(
        state = CenterState(
            isAuthed = true,
            controlledEvents = listOf(
                OrienteeringCompetition(
                    competitionId = "1",
                    competition = Competition(
                        title = "Чемпионат города по ориентированию",
                        startDate = System.currentTimeMillis(),
                        endDate = System.currentTimeMillis() + 86400000L,
                        address = "Москва, Парк Сокольники",
                        kindOfSport = KindOfSport.Orienteering,
                        description = "",
                        mainOrganizerId = "123",
                        coordinates = Coordinates(latitude = 0.0, longitude = 0.0),
                        status = CompetitionStatus.DRAFT,
                        resultsStatus = ResultsStatus.NOT_PUBLISHED,
                        timeZoneId = "Europe/Moscow"
                    ),
                    direction = OrienteeringDirection.FORWARD,
                    punchingSystem = PunchingSystem.PUNCH,
                    startTimeMode = StartTimeMode.STRICT
                )
            )
        ),
        handleEffects = {}
    )
}
