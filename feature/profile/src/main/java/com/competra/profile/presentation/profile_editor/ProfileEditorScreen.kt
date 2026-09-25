package com.competra.profile.presentation.profile_editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.components.ImageCropperDialog
import com.competra.designsystem.components.NetworkImage
import com.competra.profile.presentation.components.GenderSelector
import com.competra.domain.models.Gender
import com.competra.ui.components.toCropRect
import com.competra.ui.components.toFractionalRect
import com.competra.domain.models.user.User
import com.competra.resources.R
import org.koin.androidx.compose.koinViewModel

/**
 * Экран редактирования профиля пользователя.
 * Позволяет изменять основные данные пользователя и сохранять их локально и на сервере.
 *
 * @param viewModel Вьюмодель экрана.
 */
@Composable
fun ProfileEditorScreen(
    viewModel: ProfileEditorViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            ProfileEditorContent(
                state = state,
                onAction = viewModel::onAction
            )
        }
    }
}

/**
 * Основной контент экрана редактирования профиля.
 *
 * @param state Состояние экрана.
 * @param onAction Обработчик действий пользователя.
 */
@Composable
private fun ProfileEditorContent(
    state: ProfileEditorState,
    onAction: (ProfileEditorAction) -> Unit
) {
    val scrollState = rememberScrollState()
    val user = state.user

    val photoPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let { onAction(ProfileEditorAction.PhotoPicked(it)) }
    }

    state.pendingCropUri?.let { uri ->
        ImageCropperDialog(
            uri = uri,
            aspectRatio = 1f,
            onConfirm = { cropRect -> onAction(ProfileEditorAction.ConfirmCrop(cropRect.toCropRect())) },
            onCancel = { onAction(ProfileEditorAction.CancelCrop) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Редактирование профиля",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Аватарка пользователя
        Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            NetworkImage(
                url = user?.avatarUrl,
                cropRect = user?.avatarCropRect?.toFractionalRect(),
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
            )
            IconButton(
                onClick = { photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.edit),
                    contentDescription = "Изменить фото",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Поля ввода для данных пользователя
        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            text = user?.lastName ?: "",
            onValueChanged = { onAction(ProfileEditorAction.UpdateLastName(it)) },
            label = { Text("Фамилия") }
        )

        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            text = user?.firstName ?: "",
            onValueChanged = { onAction(ProfileEditorAction.UpdateFirstName(it)) },
            label = { Text("Имя") }
        )

        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            text = user?.middleName ?: "",
            onValueChanged = { onAction(ProfileEditorAction.UpdateMiddleName(it)) },
            label = { Text("Отчество") }
        )

        GenderSelector(
            selected = user?.gender,
            onSelected = { onAction(ProfileEditorAction.UpdateGender(it)) }
        )

        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            text = user?.phoneNumber ?: "",
            onValueChanged = { onAction(ProfileEditorAction.UpdatePhoneNumber(it)) },
            label = { Text("Номер телефона") }
        )

        DSTextInput(
            modifier = Modifier.fillMaxWidth(),
            text = user?.email ?: "",
            onValueChanged = { onAction(ProfileEditorAction.UpdateEmail(it)) },
            label = { Text("Email") },
            // Email — идентификатор входа, сервер его не меняет
            readOnly = true
        )

        Spacer(modifier = Modifier.weight(1f))

        // Кнопка сохранения изменений
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            onClick = { onAction(ProfileEditorAction.SaveProfile) },
            enabled = !state.isSaving
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = "Сохранить", fontWeight = FontWeight.Bold)
            }
        }

        // Отображение ошибки, если есть
        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileEditorPreview() {
    MaterialTheme {
        ProfileEditorContent(
            state = ProfileEditorState(
                user = User(
                    id = "1",
                    firstName = "Иван",
                    lastName = "Иванов",
                    middleName = "Иванович",
                    birthDate = 0L,
                    gender = Gender.MALE,
                    avatarUrl = "",
                    phoneNumber = "+79991234567",
                    email = "ivanov@example.com",
                    qualification = emptyList()
                )
            ),
            onAction = {}
        )
    }
}
