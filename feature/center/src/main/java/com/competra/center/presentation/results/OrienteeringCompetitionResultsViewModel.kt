package com.competra.center.presentation.results

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.center.data.results.ImportResultRow
import com.competra.center.data.results.OrienteeringCompetitionResultsState
import com.competra.center.data.results.buildResultsDiff
import com.competra.center.data.results.parseResultsHtml
import com.competra.data.navigation.CenterNavigation
import com.competra.data.navigation.Navigation
import com.competra.data.navigation.getArguments
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.CompetitionStatus
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.GroupWithParticipantsAndResults
import com.competra.domain.models.orienteering.OrienteeringDirection
import com.competra.domain.models.orienteering.OrienteeringResult
import com.competra.domain.models.orienteering.ParticipantWithResult
import com.competra.domain.models.orienteering.ResultsStatus
import com.competra.domain.models.orienteering.SplitsTableCell
import com.competra.domain.models.orienteering.buildSplitsTable
import com.competra.domain.models.orienteering.sortedForResults
import com.competra.domain.repository.UploadRepository
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import com.competra.utils.DateTimeFormat
import com.competra.utils.constants.EventsConstants
import com.competra.utils.orienteering.toRaceTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * ViewModel для экрана результатов соревнований по ориентированию.
 * Управляет состоянием экрана, загрузкой данных и обработкой действий пользователя.
 *
 * @param orienteeringCompetitionInteractor Интерактор для работы с данными соревнований.
 * @param navigation Навигация для перехода между экранами.
 */
