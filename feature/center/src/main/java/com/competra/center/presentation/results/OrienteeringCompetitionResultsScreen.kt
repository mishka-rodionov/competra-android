package com.competra.center.presentation.results

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.competra.center.data.results.ImportResultRow
import com.competra.center.data.results.ImportResultsDiff
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.components.clickRipple
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.ranksByScore
import com.competra.domain.models.orienteering.ByChoiceMode
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.ParticipantWithResult
import com.competra.domain.models.orienteering.ResultsStatus
import com.competra.domain.models.orienteering.displayPlace
import com.competra.resources.R
import com.competra.ui.components.TeamStandingsContent
import com.competra.ui.components.ResultsModeToggle
import com.competra.utils.DateTimeFormat
import com.competra.utils.orienteering.toRaceTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File

/**
 * Основной экран результатов соревнований.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrienteeringCompetitionResultsScreen(
    viewModel: OrienteeringCompetitionResultsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedParticipant by remember { mutableStateOf<ParticipantWithResult?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val importSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExportMenu by remember { mutableStateOf(false) }

    val importHtmlPicker = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let { pickedUri ->
            scope.launch(Dispatchers.IO) {
                val text = context.contentResolver.openInputStream(pickedUri)?.bufferedReader()?.use { it.readText() }
                text?.let {
                    viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.ImportHtml(it))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportCsvEvent.collectLatest { (fileName, csv) ->
            val file = File(context.cacheDir, fileName)
            file.writeText(csv, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = ShareCompat.IntentBuilder(context)
                .setType("text/csv")
                .addStream(uri)
                .setChooserTitle("Экспорт результатов")
                .createChooserIntent()
            context.startActivity(intent)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportPdfEvent.collectLatest { (fileName, bytes) ->
            val file = File(context.cacheDir, fileName)
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = ShareCompat.IntentBuilder(context)
                .setType("application/pdf")
                .addStream(uri)
                .setChooserTitle("Экспорт результатов")
                .createChooserIntent()
            context.startActivity(intent)
        }
    }

    // Диалог с опубликованной ссылкой
    val publishedUrl = state.publishedHtmlUrl
    if (publishedUrl != null) {
        val clipboardManager = context.getSystemService(android.content.ClipboardManager::class.java)
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.DismissPublishedUrl)
            },
            title = { Text("Результаты опубликованы") },
            text = {
                Text(
                    text = publishedUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val clip = android.content.ClipData.newPlainText("URL результатов", publishedUrl)
                    clipboardManager?.setPrimaryClip(clip)
                    viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.DismissPublishedUrl)
                }) { Text("Скопировать") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, publishedUrl)
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, "Поделиться ссылкой"))
                }) { Text("Поделиться") }
            }
        )
    }

    // Ошибка распознавания импортируемого HTML-файла
    state.importError?.let { error ->
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.DismissImportPreview)
            },
            title = { Text("Ошибка импорта") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.DismissImportPreview)
                }) { Text("Ок") }
            }
        )
    }

    // Подтверждение публикации результатов участникам (необратимо, шлёт push всем зарегистрированным)
    if (state.isShowPublishResultsConfirm) {
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.HidePublishResultsConfirm)
            },
            title = { Text("Опубликовать результаты?") },
            text = { Text("Все зарегистрированные участники получат push-уведомление о публикации результатов. Действие необратимо.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.PublishResults)
                }) { Text("Опубликовать") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.HidePublishResultsConfirm)
                }) { Text("Отмена") }
            }
        )
    }

    // Превью-дифф импорта результатов из HTML
    state.importDiff?.let { diff ->
        ImportResultsPreviewBottomSheet(
            competitionTitle = state.competitionTitle,
            diff = diff,
            sheetState = importSheetState,
            isImporting = state.isImporting,
            onDismiss = {
                viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.DismissImportPreview)
            },
            onConfirm = { selectedRows ->
                viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.ConfirmImport(selectedRows))
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Заголовок экрана
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SIZE_BASE.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Результаты",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Проверьте и утвердите итоговые протоколы",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.groupsWithParticipantsAndResults.isNotEmpty()) {
                    Box {
                        if (state.isPublishingHtml) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(4.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_share_24px),
                                    contentDescription = "Экспорт результатов",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Экспорт в CSV") },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.ExportCsv)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Экспорт в PDF") },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.ExportPdf)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Опубликовать результаты") },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.PublishHtml)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Импорт из HTML") },
                                onClick = {
                                    showExportMenu = false
                                    importHtmlPicker.launch("text/html")
                                }
                            )
                        }
                    }
                }
            }

            val teamStandings = state.teamStandings
            if (teamStandings != null) {
                ResultsModeToggle(
                    showTeam = state.showTeamStandings,
                    onChange = {
                        viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.ShowTeamStandings(it))
                    },
                    modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp)
                )
            }
            if (teamStandings != null && state.showTeamStandings) {
                TeamStandingsContent(standings = teamStandings, modifier = Modifier.weight(1f))
            } else LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Dimens.SIZE_BASE.dp, vertical = Dimens.SIZE_HALF.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
            ) {
                state.groupsWithParticipantsAndResults.forEach { groupWithResults ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimens.SIZE_HALF.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = groupWithResults.group.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = {
                                    viewModel.onAction(
                                        OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.OpenGroupSplitsTable(
                                            groupWithResults.group.groupId
                                        )
                                    )
                                }
                            ) {
                                Text(text = "Сплиты")
                            }
                            // Для формата "по выбору" общего порядка КП нет — вместо графика
                            // отставания от лидера по времени показываем график набора очков
                            // во времени (у каждого свой набор и порядок КП, победитель
                            // определяется по сумме баллов, а не по времени на перегонах).
                            // В «по выбору» с минимумом КП нет ни баллов, ни общего порядка —
                            // графика нет.
                            if (state.direction != OrienteeringDirection.BY_CHOICE ||
                                state.byChoiceMode == ByChoiceMode.SCORE
                            ) TextButton(
                                onClick = {
                                    val action = if (state.direction == OrienteeringDirection.BY_CHOICE) {
                                        OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.OpenScoreGraph(
                                            groupWithResults.group.groupId
                                        )
                                    } else {
                                        OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.OpenRaceGraph(
                                            groupWithResults.group.groupId
                                        )
                                    }
                                    viewModel.onAction(action)
                                }
                            ) {
                                Text(text = "График")
                            }
                        }
                    }
                    items(groupWithResults.participants) { participantWithResult ->
                        ResultParticipantCard(
                            result = participantWithResult,
                            direction = state.direction,
                            byChoiceMode = state.byChoiceMode,
                            onEditClick = {
                                selectedParticipant = participantWithResult
                                showBottomSheet = true
                            },
                            onCardClick = if (state.direction == OrienteeringDirection.BY_CHOICE) {
                                null
                            } else {
                                {
                                    viewModel.onAction(
                                        OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.OpenSplits(
                                            participantWithResult.participant.id
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
                
                if (state.groupsWithParticipantsAndResults.isEmpty()) {
                    item {
                        EmptyResultsView()
                    }
                }
            }

            if (!state.isApproved && !state.isCompetitionFinished) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.SIZE_BASE.dp)
                ) {
                    Button(
                        onClick = { viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.ApproveResults) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
                    ) {
                        Text(text = "Утвердить результаты", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state.resultsStatus == ResultsStatus.NOT_PUBLISHED && state.groupsWithParticipantsAndResults.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SIZE_BASE.dp, vertical = Dimens.SIZE_HALF.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onAction(OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.ShowPublishResultsConfirm) },
                        enabled = !state.isPublishingResults,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
                    ) {
                        if (state.isPublishingResults) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(text = "Опубликовать результаты участникам", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showBottomSheet && selectedParticipant != null) {
            EditResultBottomSheet(
                participantWithResult = selectedParticipant!!,
                sheetState = sheetState,
                onDismiss = { showBottomSheet = false },
                onSave = { startTime, finishTime ->
                    viewModel.onAction(
                        OrienteeringCompetitionResultsViewModel.OrienteeringResultsAction.UpdateResult(
                            participantWithResult = selectedParticipant!!,
                            startTime = startTime,
                            finishTime = finishTime
                        )
                    )
                    showBottomSheet = false
                }
            )
        }
    }
}

/**
 * Карточка участника с его результатом.
 *
 * Для формата "по выбору" (BY_CHOICE) [onCardClick] передаётся `null` — переход к сплитам
 * участника скрыт, т.к. порядок посещения КП не регламентирован и просмотр сплитов не имеет
 * смысла; вместо времени показывается сумма баллов (и время финиша — приоритет в определении
 * победителя у баллов, но время тоже показываем).
 */
