package com.competra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.orienteering.ScoreGraphData
import com.competra.domain.models.orienteering.ScoreGraphSeries
import com.competra.utils.orienteering.toRaceTime
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.DashedShape
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val TIME_LIMIT_COLOR = Color(0xFFE65100)

/** "Круглый" шаг для оси очков — 1/2/5 * 10^n, ближайший к range/targetTicks. Тот же алгоритм, что и в web-версии графика. */
private fun niceScoreStep(maxValue: Int, targetTicks: Int = 5): Double {
    if (maxValue <= 0) return 1.0
    val rough = maxValue.toDouble() / targetTicks
    val magnitude = 10.0.pow(floor(log10(rough)))
    val normalized = rough / magnitude
    val niceNormalized = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return (niceNormalized * magnitude).coerceAtLeast(1.0)
}

/**
 * График набора очков во времени (BY_CHOICE, score-О): X — время от старта участника, Y —
 * накопленные очки за фактически взятые КП, с учётом линейно нарастающего штрафа за опоздание
 * (см. [com.competra.domain.models.orienteering.buildScoreGraphData]). Аналог [RaceGraphChart] по
 * устройству (та же палитра [raceGraphPalette], тот же принцип легенды-переключателя видимости/
 * подсветки), но ось X непрерывная по времени, а не по позиции общего для всех КП — для BY_CHOICE
 * общего порядка КП не существует. Вертикальная пунктирная линия отмечает контрольное время
 * группы, если оно задано.
 */
