package com.competra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import com.competra.domain.models.orienteering.RaceGraphData
import com.competra.domain.models.orienteering.RaceGraphSeries
import com.competra.utils.orienteering.toRaceTime
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

/** Палитра цветов линий графика — циклическая, привязана к позиции участника в [RaceGraphData.series].
 * internal, а не private — переиспользуется [ScoreGraphChart] для единой цветовой схемы графиков. */
internal val raceGraphPalette = listOf(
    Color(0xFF2196F3), Color(0xFFE91E63), Color(0xFF4CAF50), Color(0xFFFF9800),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFFC107), Color(0xFF795548),
    Color(0xFF607D8B), Color(0xFF3F51B5), Color(0xFF8BC34A), Color(0xFFFF5722),
)

/** Диаметр точки-маркера, которой отмечается каждый взятый КП на линии графика. */
internal val graphPointSize = 6.dp

/** Минимальный, максимальный и шаговый множитель ручного вертикального зума графиков сплитов. */
internal const val VERTICAL_ZOOM_MIN = 1f
internal const val VERTICAL_ZOOM_MAX = 4f
internal const val VERTICAL_ZOOM_STEP = 0.5f

/** Позиция центра видимого окна между границами исходного диапазона: -1 — окно у нижней границы,
 * 0 — по центру, +1 — у верхней границы. Двигается кнопками ↑/↓, доступно только при зуме > 1x. */
internal const val VERTICAL_PAN_MIN = -1f
internal const val VERTICAL_PAN_MAX = 1f
internal const val VERTICAL_PAN_STEP = 0.34f

/**
 * [CartesianLayerRangeProvider], сужающий стандартный авто-диапазон Vico по Y вокруг центра,
 * сдвинутого на [panFraction] от него, в [zoom] раз. При [zoom] = [VERTICAL_ZOOM_MIN] совпадает с
 * [CartesianLayerRangeProvider.auto] независимо от [panFraction]. Используется как ручная замена
 * жеста вертикального зума/пана, которых у Vico нет: сужение диапазона визуально "разносит" близкие
 * друг к другу линии по вертикали, а сдвиг центра позволяет "прокрутить" суженное окно вверх/вниз,
 * не выходя за пределы исходного диапазона данных.
 */
internal fun verticalZoomRangeProvider(zoom: Float, panFraction: Float): CartesianLayerRangeProvider {
    if (zoom <= VERTICAL_ZOOM_MIN) return CartesianLayerRangeProvider.auto()
    val auto = CartesianLayerRangeProvider.auto()
    val clampedPan = panFraction.coerceIn(VERTICAL_PAN_MIN, VERTICAL_PAN_MAX)

    fun window(minY: Double, maxY: Double, extraStore: ExtraStore): ClosedFloatingPointRange<Double> {
        val autoMin = auto.getMinY(minY, maxY, extraStore)
        val autoMax = auto.getMaxY(minY, maxY, extraStore)
        val halfRange = (autoMax - autoMin) / 2
        val halfWindow = halfRange / zoom
        val center = (autoMin + autoMax) / 2 + clampedPan * (halfRange - halfWindow)
        return (center - halfWindow)..(center + halfWindow)
    }

    return object : CartesianLayerRangeProvider {
        override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) =
            window(minY, maxY, extraStore).start

        override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
            window(minY, maxY, extraStore).endInclusive
    }
}

/**
 * Плавающий контрол вертикального зума/пана, накладывается поверх графика: +/- сужают/расширяют
 * видимый по Y диапазон, ↑/↓ двигают суженное окно вверх/вниз в пределах исходных данных (активны
 * только пока [zoom] > 1x, то есть пока вообще есть что панить).
 */
@Composable
internal fun VerticalZoomControl(
    zoom: Float,
    panFraction: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onPanUp: () -> Unit,
    onPanDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canPan = zoom > VERTICAL_ZOOM_MIN
    Column(
        modifier = modifier
            .width(Dimens.SIZE_TRIPLE.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(percent = 50),
            ),
    ) {
        VerticalZoomButton(symbol = "+", enabled = zoom < VERTICAL_ZOOM_MAX, onClick = onZoomIn)
        VerticalZoomButton(symbol = "−", enabled = zoom > VERTICAL_ZOOM_MIN, onClick = onZoomOut)
        HorizontalDivider(modifier = Modifier.width(Dimens.SIZE_TRIPLE.dp), color = MaterialTheme.colorScheme.outlineVariant)
        VerticalZoomButton(symbol = "↑", enabled = canPan && panFraction < VERTICAL_PAN_MAX, onClick = onPanUp)
        VerticalZoomButton(symbol = "↓", enabled = canPan && panFraction > VERTICAL_PAN_MIN, onClick = onPanDown)
    }
}

@Composable
private fun VerticalZoomButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(Dimens.SIZE_TRIPLE.dp)) {
        Text(text = symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/**
 * График отставания от лидера по каждому КП (аналог WinSplits): X — КП по порядку дистанции,
 * Y — отставание в секундах (лидер всегда на нуле, чем ниже линия — тем больше отставание).
 *
 * Список участников снизу совмещает роль легенды и переключателя видимости: чекбокс
 * добавляет/убирает линию с графика, тап по строке подсвечивает линию этого участника.
 */
@Composable
fun RaceGraphChart(
    data: RaceGraphData,
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

    val controlLabels = remember(data) { data.columns.map { it.controlPoint.toString() } }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(visibleSeries) {
        if (visibleSeries.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineModel {
                visibleSeries.forEach { s ->
                    val points = s.points.filter { it.deltaSeconds != null }
                    if (points.isNotEmpty()) {
                        series(
                            x = points.map { it.positionIndex },
                            y = points.map { -(it.deltaSeconds ?: 0L).toDouble() },
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
                                valueFormatter = CartesianValueFormatter { _, value, _ ->
                                    (-value).toLong().toRaceTime()
                                },
                            ),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                valueFormatter = CartesianValueFormatter { _, value, _ ->
                                    controlLabels.getOrNull(value.toInt() - 1) ?: value.toInt().toString()
                                },
                            ),
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
                RaceGraphLegendRow(
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
private fun RaceGraphLegendRow(
    series: RaceGraphSeries,
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
