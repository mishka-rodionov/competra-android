package com.competra.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import kotlin.math.max

/**
 * Компонент для отображения изображений из сети с fallback-заглушкой.
 *
 * @param url URL изображения (null — показывает цветную заглушку из темы).
 * @param modifier Модификатор.
 * @param contentScale Способ масштабирования. Игнорируется, если задан [cropRect].
 * @param contentDescription Описание для accessibility.
 * @param fullscreenOnClick Открывать ли изображение на весь экран по тапу.
 * Выключай там, где по картинке уже назначен другой клик (например, весь ряд списка).
 * @param cropRect Область кропа в нормализованных координатах (0..1) относительно
 * оригинального файла — сам файл на сервере не изменён, обрезка применяется только
 * при отображении. Если null, показывается всё изображение (обратная совместимость
 * со старыми аватарами/обложками без выбранной рамки).
 */
@Composable
fun NetworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    fullscreenOnClick: Boolean = true,
    cropRect: FractionalRect? = null
) {
    var isFullscreenVisible by remember { mutableStateOf(false) }

    if (url.isNullOrBlank()) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }

    val clickableModifier = if (fullscreenOnClick) {
        modifier.clickable { isFullscreenVisible = true }
    } else {
        modifier
    }

    if (cropRect == null) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = clickableModifier,
            contentScale = contentScale,
            placeholder = null,
            error = null
        )
    } else {
        CroppedNetworkImage(
            url = url,
            cropRect = cropRect,
            contentDescription = contentDescription,
            modifier = clickableModifier
        )
    }

    if (isFullscreenVisible) {
        FullscreenImageViewer(
            url = url,
            contentDescription = contentDescription,
            onDismiss = { isFullscreenVisible = false }
        )
    }
}

/**
 * Рисует только выбранную нормализованную область [cropRect] исходного изображения,
 * растягивая её на весь [modifier] — тот же принцип, что и у [ContentScale.Crop],
 * но применённый к прямоугольнику внутри изображения, а не к нему целиком.
 */
@Composable
private fun CroppedNetworkImage(
    url: String,
    cropRect: FractionalRect,
    contentDescription: String?,
    modifier: Modifier
) {
    var imageSize by remember(url) { mutableStateOf<Size?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val image = imageSize
        // requiredSize (не size!) — иначе родительский Box с фиксированным размером (например,
        // круглый аватар 100dp) обрежет запрошенный размер картинки ДО применения graphicsLayer,
        // и масштаб/сдвиг для кропа будут считаться от уже урезанного layout-бокса.
        val sizeModifier = if (image != null) {
            with(density) { Modifier.requiredSize(image.width.toDp(), image.height.toDp()) }
        } else {
            Modifier
        }
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.None,
            modifier = sizeModifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val current = image ?: return@graphicsLayer
                    if (containerSize == IntSize.Zero) return@graphicsLayer
                    val cropWidthPx = cropRect.width * current.width
                    val cropHeightPx = cropRect.height * current.height
                    if (cropWidthPx <= 0f || cropHeightPx <= 0f) return@graphicsLayer
                    val scale = max(containerSize.width / cropWidthPx, containerSize.height / cropHeightPx)
                    val displayedWidth = current.width * scale
                    val displayedHeight = current.height * scale
                    scaleX = scale
                    scaleY = scale
                    translationX = displayedWidth * (0.5f - cropRect.x - cropRect.width / 2f)
                    translationY = displayedHeight * (0.5f - cropRect.y - cropRect.height / 2f)
                },
            onSuccess = { state ->
                val image = state.result.image
                imageSize = Size(image.width.toFloat(), image.height.toFloat())
            }
        )
    }
}
