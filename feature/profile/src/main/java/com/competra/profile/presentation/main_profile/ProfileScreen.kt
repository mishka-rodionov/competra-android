package com.competra.profile.presentation.main_profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.competra.designsystem.components.DSBottomDialog
import com.competra.designsystem.components.DSButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.competra.designsystem.components.NetworkImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.competra.designsystem.colors.LightColors
import com.competra.designsystem.theme.Dimens
import com.competra.profile.data.ProfileAction
import com.competra.profile.data.ProfileState
import com.competra.resources.R
import com.competra.ui.components.toFractionalRect
import com.competra.utils.DateTimeFormat
import org.koin.androidx.compose.koinViewModel

/**
 * Экран профиля пользователя.
 * Обеспечивает отображение данных профиля или предложение авторизации.
 */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = koinViewModel()) {

    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.getCurrentUser()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (state.user == null) {
            UnauthorizedUser(viewModel::onAction)
        } else {
            AuthorizedUser(state, viewModel::onAction)
        }
    }
}

/**
 * Отображение для неавторизованного пользователя.
 * Привлекательный экран с предложением войти в систему.
 */
@Composable
fun UnauthorizedUser(userAction: (ProfileAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SIZE_BASE.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_account_circle_24px),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))
        
        Text(
            text = "Добро пожаловать!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))
        
        Text(
            text = "Войдите или зарегистрируйтесь, чтобы сохранять свои достижения и участвовать в событиях.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp)
        )
        
        Spacer(modifier = Modifier.height(Dimens.SIZE_TRIPLE.dp))
        
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { userAction.invoke(ProfileAction.ToAuth) },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Войти", modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { userAction.invoke(ProfileAction.ToRegister) },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Создать аккаунт", modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

/**
 * Отображение данных авторизованного пользователя.
 * Группирует информацию в карточки и разделы для лучшего UX.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizedUser(state: ProfileState, onAction: (ProfileAction) -> Unit) {
    val user = state.user
    val scrollState = rememberScrollState()
    var showLogoutSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val deleteAccountSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Dimens.SIZE_BASE.dp)
    ) {
        // Карточка профиля (Header)
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                // Аватар с кнопкой редактирования
                Box {
                    NetworkImage(
                        url = user?.avatarUrl,
                        cropRect = user?.avatarCropRect?.toFractionalRect(),
                        contentDescription = "User Avatar",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                    )
                    
                    IconButton(
                        onClick = { onAction(ProfileAction.ToProfileEditor) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.edit),
                            contentDescription = "Edit Profile",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))

                Column {
                    Text(
                        text = "${user?.firstName} ${user?.lastName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = DateTimeFormat.transformLongToDisplayDate(user?.birthDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))

        // Секция активности
        Text(
            text = "Активность",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                ProfileMenuItem(
                    text = "Предстоящие старты",
                    icon = ImageVector.vectorResource(R.drawable.ic_date_range_24px),
                    onClick = { onAction(ProfileAction.ToUserRegistrations) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp),
                    thickness = 0.5.dp,
                    color = LightColors.greyB8.copy(alpha = 0.3f)
                )
                ProfileMenuItem(
                    text = "Мои результаты",
                    icon = ImageVector.vectorResource(R.drawable.ic_star_24px),
                    onClick = { /* TODO */ }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp),
                    thickness = 0.5.dp,
                    color = LightColors.greyB8.copy(alpha = 0.3f)
                )
                ProfileMenuItem(
                    text = "Результаты в протоколах",
                    icon = ImageVector.vectorResource(R.drawable.ic_date_range_24px),
                    subtitle = when {
                        state.linkSuggestionsCount > 0 -> "Найдены похожие на ваши: ${state.linkSuggestionsCount}"
                        state.pendingLinkRequestsCount > 0 -> "Заявок на рассмотрении: ${state.pendingLinkRequestsCount}"
                        else -> "Привяжите результаты, внесённые организатором вручную"
                    },
                    isHighlighted = state.linkSuggestionsCount > 0,
                    onClick = { onAction(ProfileAction.ToResultLinks) }
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

        // Секция настроек/другого
        Text(
            text = "Дополнительно",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.SIZE_BASE.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            ProfileMenuItem(
                text = "О приложении",
                icon = ImageVector.vectorResource(R.drawable.ic_info_24px),
                onClick = { onAction(ProfileAction.ToAboutApp) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp),
                thickness = 0.5.dp,
                color = LightColors.greyB8.copy(alpha = 0.3f)
            )
            ProfileMenuItem(
                text = "Push-уведомления",
                icon = ImageVector.vectorResource(R.drawable.ic_build_24px),
                onClick = { onAction(ProfileAction.ToPushPreferences) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Dimens.SIZE_BASE.dp),
                thickness = 0.5.dp,
                color = LightColors.greyB8.copy(alpha = 0.3f)
            )
            ProfileMenuItem(
                text = "Показать онбординг снова",
                icon = ImageVector.vectorResource(R.drawable.play_arrow_24px),
                onClick = { onAction(ProfileAction.ShowOnboarding) }
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showLogoutSheet = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Text(text = "Выйти", modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onAction(ProfileAction.OpenDeleteAccountConfirm) },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Text(text = "Удалить аккаунт", modifier = Modifier.padding(vertical = 4.dp))
        }
    }

    if (showLogoutSheet) {
        DSBottomDialog(
            sheetState = sheetState,
            onDismiss = { showLogoutSheet = false },
            sheetContent = {
                Text(
                    text = "Выход из аккаунта",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

                Text(
                    text = "Вы действительно хотите выйти?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))

                DSButton(
                    text = "Выход",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showLogoutSheet = false
                        onAction(ProfileAction.Logout)
                    }
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showLogoutSheet = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Отмена", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        )
    }

    if (state.showDeleteAccountConfirm) {
        DSBottomDialog(
            sheetState = deleteAccountSheetState,
            onDismiss = { onAction(ProfileAction.CloseDeleteAccountConfirm) },
            sheetContent = {
                Text(
                    text = "Удаление аккаунта",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

                Text(
                    text = "Аккаунт и связанные с ним персональные данные будут удалены безвозвратно. " +
                        "Это действие нельзя отменить.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_DOUBLE.dp))

                DSButton(
                    text = "Удалить безвозвратно",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onAction(ProfileAction.DeleteAccount) }
                )

                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onAction(ProfileAction.CloseDeleteAccountConfirm) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Отмена", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        )
    }
}

/**
 * Элемент меню в профиле пользователя.
 * Содержит иконку, текст и шеврон перехода.
 */
@Composable
fun ProfileMenuItem(
    text: String,
    icon: ImageVector,
    subtitle: String? = null,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(Dimens.SIZE_BASE.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(Dimens.SIZE_BASE.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isHighlighted) FontWeight.Medium else null
                )
            }
        }
        
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_chevron_forward_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}