@Composable
fun ScoreGraphChart(
    data: ScoreGraphData,
    visibleParticipantIds: Set<String>,
    highlightedParticipantId: String?,
    onToggleVisibility: (participantId: String) -> Unit,
    onToggleHighlight: (participantId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorByParticipantId = remember(data) {
        data.series.mapIndexed { index, series ->
            series.participant.id to raceGraphPalette[index % raceGraphPalette.size]
        }.toMap()
    }

    val visibleSeries = remember(data, visibleParticipantIds) {
        data.series.filter { it.participant.id in visibleParticipantIds }
    }

    val maxScore = remember(visibleSeries) {
        visibleSeries.flatMap { it.points }.maxOfOrNull { it.cumulativeScore } ?: 0
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(visibleSeries) {
        if (visibleSeries.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineModel {
                visibleSeries.forEach { s ->
                    if (s.points.isNotEmpty()) {
                        series(
                            x = s.points.map { it.elapsedSeconds },
                            y = s.points.map { it.cumulativeScore.toDouble() },
                            key = s.participant.id,
                        )
                    }
                }
            }
        }
    }

    Column(modifier = modifier) {
        if (visibleSeries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Нет участников для отображения на графике",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val lines = visibleSeries.map { s ->
                val baseColor = colorByParticipantId[s.participant.id] ?: Color.Gray
                val isDimmed = highlightedParticipantId != null && highlightedParticipantId != s.participant.id
                val pointColor = if (isDimmed) baseColor.copy(alpha = 0.25f) else baseColor
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(pointColor)),
                    pointProvider = LineCartesianLayer.PointProvider.single(
                        LineCartesianLayer.Point(
                            component = rememberShapeComponent(fill = Fill(pointColor), shape = CircleShape),
                            size = graphPointSize,
                        )
                    ),
                )
            }

            val timeLimitLineComponent = rememberLineComponent(
                fill = Fill(TIME_LIMIT_COLOR),
                thickness = 1.5.dp,
                shape = DashedShape(dashLength = 4.dp, gapLength = 3.dp),
            )
            val decorations = remember(data.timeLimitSeconds, timeLimitLineComponent) {
                data.timeLimitSeconds?.let { limit ->
                    listOf(VerticalLine(x = { limit.toDouble() }, line = timeLimitLineComponent))
                } ?: emptyList()
            }

            var verticalZoom by remember(data) { mutableFloatStateOf(VERTICAL_ZOOM_MIN) }
            var verticalPan by remember(data) { mutableFloatStateOf(0f) }
            // Горизонтальные zoom/scroll вынесены за пределы key(verticalZoom, verticalPan), чтобы
            // позиция и масштаб по X не сбрасывались при изменении вертикального зума/пана ниже.
            val horizontalZoomState = rememberVicoZoomState(initialZoom = Zoom.Content)
            val horizontalScrollState = rememberVicoScrollState()

            Box(modifier = Modifier.fillMaxWidth()) {
                // Vico пересчитывает Y-диапазон только при регистрации графика или при поступлении
                // новых данных, но не при простой смене rangeProvider на месте (см. CartesianChartModel.kt
                // collectAsState: LaunchedEffect(chartID, ...) не перезапускается, если chart.id не
                // меняется, а CartesianChart.copy() всегда сохраняет старый id). Поэтому единственный
                // надёжный способ применить новый rangeProvider — пересоздать CartesianChartHost целиком.
                key(verticalZoom, verticalPan) {
                    val rangeProvider = remember(verticalZoom, verticalPan) {
                        verticalZoomRangeProvider(verticalZoom, verticalPan)
                    }
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(lines),
                                rangeProvider = rangeProvider,
                            ),
                            startAxis = VerticalAxis.rememberStart(
                                valueFormatter = CartesianValueFormatter { _, value, _ -> value.toInt().toString() },
                                itemPlacer = remember(maxScore) {
                                    VerticalAxis.ItemPlacer.step({ niceScoreStep(maxScore) })
                                },
                            ),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                valueFormatter = CartesianValueFormatter { _, value, _ -> value.toLong().toRaceTime() },
                            ),
                            decorations = decorations,
                        ),
                        modelProducer = modelProducer,
                        scrollState = horizontalScrollState,
                        zoomState = horizontalZoomState,
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                    )
                }
                VerticalZoomControl(
                    zoom = verticalZoom,
                    panFraction = verticalPan,
                    onZoomIn = { verticalZoom = (verticalZoom + VERTICAL_ZOOM_STEP).coerceAtMost(VERTICAL_ZOOM_MAX) },
                    onZoomOut = { verticalZoom = (verticalZoom - VERTICAL_ZOOM_STEP).coerceAtLeast(VERTICAL_ZOOM_MIN) },
                    onPanUp = { verticalPan = (verticalPan + VERTICAL_PAN_STEP).coerceAtMost(VERTICAL_PAN_MAX) },
                    onPanDown = { verticalPan = (verticalPan - VERTICAL_PAN_STEP).coerceAtLeast(VERTICAL_PAN_MIN) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.SIZE_HALF.dp),
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(data.series, key = { it.participant.id }) { series ->
                ScoreGraphLegendRow(
                    series = series,
                    color = colorByParticipantId[series.participant.id] ?: Color.Gray,
                    isVisible = series.participant.id in visibleParticipantIds,
                    isHighlighted = series.participant.id == highlightedParticipantId,
                    onToggleVisibility = { onToggleVisibility(series.participant.id) },
                    onToggleHighlight = { onToggleHighlight(series.participant.id) },
                )
            }
        }
    }
}

@Composable
private fun ScoreGraphLegendRow(
    series: ScoreGraphSeries,
    color: Color,
    isVisible: Boolean,
    isHighlighted: Boolean,
    onToggleVisibility: () -> Unit,
    onToggleHighlight: () -> Unit,
) {
    val rowBackground = if (isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(enabled = isVisible, onClick = onToggleHighlight)
            .padding(horizontal = Dimens.SIZE_BASE.dp, vertical = Dimens.SIZE_QUARTER.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = isVisible, onCheckedChange = { onToggleVisibility() })

        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (isVisible) color else MaterialTheme.colorScheme.outlineVariant),
        )

        Column(modifier = Modifier.weight(1f).padding(start = Dimens.SIZE_HALF.dp)) {
            Text(
                text = "${series.participant.lastName} ${series.participant.firstName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            )
            // Участник вне конкурса места не имеет (rank = null) — подписываем «Вне конкурса».
            val placeText = if (series.participant.isOutOfCompetition) "Вне конкурса" else series.result?.rank?.let { "Место $it" }
            placeText?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
