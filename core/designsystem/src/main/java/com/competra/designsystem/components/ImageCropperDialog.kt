package com.competra.designsystem.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.competra.designsystem.theme.Dimens
import com.competra.resources.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val SCRIM_ALPHA = 0.65f
private val FRAME_BORDER_WIDTH = 2.dp
private val HANDLE_HIT_RADIUS = 24.dp
private val HANDLE_VISUAL_RADIUS = 5.dp
private const val MIN_FRAME_SCALE = 0.2f

private enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

private fun Corner.opposite(): Corner = when (this) {
    Corner.TOP_LEFT -> Corner.BOTTOM_RIGHT
    Corner.TOP_RIGHT -> Corner.BOTTOM_LEFT
    Corner.BOTTOM_LEFT -> Corner.TOP_RIGHT
    Corner.BOTTOM_RIGHT -> Corner.TOP_LEFT
}

private fun Rect.corner(c: Corner): Offset = when (c) {
    Corner.TOP_LEFT -> topLeft
    Corner.TOP_RIGHT -> Offset(right, top)
    Corner.BOTTOM_LEFT -> Offset(left, bottom)
    Corner.BOTTOM_RIGHT -> Offset(right, bottom)
}

/**
 * Диалог выбора области кропа изображения перед загрузкой.
 *
 * Оригинальный файл уходит на сервер без изменений — результат этого диалога
 * это только нормализованные координаты рамки (0..1) относительно исходного
 * изображения, которые сервер хранит отдельно и использует при отображении
 * аватара/обложки.
 *
 * Фото всегда показывается целиком (letterbox, [ContentScale.Fit]) и не двигается —
 * пользователь двигает и масштабирует (за угловые маркеры, с сохранением [aspectRatio])
 * саму белую рамку поверх изображения, отмечая ей область будущего кропа.
 *
 * @param uri Uri выбранного пользователем изображения.
 * @param aspectRatio Соотношение сторон рамки кропа (1f — аватар, 16f/9f — обложка).
 * @param onConfirm Колбек с выбранной рамкой. Байты файла к этому моменту не тронуты.
 * @param onCancel Колбек отмены — весь флоу загрузки должен прерваться, ничего не грузится.
 */
