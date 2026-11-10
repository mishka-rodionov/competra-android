package com.competra.profile.presentation.guides

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.competra.designsystem.theme.Dimens
import com.competra.designsystem.colors.LightColors
import com.competra.profile.data.GuideLinks
import com.competra.profile.presentation.main_profile.ProfileMenuItem
import com.competra.resources.R

/**
 * Экран «Инструкции»: список гидов организатора. Каждый гид открывается
 * в браузере со страницы на competra.ru ([GuideLinks]).
 */
@Composable
fun GuidesScreen() {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Инструкции",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Пошаговые гиды для организатора. Открываются в браузере на сайте competra.ru — нужен интернет.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            GuideLinks.all.forEachIndexed { index, guide ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp),
                        thickness = 0.5.dp,
                        color = LightColors.greyB8.copy(alpha = 0.3f)
                    )
                }
                ProfileMenuItem(
                    text = guide.title,
                    icon = ImageVector.vectorResource(R.drawable.ic_menu_book_24px),
                    subtitle = guide.description,
                    onClick = { uriHandler.openUri(guide.url) }
                )
            }
        }
    }
}