@Composable
fun ResultParticipantCard(
    result: ParticipantWithResult,
    onEditClick: () -> Unit,
    direction: OrienteeringDirection = OrienteeringDirection.FORWARD,
    byChoiceMode: ByChoiceMode = ByChoiceMode.DEFAULT,
    onCardClick: (() -> Unit)? = null
) {
    // Баллы — только в score-О; в «по выбору» с минимумом КП показывается время.
    val isByChoice = ranksByScore(direction, byChoiceMode)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onCardClick != null) it.clickRipple(onClick = onCardClick) else it },
        shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(Dimens.SIZE_BASE.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Место или статус
            val rankText = result.result?.displayPlace?.toString() ?: result.result?.status?.name ?: "-"
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (result.result?.rank == 1) Color(0xFFFFD700).copy(alpha = 0.2f) 
                        else MaterialTheme.colorScheme.surfaceVariant, 
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rankText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (result.result?.rank == 1) Color(0xFFB8860B) else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${result.participant.firstName} ${result.participant.lastName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (result.result?.isEdited == true) {
                    Text(
                        text = "Отредактировано вручную",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }

            // Баллы+время (BY_CHOICE, приоритет у баллов) или только время (остальные форматы)
            if (isByChoice && result.result?.status == ResultStatus.FINISHED) {
                Column(horizontalAlignment = Alignment.End) {
                    val score = result.result?.totalScore ?: 0
                    val penalty = result.result?.scorePenalty ?: 0
                    Text(
                        text = if (penalty > 0) "$score очков (-$penalty)" else "$score очков",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = result.result?.totalTime?.toRaceTime() ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = result.result?.totalTime?.toRaceTime() ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (result.result?.isEditable == true) {
                Spacer(modifier = Modifier.width(Dimens.SIZE_HALF.dp))
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.edit),
                        contentDescription = "Edit result",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyResultsView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.SIZE_DOUBLE.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_location_on_24px),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
        Text(
            text = "Результаты еще не загружены или соревнование не завершено",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Боттомшит для редактирования времени старта и финиша.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditResultBottomSheet(
    participantWithResult: ParticipantWithResult,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSave: (startTime: Long?, finishTime: Long?) -> Unit
) {
    var startTimeStr by remember {
        mutableStateOf(DateTimeFormat.transformLongToTime(participantWithResult.result?.startTime) ?: "")
    }
    var finishTimeStr by remember {
        mutableStateOf(DateTimeFormat.transformLongToTime(participantWithResult.result?.finishTime) ?: "")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SIZE_BASE.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Корректировка времени",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "${participantWithResult.participant.firstName} ${participantWithResult.participant.lastName}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = Dimens.SIZE_HALF.dp)
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            DSTextInput(
                modifier = Modifier.fillMaxWidth(),
                text = startTimeStr,
                onValueChanged = { startTimeStr = it },
                label = { Text("Время старта (HH:mm:ss)") }
            )
            
            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

            DSTextInput(
                modifier = Modifier.fillMaxWidth(),
                text = finishTimeStr,
                onValueChanged = { finishTimeStr = it },
                label = { Text("Время финиша (HH:mm:ss)") }
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))

            Button(
                onClick = {
                    val newStart = DateTimeFormat.updateTimeInTimestamp(participantWithResult.result?.startTime, startTimeStr)
                    val newFinish = DateTimeFormat.updateTimeInTimestamp(participantWithResult.result?.finishTime, finishTimeStr)
                    onSave(newStart, newFinish)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
            ) {
                Text("Сохранить изменения", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Превью-дифф перед импортом результатов из HTML: изменившиеся заматченные строки (с чекбоксом
 * на каждой — можно исключить отдельные строки из импорта) и отдельно, только информационно,
 * строки с нераспознанным стартовым номером.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportResultsPreviewBottomSheet(
    competitionTitle: String,
    diff: ImportResultsDiff,
    sheetState: SheetState,
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<ImportResultRow>) -> Unit,
) {
    val checkedByParticipantId = remember(diff) {
        mutableStateMapOf<String, Boolean>().apply { diff.changed.forEach { this[it.participant.id] = true } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(Dimens.SIZE_BASE.dp)
        ) {
            Text(
                text = "Импорт результатов из HTML",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Импорт применится к текущему соревнованию: $competitionTitle",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = Dimens.SIZE_BASE.dp)
            )

            when {
                diff.changed.isEmpty() && diff.unmatched.isEmpty() -> Text(
                    "В файле не найдено ни одной строки результатов.",
                    color = MaterialTheme.colorScheme.error
                )
                diff.changed.isEmpty() -> Text(
                    "Совпадений с текущими участниками не найдено — проверьте, что выбран файл именно этого соревнования.",
                    color = MaterialTheme.colorScheme.error
                )
                else -> Text("Изменения (${diff.changed.size}):", style = MaterialTheme.typography.titleMedium)
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(diff.changed, key = { it.participant.id }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checkedByParticipantId[row.participant.id] == true,
                            onCheckedChange = { checkedByParticipantId[row.participant.id] = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "№${row.participant.startNumber} ${row.participant.lastName} ${row.participant.firstName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                row.changeSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (diff.unmatched.isNotEmpty()) {
                    item {
                        Text(
                            "Не распознано (${diff.unmatched.size}) — стартовый номер не найден среди участников, будут пропущены:",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = Dimens.SIZE_BASE.dp, bottom = 4.dp)
                        )
                    }
                    items(diff.unmatched) { row ->
                        Text(
                            "№${row.startNumber} ${row.fullName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            val selectedCount = diff.changed.count { checkedByParticipantId[it.participant.id] == true }
            Button(
                onClick = { onConfirm(diff.changed.filter { checkedByParticipantId[it.participant.id] == true }) },
                enabled = selectedCount > 0 && !isImporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(top = Dimens.SIZE_BASE.dp),
                shape = RoundedCornerShape(Dimens.SIZE_BASE.dp)
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Применить ($selectedCount)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