class OrienteeringCompetitionResultsViewModel(
    private val orienteeringCompetitionInteractor: OrienteeringCompetitionInteractor,
    private val navigation: Navigation,
    private val uploadRepository: UploadRepository,
    private val analytics: AnalyticsTracker,
): BaseViewModel<OrienteeringCompetitionResultsState>(OrienteeringCompetitionResultsState()) {

    val competitionId: String? = navigation.getArguments<String>(EventsConstants.EVENT_ID.name)

    private val _exportCsvEvent = MutableSharedFlow<Pair<String, String>>()
    val exportCsvEvent: SharedFlow<Pair<String, String>> = _exportCsvEvent

    private val _exportPdfEvent = MutableSharedFlow<Pair<String, ByteArray>>()
    val exportPdfEvent: SharedFlow<Pair<String, ByteArray>> = _exportPdfEvent


    init {
        loadResults()
    }

    /**
     * Загружает результаты соревнований и обновляет состояние.
     *
     * Для завершённых соревнований результаты подтягиваются с сервера (см.
     * [OrienteeringCompetitionInteractor.getResultsByGroupsAuto]): они могли быть внесены не с
     * этого устройства (другой судейский телефон, веб), а фоновая синхронизация только выгружает
     * локальные изменения и не скачивает чужие результаты обратно. Для соревнований в процессе —
     * без сетевых запросов, только локальная БД.
     */
    private fun loadResults() {
        competitionId?.let { id ->
            viewModelScope.launch(Dispatchers.IO) {
                val competition = orienteeringCompetitionInteractor.getCompetition(id)
                val results = orienteeringCompetitionInteractor.getResultsByGroupsAuto(id)

                val direction = competition?.direction ?: OrienteeringDirection.FORWARD
                val sortedResults = results.map { group ->
                    group.copy(participants = group.participants.sortedForResults(direction))
                }
                val isApproved = sortedResults.isNotEmpty() &&
                    sortedResults.all { group -> group.participants.all { it.result?.isEditable == false } }
                val isCompetitionFinished = competition?.competition?.status in
                    listOf(CompetitionStatus.FINISHED, CompetitionStatus.ARCHIVED)
                updateState {
                    copy(
                        groupsWithParticipantsAndResults = sortedResults,
                        direction = direction,
                        isApproved = isApproved,
                        isCompetitionFinished = isCompetitionFinished,
                        competitionTitle = competition?.competition?.title ?: "",
                        resultsStatus = competition?.competition?.resultsStatus ?: ResultsStatus.NOT_PUBLISHED,
                    )
                }
            }
        }
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            is OrienteeringResultsAction.UpdateResult -> updateParticipantResult(action.participantWithResult, action.startTime, action.finishTime)
            is OrienteeringResultsAction.ApproveResults -> approveResults()
            is OrienteeringResultsAction.OpenSplits -> openSplits(action.participantId)
            is OrienteeringResultsAction.OpenGroupSplitsTable -> openGroupSplitsTable(action.groupId)
            is OrienteeringResultsAction.OpenRaceGraph -> openRaceGraph(action.groupId)
            is OrienteeringResultsAction.OpenScoreGraph -> openScoreGraph(action.groupId)
            is OrienteeringResultsAction.ExportCsv -> exportCsv()
            is OrienteeringResultsAction.ExportPdf -> exportPdf()
            is OrienteeringResultsAction.PublishHtml -> publishHtml()
            is OrienteeringResultsAction.DismissPublishedUrl -> updateState { copy(publishedHtmlUrl = null) }
            is OrienteeringResultsAction.ShowPublishResultsConfirm -> updateState { copy(isShowPublishResultsConfirm = true) }
            is OrienteeringResultsAction.HidePublishResultsConfirm -> updateState { copy(isShowPublishResultsConfirm = false) }
            is OrienteeringResultsAction.PublishResults -> publishResults()
            is OrienteeringResultsAction.ImportHtml -> importHtml(action.htmlText)
            is OrienteeringResultsAction.ConfirmImport -> confirmImport(action.selectedRows)
            is OrienteeringResultsAction.DismissImportPreview -> updateState { copy(importDiff = null, importError = null) }
        }
    }

    /**
     * Парсит выбранный HTML-протокол результатов и строит дифф-превью относительно текущих
     * данных соревнования. Импорт всегда применяется к текущему открытому соревнованию —
     * competitionId в самом HTML не хранится.
     */
    private fun importHtml(htmlText: String) {
        val groups = stateValue.groupsWithParticipantsAndResults
        val compId = competitionId ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val diff = try {
                val parsedRows = parseResultsHtml(htmlText)
                if (parsedRows.isEmpty()) {
                    updateState { copy(importError = "Не удалось распознать файл — проверьте, что это HTML-протокол результатов.") }
                    return@launch
                }
                buildResultsDiff(parsedRows, groups, compId)
            } catch (e: Exception) {
                updateState { copy(importError = "Не удалось распознать файл — проверьте, что это HTML-протокол результатов.") }
                return@launch
            }
            updateState { copy(importDiff = diff, importError = null) }
        }
    }

    private fun confirmImport(selectedRows: List<ImportResultRow>) {
        if (selectedRows.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isImporting = true) }
            orienteeringCompetitionInteractor.importResults(selectedRows.map { it.request })
            updateState { copy(isImporting = false, importDiff = null) }
            loadResults()
        }
    }

    private fun openSplits(participantId: String) {
        val compId = competitionId ?: return
        viewModelScope.launch {
            navigation.navigate(CenterNavigation.ParticipantSplitsRoute(participantId, compId))
        }
    }

    private fun openGroupSplitsTable(groupId: Long) {
        val compId = competitionId ?: return
        viewModelScope.launch {
            navigation.navigate(CenterNavigation.GroupSplitsTableRoute(groupId, compId))
        }
    }

    private fun openRaceGraph(groupId: Long) {
        val compId = competitionId ?: return
        viewModelScope.launch {
            navigation.navigate(CenterNavigation.GroupRaceGraphRoute(groupId, compId))
        }
    }

    private fun openScoreGraph(groupId: Long) {
        val compId = competitionId ?: return
        viewModelScope.launch {
            navigation.navigate(CenterNavigation.GroupScoreGraphRoute(groupId, compId))
        }
    }

    /**
     * Обновляет результат участника.
     *
     * @param participantWithResult Данные участника и его текущий результат.
     * @param startTime Новое время старта.
     * @param finishTime Новое время финиша.
     */
    private fun updateParticipantResult(participantWithResult: ParticipantWithResult, startTime: Long?, finishTime: Long?) {
        val currentResult = participantWithResult.result ?: return
        val updatedResult = currentResult.copy(
            startTime = startTime,
            finishTime = finishTime,
            isEdited = true,
            totalTime = if (startTime != null && finishTime != null) (finishTime - startTime) / 1000 else null
        )
        viewModelScope.launch(Dispatchers.IO) {
            orienteeringCompetitionInteractor.updateParticipantResult(updatedResult)
            loadResults()
        }
    }

    /**
     * Утверждает результаты для текущего соревнования.
     */
    private fun approveResults() {
        competitionId?.let { id ->
            viewModelScope.launch(Dispatchers.IO) {
                orienteeringCompetitionInteractor.approveResults(id).onSuccess {
                    loadResults()
                }
            }
        }
    }

    private fun exportCsv() {
        val groups = stateValue.groupsWithParticipantsAndResults
        if (groups.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val title = stateValue.competitionTitle
            val csv = buildCsvContent(title, groups)
            val fileName = buildSafeFileName(title)
            _exportCsvEvent.emit(fileName to csv)
        }
    }

    private fun exportPdf() {
        val groups = stateValue.groupsWithParticipantsAndResults
        if (groups.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val title = stateValue.competitionTitle
            val bytes = buildPdfBytes(title, groups)
            val fileName = buildSafeFileName(title).replace(".csv", ".pdf")
            _exportPdfEvent.emit(fileName to bytes)
        }
    }

    private fun buildSafeFileName(title: String): String {
        val safe = title.replace(Regex("[^а-яА-ЯёЁa-zA-Z0-9_\\- ]"), "").trim().take(40)
        return if (safe.isNotEmpty()) "results_$safe.csv" else "results.csv"
    }

    private fun buildCsvContent(title: String, groups: List<GroupWithParticipantsAndResults>): String {
        val sb = StringBuilder("﻿") // UTF-8 BOM для корректного открытия в Excel
        if (title.isNotEmpty()) {
            sb.appendLine("Соревнование: $title")
            sb.appendLine()
        }
        groups.forEach { group ->
            val table = buildSplitsTable(group)
            val cpOrder = table.columns.map { it.controlPoint }

            sb.appendLine("Группа: ${group.group.title}")
            val headerSuffix = if (cpOrder.isNotEmpty()) ";${buildCpHeaders(cpOrder)}" else ""
            sb.appendLine("Место;Фамилия;Имя;Команда;Старт;Финиш;Результат;Статус$headerSuffix")

            group.participants.zip(table.rows).forEach { (pw, row) ->
                val rank = pw.result?.rank?.toString() ?: ""
                val start = DateTimeFormat.transformLongToTime(pw.result?.startTime) ?: ""
                val finish = DateTimeFormat.transformLongToTime(pw.result?.finishTime) ?: ""
                val total = pw.result?.totalTime?.toRaceTime() ?: ""
                val status = when (pw.result?.status) {
                    ResultStatus.FINISHED -> "Финиш"
                    ResultStatus.OVERTIME -> "Превышено КВ"
                    ResultStatus.DSQ      -> "Снят"
                    ResultStatus.DNS      -> "Не стартовал"
                    ResultStatus.DNF      -> "Сошёл"
                    else                  -> ""
                }
                val resultRow = "$rank;${pw.participant.lastName};${pw.participant.firstName};${pw.participant.commandName};$start;$finish;$total;$status"
                if (cpOrder.isNotEmpty()) {
                    val splitValues = buildSplitValues(row.cells)
                    sb.appendLine("$resultRow;$splitValues")
                } else {
                    sb.appendLine(resultRow)
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    /** Строит заголовки колонок сплитов с суффиксом [n] для дублирующихся КП. */
    private fun buildCpHeaders(cpOrder: List<Int>): String {
        val cpCounts = mutableMapOf<Int, Int>()
        val hasDuplicates = cpOrder.groupingBy { it }.eachCount().any { it.value > 1 }
        return cpOrder.joinToString(";") { cp ->
            val count = cpCounts.merge(cp, 1, Int::plus)!!
            val tag = if (hasDuplicates && cpOrder.count { it == cp } > 1) "[$count]" else ""
            "КП$cp${tag}_δ;КП$cp${tag}_Σ"
        }
    }

    /** Строит строку значений сплитов (дельта;кумулятив на каждый КП) для одной строки таблицы. */
    private fun buildSplitValues(cells: List<SplitsTableCell>): String {
        return cells.joinToString(";") { cell ->
            val delta = cell.deltaSeconds?.toRaceTime() ?: ""
            val cumul = cell.cumulativeSeconds?.toRaceTime() ?: ""
            "$delta;$cumul"
        }
    }

    private fun buildPdfBytes(title: String, groups: List<GroupWithParticipantsAndResults>): ByteArray {
        val document = PdfDocument()
        val pW = 595; val pH = 842
        val margin = 36f
        val lineH = 16f
        val smallLineH = 14f

        val paintTitle  = Paint().apply { textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
        val paintGroup  = Paint().apply { textSize = 13f; isFakeBoldText = true; isAntiAlias = true }
        val paintHeader = Paint().apply { textSize = 9f;  isFakeBoldText = true; isAntiAlias = true }
        val paintBody   = Paint().apply { textSize = 9f;  isAntiAlias = true }
        val paintSplit  = Paint().apply { textSize = 8f;  color = android.graphics.Color.DKGRAY; isAntiAlias = true }

        var pageNum = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pW, pH, pageNum).create())
        var canvas = page.canvas
        var y = margin + paintTitle.textSize

        fun ensureSpace(needed: Float) {
            if (y + needed > pH - margin) {
                document.finishPage(page)
                page = document.startPage(PdfDocument.PageInfo.Builder(pW, pH, ++pageNum).create())
                canvas = page.canvas
                y = margin + paintBody.textSize
            }
        }

        if (title.isNotEmpty()) {
            canvas.drawText(title, margin, y, paintTitle)
            y += lineH * 1.5f
        }

        groups.forEach { group ->
            val table = buildSplitsTable(group)
            val cpOrder = table.columns.map { it.controlPoint }

            ensureSpace(lineH * 3)
            canvas.drawText("Группа: ${group.group.title}", margin, y, paintGroup)
            y += lineH
            canvas.drawText(
                "М   Фамилия Имя              Команда           Старт      Финиш      Результат   Статус",
                margin, y, paintHeader
            )
            y += smallLineH

            group.participants.zip(table.rows).forEach { (pw, row) ->
                ensureSpace(lineH + if (cpOrder.isNotEmpty()) smallLineH else 0f)

                val rank   = (pw.result?.rank?.toString() ?: "-").padEnd(3)
                val name   = "${pw.participant.lastName} ${pw.participant.firstName}".take(20).padEnd(20)
                val team   = pw.participant.commandName.take(18).padEnd(18)
                val start  = (DateTimeFormat.transformLongToTime(pw.result?.startTime) ?: "").padEnd(10)
                val finish = (DateTimeFormat.transformLongToTime(pw.result?.finishTime) ?: "").padEnd(10)
                val total  = (pw.result?.totalTime?.toRaceTime() ?: "").padEnd(10)
                val status = when (pw.result?.status) {
                    ResultStatus.FINISHED -> "Финиш"
                    ResultStatus.OVERTIME -> "Превышено КВ"
                    ResultStatus.DSQ      -> "Снят"
                    ResultStatus.DNS      -> "Не стартовал"
                    ResultStatus.DNF      -> "Сошёл"
                    else                  -> "-"
                }
                canvas.drawText("$rank $name $team $start $finish $total $status", margin, y, paintBody)
                y += lineH

                if (cpOrder.isNotEmpty()) {
                    val splitsLine = buildString {
                        cpOrder.forEachIndexed { i, cp ->
                            if (i > 0) append("  ")
                            val cell = row.cells[i]
                            val cellDeltaSeconds = cell.deltaSeconds
                            if (cellDeltaSeconds != null) {
                                val delta = cellDeltaSeconds.toRaceTime()
                                val cumul = cell.cumulativeSeconds?.toRaceTime() ?: ""
                                append("КП$cp: $delta ($cumul)")
                            } else {
                                append("КП$cp: —")
                            }
                        }
                    }
                    canvas.drawText(splitsLine.take(110), margin + 12f, y, paintSplit)
                    y += smallLineH
                }
            }
            y += lineH * 0.5f
        }

        document.finishPage(page)
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    private fun publishHtml() {
        val groups = stateValue.groupsWithParticipantsAndResults
        if (groups.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isPublishingHtml = true) }
            val title = stateValue.competitionTitle
            // Для BY_CHOICE нужны реальные очки за взятые КП дистанции — их не восстановить
            // надёжно из totalScore/scorePenalty (при обнулении баллов за сильное опоздание
            // totalScore=0, а scorePenalty может быть больше фактически заработанных баллов).
            val distancesByGroupId = groups.associate { g ->
                g.group.groupId to orienteeringCompetitionInteractor.getDistanceById(g.group.distanceId).getOrNull()
            }
            val html = buildHtmlContent(title, groups, stateValue.direction, distancesByGroupId)
            val bytes = html.toByteArray(Charsets.UTF_8)
            uploadRepository.uploadFile(bytes, "results.html", "competition_results")
                .onSuccess { url ->
                    val compId = competitionId ?: return@onSuccess
                    val comp = orienteeringCompetitionInteractor.getCompetition(compId) ?: return@onSuccess
                    val updated = comp.copy(
                        competition = comp.competition.copy(
                            resultsUrl = url,
                            isSynced = false,
                        )
                    )
                    orienteeringCompetitionInteractor.updateCompetition(updated)
                    updateState { copy(isPublishingHtml = false, publishedHtmlUrl = url) }
                }
                .onFailure {
                    updateState { copy(isPublishingHtml = false) }
                }
        }
    }

    /**
     * Переводит [ResultsStatus] соревнования из NOT_PUBLISHED в OFFICIAL — необратимое,
     * видимое участникам действие. Backend на этом переходе рассылает push-уведомление
     * "результаты опубликованы" всем зарегистрированным участникам (см. OrienteeringCompetitionService
     * в eSport). Идёт тем же путём, что и [publishHtml] — updateCompetition помечает запись
     * isSynced=false, SyncCenterWorker выгружает изменение на сервер.
     */
    private fun publishResults() {
        val compId = competitionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isPublishingResults = true, isShowPublishResultsConfirm = false) }
            val comp = orienteeringCompetitionInteractor.getCompetition(compId)
            if (comp == null) {
                updateState { copy(isPublishingResults = false) }
                return@launch
            }
            val updated = comp.copy(
                competition = comp.competition.copy(
                    resultsStatus = ResultsStatus.OFFICIAL,
                    isSynced = false,
                )
            )
            orienteeringCompetitionInteractor.updateCompetition(updated)
            analytics.trackEvent(AnalyticsEvent.ResultsPublishClicked(compId))
            updateState { copy(isPublishingResults = false, resultsStatus = ResultsStatus.OFFICIAL) }
        }
    }

    private fun buildHtmlContent(
        title: String,
        groups: List<GroupWithParticipantsAndResults>,
        direction: OrienteeringDirection = OrienteeringDirection.FORWARD,
        distancesByGroupId: Map<Long, Distance?> = emptyMap()
    ): String {
        val isByChoice = direction == OrienteeringDirection.BY_CHOICE
        val sb = StringBuilder()
        sb.append(
            """<!DOCTYPE html>
<meta content='text/html'; charset='utf-8' http-equiv='Content-Type'>
<style>
body {font-family: 'Arial Narrow'; font-size: 10pt;}
table.rezult {font-family: 'Arial Narrow'; font-size: 10pt; border: 1px solid #999999; border-collapse: collapse; background: #DDDDDD;text-align: center;}
table.rezult td{ margin:0; padding: 2px 5px;  border: 1px solid #999999; border-collapse: collapse; background: #FFFFFF;}
.yl, tr.yl td {background: #FFFFBB;}
.cr {text-align:left}
.rezult th { font-family: 'Arial Narrow';font-style: italic; font-size: 10pt; color: #FFFFFF;padding: 2px 3px; border: 1px solid #999999; border-collapse: collapse; background: #112255;}
H1  {font-family: 'Arial Narrow';font-size: 14pt;font-weight: bold;color: #112255;text-align: left;}
H2  {font-family: 'Arial Narrow';font-size: 12pt;font-weight: bold;color: #112255;text-align: left;}
H3  {font-family: 'Arial Narrow';font-size: 10pt;font-weight: bold;color: #112255;text-align: left;}
span.group  {font-family: 'Arial Narrow';font-size: 12pt;font-weight: bold;}
</style>
"""
        )

        val titleText = if (title.isNotEmpty()) "$title. Промежуточные времена" else "Промежуточные времена"
        sb.appendLine("<h1>$titleText</h1>")
        sb.appendLine("<h3>Данный протокол не является официальным документом</h3>")
        sb.appendLine("<a name=\"uppoint\">  </a>")
        sb.appendLine("<br><br>")

        val groupNavigation = groups.joinToString("    ") { g ->
            "<a href=\"#${g.group.title}\">${g.group.title}</a>"
        }

        groups.forEachIndexed { groupIndex, group ->
            val table = buildSplitsTable(group)
            // Для BY_CHOICE у каждого участника свой набор и порядок КП — общий cpOrder по
            // дистанции не имеет смысла. Колонки строятся по позиции (1..максимум сплитов
            // в группе), а какой именно КП стоит за каждой позицией у конкретного участника —
            // печатается прямо в его ячейке.
            val cpOrder = if (isByChoice) emptyList() else table.columns.map { it.controlPoint }
            val maxSplitsCount = if (isByChoice) {
                group.participants.mapNotNull { it.result?.splits?.size }.maxOrNull() ?: 0
            } else 0
            // Реальные баллы за каждый КП дистанции — нужны, чтобы посчитать точные "сырые"
            // баллы участника (см. scoreText ниже), т.к. totalScore хранится уже за вычетом
            // штрафа, а при обнулении баллов (сильное опоздание) totalScore+scorePenalty не
            // равно фактически заработанным баллам.
            val scoreByNumber = distancesByGroupId[group.group.groupId]
                ?.controlPoints
                ?.associate { it.number to it.score }
                ?: emptyMap()

            val leaderTotalTime = group.participants
                .filter { it.result?.status == ResultStatus.FINISHED }
                .minByOrNull { it.result?.totalTime ?: Long.MAX_VALUE }
                ?.result?.totalTime

            // Для BY_CHOICE отставание имеет смысл только между участниками с одинаковыми
            // очками — время не является общим критерием ранжирования (первичны баллы), сравнивать
            // "отставание" по времени между разными по очкам участниками нельзя.
            val byChoiceTieGroups = if (isByChoice) {
                group.participants
                    .filter { it.result?.status == ResultStatus.FINISHED }
                    .groupBy { it.result?.totalScore ?: 0 }
            } else emptyMap()

            sb.appendLine("<a name=\"${group.group.title}\"></a>")
            if (groupIndex == 0) {
                sb.appendLine("<span class='group'>$groupNavigation</span><br>")
            }
            sb.appendLine("<h2>${group.group.title}</h2>")

            sb.append("<table class='rezult'>\n<tr>")
            sb.append("<th>№ п/п </th><th>Ст.№ </th><th>Фамилия, Имя </th><th>Команда </th>")
            sb.append("<th>Результат </th><th>Место </th><th>Отставание </th>")
            cpOrder.forEachIndexed { i, cp -> sb.append("<th>#${i + 1} ($cp) </th>") }
            repeat(maxSplitsCount) { i -> sb.append("<th>#${i + 1} </th>") }
            sb.appendLine("</tr>")

            group.participants.zip(table.rows).forEachIndexed { rowIdx, (pw, row) ->
                val rowAttr = if (rowIdx % 2 == 0) "style='background: #FFFFFF;'" else "class='yl'"
                sb.append("<tr $rowAttr>")

                sb.append("<td><nobr>${rowIdx + 1}</td>")
                sb.append("<td><nobr>${pw.participant.startNumber}</td>")
                sb.append("<td class='cr'><nobr>${pw.participant.lastName} ${pw.participant.firstName}</td>")
                sb.append("<td class='cr'><nobr>${pw.participant.commandName}</td>")

                val totalTime = pw.result?.totalTime?.toRaceTime() ?: ""
                val statusText = when (pw.result?.status) {
                    ResultStatus.FINISHED -> if (isByChoice) {
                        val netScore = pw.result?.totalScore ?: 0
                        val penalty = pw.result?.scorePenalty ?: 0
                        // Реальные "сырые" баллы — сумма очков по фактически взятым КП
                        // (совпадают с тем, как считает computeByChoiceResult при отметке).
                        // Фолбэк на netScore+penalty только если дистанция группы не найдена.
                        val rawScore = if (scoreByNumber.isNotEmpty()) {
                            pw.result?.splits?.sumOf { scoreByNumber[it.controlPoint] ?: 0 } ?: (netScore + penalty)
                        } else {
                            netScore + penalty
                        }
                        val scoreText = if (penalty > 0) "$rawScore - $penalty (штраф) = $netScore" else "$netScore очков"
                        "$scoreText<br>$totalTime"
                    } else {
                        totalTime
                    }
                    ResultStatus.OVERTIME -> "превышено кв"
                    ResultStatus.DSQ      -> "снят"
                    ResultStatus.DNS      -> "н/с"
                    ResultStatus.DNF      -> "не финишировал"
                    else                  -> ""
                }
                sb.append("<td><nobr>$statusText</td>")
                sb.append("<td><nobr>${pw.result?.rank?.toString() ?: ""}</td>")

                val gap = if (isByChoice) {
                    // Отставание считаем только внутри группы участников с одинаковыми очками —
                    // относительно самого быстрого из них по времени прохождения.
                    val tieGroup = byChoiceTieGroups[pw.result?.totalScore ?: 0].orEmpty()
                    val tieLeaderTime = tieGroup.minOfOrNull { it.result?.totalTime ?: Long.MAX_VALUE }
                    if (pw.result?.status == ResultStatus.FINISHED && tieGroup.size > 1 && tieLeaderTime != null) {
                        val diff = (pw.result?.totalTime ?: 0L) - tieLeaderTime
                        if (diff <= 0L) "" else "+${formatGap(diff)}"
                    } else ""
                } else if (pw.result?.status == ResultStatus.FINISHED && leaderTotalTime != null) {
                    val diff = (pw.result?.totalTime ?: 0L) - leaderTotalTime
                    if (diff <= 0L) "" else "+${formatGap(diff)}"
                } else ""
                sb.append("<td><nobr>$gap</td>")

                if (!isByChoice) {
                    row.cells.forEachIndexed { i, cell ->
                        val cellCumulativeSeconds = cell.cumulativeSeconds
                        if (cellCumulativeSeconds == null) {
                            sb.append("<td><nobr></td>")
                            return@forEachIndexed
                        }
                        val cumulStr = cellCumulativeSeconds.toRaceTime()
                        val cumulRank = cell.cumulativeRank ?: 0
                        val cumulCell = if (cumulRank == 1) "<b><nobr>$cumulStr($cumulRank)</b>" else "<nobr>$cumulStr($cumulRank)"

                        if (i == 0) {
                            sb.append("<td>$cumulCell<br></td>")
                        } else {
                            val deltaStr = cell.deltaSeconds?.toRaceTime() ?: ""
                            val deltaRank = cell.deltaRank ?: 0
                            val deltaCell = if (deltaRank == 1) "<b><nobr>$deltaStr($deltaRank)</b>" else "<nobr>$deltaStr($deltaRank)"
                            sb.append("<td>$cumulCell<br>$deltaCell</td>")
                        }
                    }
                } else {
                    val splits = pw.result?.splits.orEmpty()
                    val startTs = pw.result?.startTime ?: pw.participant.startTime
                    repeat(maxSplitsCount) { i ->
                        val split = splits.getOrNull(i)
                        if (split == null) {
                            sb.append("<td><nobr></td>")
                        } else {
                            val prevTs = if (i == 0) startTs else splits[i - 1].timestamp
                            val cumulStr = ((split.timestamp - startTs) / 1000L).toRaceTime()
                            val deltaStr = ((split.timestamp - prevTs) / 1000L).toRaceTime()
                            sb.append("<td><nobr>#${i + 1}(${split.controlPoint})</nobr><br>$cumulStr<br>$deltaStr</td>")
                        }
                    }
                }

                sb.appendLine("</tr>")
            }
            sb.appendLine("</table>")
            sb.appendLine("<br>")
        }

        return sb.toString()
    }

    private fun formatGap(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
        else "$m:${s.toString().padStart(2, '0')}"
    }

    /**
     * Действия на экране результатов.
     */
    sealed class OrienteeringResultsAction : BaseAction {
        data class UpdateResult(
            val participantWithResult: ParticipantWithResult,
            val startTime: Long?,
            val finishTime: Long?
        ) : OrienteeringResultsAction()

        object ApproveResults : OrienteeringResultsAction()

        data class OpenSplits(val participantId: String) : OrienteeringResultsAction()

        data class OpenGroupSplitsTable(val groupId: Long) : OrienteeringResultsAction()

        data class OpenRaceGraph(val groupId: Long) : OrienteeringResultsAction()

        data class OpenScoreGraph(val groupId: Long) : OrienteeringResultsAction()

        data object ExportCsv : OrienteeringResultsAction()

        data object ExportPdf : OrienteeringResultsAction()

        data object PublishHtml : OrienteeringResultsAction()

        data object DismissPublishedUrl : OrienteeringResultsAction()

        /** Показать/скрыть диалог подтверждения публикации результатов участникам. */
        data object ShowPublishResultsConfirm : OrienteeringResultsAction()
        data object HidePublishResultsConfirm : OrienteeringResultsAction()

        /** Подтверждена публикация результатов — переводит resultsStatus в OFFICIAL, шлёт push участникам. */
        data object PublishResults : OrienteeringResultsAction()

        /** Пользователь выбрал HTML-файл для импорта; [htmlText] — его содержимое. */
        data class ImportHtml(val htmlText: String) : OrienteeringResultsAction()

        /** Подтверждение импорта: применить только отмеченные в превью строки. */
        data class ConfirmImport(val selectedRows: List<ImportResultRow>) : OrienteeringResultsAction()

        data object DismissImportPreview : OrienteeringResultsAction()
    }
}