@Composable
fun ImageCropperDialog(
    uri: Uri,
    aspectRatio: Float,
    onConfirm: (FractionalRect) -> Unit,
    onCancel: () -> Unit
) {
    var imageIntrinsicSize by remember { mutableStateOf<Size?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var frameRect by remember { mutableStateOf<Rect?>(null) }

    /** Прямоугольник, который реально занимает изображение внутри канваса при [ContentScale.Fit]. */
    fun displayedImageRect(): Rect? {
        val image = imageIntrinsicSize ?: return null
        if (canvasSize == IntSize.Zero) return null
        val canvasWidth = canvasSize.width.toFloat()
        val canvasHeight = canvasSize.height.toFloat()
        val fitScale = min(canvasWidth / image.width, canvasHeight / image.height)
        val displayedWidth = image.width * fitScale
        val displayedHeight = image.height * fitScale
        val left = (canvasWidth - displayedWidth) / 2f
        val top = (canvasHeight - displayedHeight) / 2f
        return Rect(left, top, left + displayedWidth, top + displayedHeight)
    }

    /** Максимальный прямоугольник с заданным [aspectRatio], помещающийся в [imageRect]. */
    fun baseFrameSize(imageRect: Rect): Size {
        return if (imageRect.width / imageRect.height > aspectRatio) {
            Size(imageRect.height * aspectRatio, imageRect.height)
        } else {
            Size(imageRect.width, imageRect.width / aspectRatio)
        }
    }

    LaunchedEffect(imageIntrinsicSize, canvasSize, aspectRatio) {
        if (frameRect != null) return@LaunchedEffect
        val imageRect = displayedImageRect() ?: return@LaunchedEffect
        val base = baseFrameSize(imageRect)
        val left = imageRect.left + (imageRect.width - base.width) / 2f
        val top = imageRect.top + (imageRect.height - base.height) / 2f
        frameRect = Rect(left, top, left + base.width, top + base.height)
    }

    fun computeCropRect(): FractionalRect? {
        val imageRect = displayedImageRect() ?: return null
        val frame = frameRect ?: return null
        return FractionalRect(
            x = ((frame.left - imageRect.left) / imageRect.width).coerceIn(0f, 1f),
            y = ((frame.top - imageRect.top) / imageRect.height).coerceIn(0f, 1f),
            width = (frame.width / imageRect.width).coerceIn(0f, 1f),
            height = (frame.height / imageRect.height).coerceIn(0f, 1f)
        )
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val density = LocalDensity.current
            val handleHitRadiusPx = with(density) { HANDLE_HIT_RADIUS.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(Dimens.SIZE_BASE.dp)
                    .onSizeChanged { canvasSize = it },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { state ->
                        val image = state.result.image
                        imageIntrinsicSize = Size(image.width.toFloat(), image.height.toFloat())
                    }
                )

                // Затемняет всё вне рамки, рисует её границу и угловые маркеры для ресайза.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .pointerInput(imageIntrinsicSize, canvasSize, aspectRatio) {
                            var dragCorner: Corner? = null
                            var anchor = Offset.Zero
                            var isMoving = false

                            detectDragGestures(
                                onDragStart = onStart@{ startPos ->
                                    val frame = frameRect ?: return@onStart
                                    val nearestCorner = Corner.entries.minByOrNull {
                                        (frame.corner(it) - startPos).getDistance()
                                    }
                                    when {
                                        nearestCorner != null &&
                                            (frame.corner(nearestCorner) - startPos).getDistance() <= handleHitRadiusPx -> {
                                            dragCorner = nearestCorner
                                            anchor = frame.corner(nearestCorner.opposite())
                                            isMoving = false
                                        }
                                        frame.contains(startPos) -> {
                                            dragCorner = null
                                            isMoving = true
                                        }
                                        else -> {
                                            dragCorner = null
                                            isMoving = false
                                        }
                                    }
                                },
                                onDrag = onDrag@{ change, dragAmount ->
                                    change.consume()
                                    val imageRect = displayedImageRect() ?: return@onDrag
                                    val currentFrame = frameRect ?: return@onDrag
                                    val corner = dragCorner
                                    if (corner != null) {
                                        val pointer = change.position
                                        val base = baseFrameSize(imageRect)
                                        val signX = if (corner == Corner.TOP_RIGHT || corner == Corner.BOTTOM_RIGHT) 1f else -1f
                                        val signY = if (corner == Corner.BOTTOM_LEFT || corner == Corner.BOTTOM_RIGHT) 1f else -1f
                                        val delta = pointer - anchor
                                        val baseDiagonal = sqrt(base.width * base.width + base.height * base.height)
                                        val rawScale = delta.getDistance() / baseDiagonal
                                        val maxScaleX = if (signX > 0) (imageRect.right - anchor.x) / base.width
                                        else (anchor.x - imageRect.left) / base.width
                                        val maxScaleY = if (signY > 0) (imageRect.bottom - anchor.y) / base.height
                                        else (anchor.y - imageRect.top) / base.height
                                        val maxScale = max(MIN_FRAME_SCALE, min(maxScaleX, maxScaleY))
                                        val scale = rawScale.coerceIn(MIN_FRAME_SCALE, maxScale)
                                        val w = base.width * scale
                                        val h = base.height * scale
                                        val left = if (signX > 0) anchor.x else anchor.x - w
                                        val top = if (signY > 0) anchor.y else anchor.y - h
                                        frameRect = Rect(left, top, left + w, top + h)
                                    } else if (isMoving) {
                                        val newLeft = (currentFrame.left + dragAmount.x)
                                            .coerceIn(imageRect.left, imageRect.right - currentFrame.width)
                                        val newTop = (currentFrame.top + dragAmount.y)
                                            .coerceIn(imageRect.top, imageRect.bottom - currentFrame.height)
                                        frameRect = Rect(newLeft, newTop, newLeft + currentFrame.width, newTop + currentFrame.height)
                                    }
                                }
                            )
                        }
                ) {
                    val frame = frameRect ?: return@Canvas
                    drawRect(color = Color.Black.copy(alpha = SCRIM_ALPHA))
                    drawRect(color = Color.Transparent, topLeft = frame.topLeft, size = frame.size, blendMode = BlendMode.Clear)
                    drawRect(color = Color.White, topLeft = frame.topLeft, size = frame.size, style = Stroke(width = FRAME_BORDER_WIDTH.toPx()))
                    val handleVisualRadiusPx = HANDLE_VISUAL_RADIUS.toPx()
                    for (c in Corner.entries) {
                        drawCircle(color = Color.White, radius = handleVisualRadiusPx, center = frame.corner(c))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SIZE_BASE.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onCancel) {
                    Text(text = "Отмена", color = Color.White)
                }
                TextButton(
                    onClick = {
                        computeCropRect()?.let(onConfirm)
                    }
                ) {
                    Text(text = stringResource(id = R.string.label_apply), color = Color.White)
                }
            }
        }
    }
}
