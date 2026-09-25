package com.competra.center.presentation.event_control.orienteering

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.competra.designsystem.components.DSBottomDialog
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.components.clickRipple
import com.competra.designsystem.theme.Dimens
import com.competra.center.data.event_control.OrientEventControlAction
import com.competra.center.data.event_control.OrienteeringEventControlState
import com.competra.domain.models.Competition
import com.competra.domain.models.KindOfSport
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.models.orienteering.OrienteeringCompetition
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.PunchingSystem
import com.competra.domain.models.orienteering.ResultsStatus
import com.competra.domain.models.orienteering.StartTimeMode
import com.competra.resources.R
import org.koin.androidx.compose.koinViewModel

/**
 * Главный экран управления событием по ориентированию.
 */
@Composable
fun OrienteeringEventControlScreen(
    viewModel: OrienteeringEventControlViewModel = koinViewModel(),
    windowSizeClass: WindowSizeClass
) {
    val state by viewModel.state.collectAsState()
    val isExpanded = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    LaunchedEffect(Unit) {
        viewModel.onAction(OrientEventControlAction.Reload)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!state.isCompetitionRunning && !state.isTimerRunning) {
                viewModel.onAction(OrientEventControlAction.StopService)
            }
        }
    }

    OrienteeringEventControlScreenContent(
        state = state,
        isExpanded = isExpanded,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrienteeringEventControlScreenContent(
    state: OrienteeringEventControlState,
    isExpanded: Boolean,
    onAction: (OrientEventControlAction) -> Unit
) {
    val scrollState = rememberScrollState()

    val startTimeMode = state.competition?.startTimeMode
    val isByStartStation = startTimeMode == StartTimeMode.BY_START_STATION
    // Для старта по стартовой станции жеребьёвки не бывает вообще — секции, зависящие от неё
    // (выдача чипов, старт), открываются сразу, без условия isDrawConducted.
    val isDrawConducted = state.isDrawConducted || isByStartStation
    val showStartSection = !state.isFinished && isDrawConducted &&
        (state.isCompetitionRunning || state.allChipsDistributed)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(Dimens.SIZE_BASE.dp)
        ) {
            Text(
                text = state.competitionTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            // 4 NFC-кнопки управления чипом — без изменений
            SectionHeader(title = "Панель управления")
            OrienteeringEventControlContent(isExpanded, onAction)

            Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))

            // Баннеры статуса — только когда соревнование запущено
            if (state.isCompetitionRunning) {
                if (state.isTimerRunning) {
                    CountdownBanner(
                        countdownMillis = state.countdownMillis,
                        onCancel = { onAction(OrientEventControlAction.CancelCountdown) }
                    )
                } else {
                    if (!state.isTimerRunning) {
                        StopwatchBanner(elapsedMillis = state.stopwatchMillis)
                        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                    }
                    if (state.allParticipantsFinished) {
                        AllParticipantsFinishedBanner()
                    } else {
                        CompetitionStartedBanner()
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
            }

            // Регистрация — до запуска соревнования. Завершение регистрации запрещает участникам
            // отменять заявку, после чего организатор спокойно правит стартовый протокол.
            if (!state.isFinished && !state.isCompetitionRunning) {
                SectionHeader(title = "Регистрация")
                RegistrationSection(
                    isRegistrationClosed = state.isRegistrationClosed,
                    onCloseRegistration = { onAction(OrientEventControlAction.ShowCloseRegistrationDialog) }
                )
                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
            }

            // Список разделов с прогрессивным раскрытием
            SectionHeader(title = "Разделы")

            // 1. Стартовый протокол — всегда
            NavigationRow(
                text = "Стартовый протокол",
                onClick = { onAction(OrientEventControlAction.OpenParticipantLists) }
            )

            // 2. Жеребьёвка — если добавлены участники и соревнование не запущено. При старте по
            // стартовой станции жеребьёвки не бывает вообще — время старта берётся из отметки на чипе.
            if (!state.isFinished && !state.isCompetitionRunning && state.participantGroups.isNotEmpty() && !isByStartStation) {
                NavigationRow(
                    text = "Жеребьёвка",
                    onClick = { onAction(OrientEventControlAction.OpenDrawParticipants) }
                )
            }

            // 3. Стартовая решётка — после жеребьёвки (до запуска как предпросмотр и во время старта).
            // Без жеребьёвки (BY_START_STATION) концепция стартовых минут неприменима — скрываем.
            if (isDrawConducted && !isByStartStation) {
                NavigationRow(
                    text = "Стартовая решётка",
                    onClick = { onAction(OrientEventControlAction.OpenStartGrid) }
                )
            }

            // 4. Выдача чипов — после жеребьёвки, до запуска
            if (!state.isFinished && !state.isCompetitionRunning && isDrawConducted) {
                NavigationRow(
                    text = "Выдача чипов",
                    onClick = { onAction(OrientEventControlAction.OpenGetOrienteeringChip) }
                )
            }

            // 5. Результаты — после завершения или во время соревнования (текущие финишные результаты)
            if (state.isFinished || state.isCompetitionRunning) {
                NavigationRow(
                    text = "Результаты",
                    onClick = { onAction(OrientEventControlAction.OpenResults) }
                )
            }

            // Секция старта/завершения
            if (showStartSection) {
                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
                SectionHeader(title = "Старт")

                if (!state.isCompetitionRunning) {
                    val canLaunch = startTimeMode == StartTimeMode.USER_SET ||
                        startTimeMode == StartTimeMode.STRICT ||
                        startTimeMode == StartTimeMode.BY_START_STATION

                    if (startTimeMode == StartTimeMode.USER_SET) {
                        DSTextInput(
                            modifier = Modifier.fillMaxWidth(),
                            text = state.countdownTimerInput,
                            label = { Text(stringResource(R.string.label_countdown_timer)) },
                            onValueChanged = { onAction(OrientEventControlAction.UpdateCountdownTimerInput(it)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                    }

                    if (!state.allChipsDistributed) {
                        Text(
                            text = "Не все участники получили чипы",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = Dimens.SIZE_HALF.dp)
                        )
                    }

                    if (canLaunch) {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            onClick = { onAction(OrientEventControlAction.ShowStartConfirmDialog) },
                            enabled = state.allChipsDistributed,
                            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                if (isByStartStation) "ОТКРЫТЬ СТАРТ" else "ЗАПУСТИТЬ ТАЙМЕР",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (state.isCompetitionRunning) {
                    Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        onClick = { onAction(OrientEventControlAction.ShowStopConfirmDialog) },
                        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("ЗАВЕРШИТЬ СОРЕВНОВАНИЕ", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))
        }
    }

    if (state.isShowStartConfirmDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        DSBottomDialog(
            sheetState = sheetState,
            onDismiss = { onAction(OrientEventControlAction.HideStartConfirmDialog) },
            sheetContent = {
                StartConfirmContent(
                    isByStartStation = isByStartStation,
                    onConfirm = { onAction(OrientEventControlAction.StartCompetition) },
                    onCancel = { onAction(OrientEventControlAction.HideStartConfirmDialog) }
                )
            }
        )
    }

    if (state.isShowCloseRegistrationDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        DSBottomDialog(
            sheetState = sheetState,
            onDismiss = { onAction(OrientEventControlAction.HideCloseRegistrationDialog) },
            sheetContent = {
                CloseRegistrationConfirmContent(
                    onConfirm = { onAction(OrientEventControlAction.CloseRegistration) },
                    onCancel = { onAction(OrientEventControlAction.HideCloseRegistrationDialog) }
                )
            }
        )
    }

    if (state.isShowStopConfirmDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        DSBottomDialog(
            sheetState = sheetState,
            onDismiss = { onAction(OrientEventControlAction.HideStopConfirmDialog) },
            sheetContent = {
                StopConfirmContent(
                    onConfirm = { onAction(OrientEventControlAction.StopCompetition) },
                    onCancel = { onAction(OrientEventControlAction.HideStopConfirmDialog) }
                )
            }
        )
    }
}

@Composable
private fun StartConfirmContent(
    isByStartStation: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(Dimens.SIZE_BASE.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = if (isByStartStation) "Открыть старт?" else "Запустить соревнование?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
        Text(
            text = if (isByStartStation) {
                "Участники смогут начинать дистанцию по своей отметке на стартовой станции — общего времени старта нет."
            } else {
                "Участники начнут стартовать согласно расписанию. Убедитесь, что всё готово."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
        ) {
            Text(if (isByStartStation) "Открыть старт" else "Запустить", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
        ) {
            Text("Отмена", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
    }
}

@Composable
private fun StopConfirmContent(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(Dimens.SIZE_BASE.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "Завершить соревнование?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
        Text(
            text = "Всем участникам без финишного результата будет присвоен статус DNF.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Завершить", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
        ) {
            Text("Отмена", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
    }
}

/**
 * Секция регистрации: кнопка досрочного завершения регистрации или отметка, что она уже закрыта.
 */
@Composable
private fun RegistrationSection(isRegistrationClosed: Boolean, onCloseRegistration: () -> Unit) {
    if (isRegistrationClosed) {
        Text(
            text = "Регистрация завершена. Участники не могут зарегистрироваться или отменить регистрацию.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            onClick = onCloseRegistration,
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
        ) {
            Text("Завершить регистрацию", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CloseRegistrationConfirmContent(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(Dimens.SIZE_BASE.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "Завершить регистрацию?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
        Text(
            text = "Участники больше не смогут зарегистрироваться или отменить регистрацию. " +
                "Стартовый протокол сможете менять только вы. " +
                "Открыть регистрацию снова можно в настройках соревнования.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
        ) {
            Text("Завершить", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
        ) {
            Text("Отмена", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = Dimens.SIZE_HALF.dp)
    )
}

/**
 * Баннер обратного отсчёта — показывается пока идёт таймер до старта.
 */
@Composable
private fun CountdownBanner(countdownMillis: Long, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SIZE_BASE.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "До старта",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val minutes = (countdownMillis / 1000) / 60
            val seconds = (countdownMillis / 1000) % 60
            Text(
                text = "%02d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
            ) {
                Text("Отменить старт", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Баннер «Соревнование начато, старт дан» — показывается после истечения таймера.
 */
@Composable
private fun CompetitionStartedBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SIZE_BASE.dp, vertical = Dimens.SIZE_BASER.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))
            Text(
                text = "Соревнование начато • Старт дан",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun AllParticipantsFinishedBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SIZE_BASE.dp, vertical = Dimens.SIZE_BASER.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape)
            )
            Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))
            Text(
                text = "Все участники финишировали",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun ControlActionButton(text: String, icon: Int, onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NavigationRow(text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickRipple(onClick = onClick),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Dimens.SIZE_BASE.dp, vertical = Dimens.SIZE_BASER.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_location_on_24px),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun OrienteeringEventControlContent(
    isExpanded: Boolean,
    userAction: (OrientEventControlAction) -> Unit
) {
    if (isExpanded) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ControlGridItem(Modifier.weight(1f), "Сканировать", Color(0xFF2196F3), R.drawable.edit) {
                userAction(OrientEventControlAction.OpenOrientReadCard)
            }
            ControlGridItem(Modifier.weight(1f), "Очистить", Color(0xFF4CAF50), R.drawable.edit) {}
            ControlGridItem(Modifier.weight(1f), "Проверить", Color(0xFFFFC107), R.drawable.edit) {}
            ControlGridItem(Modifier.weight(1f), "Записать", Color(0xFFF44336), R.drawable.edit) {
                userAction(OrientEventControlAction.OpenWriteChip)
            }
        }
    } else {
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                ControlGridItem(Modifier.weight(1f), "Сканировать", Color(0xFF2196F3), R.drawable.edit) {
                    userAction(OrientEventControlAction.OpenOrientReadCard)
                }
                ControlGridItem(Modifier.weight(1f), "Очистить", Color(0xFF4CAF50), R.drawable.edit) {}
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                ControlGridItem(Modifier.weight(1f), "Проверить", Color(0xFFFFC107), R.drawable.edit) {}
                ControlGridItem(Modifier.weight(1f), "Записать", Color(0xFFF44336), R.drawable.edit) {
                    userAction(OrientEventControlAction.OpenWriteChip)
                }
            }
        }
    }
}

@Composable
private fun ControlGridItem(
    modifier: Modifier,
    text: String,
    color: Color,
    icon: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .padding(4.dp)
            .aspectRatio(1.1f)
            .clickRipple(onClick = onClick),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(icon),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Баннер секундомера соревнования — показывает прошедшее время в формате ЧЧ:ММ:СС.сс.
 */
@Composable
private fun StopwatchBanner(elapsedMillis: Long) {
    val hours = elapsedMillis / 3_600_000L
    val minutes = (elapsedMillis % 3_600_000L) / 60_000L
    val seconds = (elapsedMillis % 60_000L) / 1_000L
    val hundredths = (elapsedMillis % 1_000L) / 10
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
    ) {
        Text(
            text = "%02d:%02d:%02d.%02d".format(hours, minutes, seconds, hundredths),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.SIZE_HALF.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Preview(name = "Compact", showBackground = true)
@Composable
fun OrienteeringEventControlScreenPreviewCompact() {
    MaterialTheme {
        OrienteeringEventControlContent(isExpanded = false, userAction = {})
    }
}

@Preview(showBackground = true)
@Composable
fun OrienteeringEventControlScreenPreview() {
    MaterialTheme {
        OrienteeringEventControlScreenContent(
            state = OrienteeringEventControlState(
                competitionTitle = "Чемпионат города по ориентированию",
                isTimerRunning = true,
                countdownMillis = 120000L,
                competition = OrienteeringCompetition(
                    competitionId = "1",
                    competition = Competition(
                        title = "Чемпионат города по ориентированию",
                        startDate = System.currentTimeMillis(),
                        kindOfSport = KindOfSport.Orienteering,
                        status = CompetitionStatus.DRAFT,
                        resultsStatus = ResultsStatus.NOT_PUBLISHED,
                        timeZoneId = "Europe/Moscow"
                    ),
                    direction = OrienteeringDirection.FORWARD,
                    punchingSystem = PunchingSystem.PUNCH,
                    startTimeMode = StartTimeMode.USER_SET
                )
            ),
            isExpanded = false,
            onAction = {}
        )
    }
}
