package com.competra.profile.presentation.auth

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.competra.designsystem.components.DSButton
import com.competra.designsystem.components.DSTextInput
import com.competra.profile.data.auth.AuthAction
import com.competra.profile.presentation.components.AuthHeader
import com.competra.resources.R
import org.koin.androidx.compose.koinViewModel

/**
 * Экран входа: ввод email, на который придёт код, и переход к регистрации.
 */
@Composable
fun AuthScreen(authViewModel: AuthViewModel = koinViewModel()) {
    val state by authViewModel.state.collectAsState()
    EmailInputContent(isLoading = state.isLoading, userAction = authViewModel::onAction)
}

/**
 * Контент экрана входа.
 *
 * @param isLoading идёт запрос кода — кнопка показывает прогресс
 * @param userAction обработчик действий пользователя
 */
@Composable
fun EmailInputContent(isLoading: Boolean = false, userAction: (AuthAction) -> Unit) {
    var email by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val trimmedEmail = email.trim()
    val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()
    val showEmailError = trimmedEmail.isNotEmpty() && !isEmailValid

    fun submit() {
        if (!isEmailValid || isLoading) return
        keyboardController?.hide()
        userAction.invoke(AuthAction.AuthClicked(trimmedEmail))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AuthHeader(
            iconRes = R.drawable.ic_launcher_monochrome,
            iconSize = 72.dp,
            title = stringResource(R.string.auth_title),
            subtitle = stringResource(R.string.auth_subtitle)
        )

        Spacer(modifier = Modifier.height(32.dp))

        DSTextInput(
            text = email,
            onValueChanged = { email = it },
            label = { Text(stringResource(R.string.label_email)) },
            leadingIcon = {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_mail_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            isError = showEmailError,
            supportingText = if (showEmailError) {
                { Text(stringResource(R.string.error_invalid_email)) }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(16.dp))

        DSButton(
            text = stringResource(R.string.auth_get_code),
            isEnabled = isEmailValid || isLoading,
            isLoading = isLoading,
            onClick = { submit() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.auth_no_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { userAction.invoke(AuthAction.ToRegistration) }) {
                Text(
                    text = stringResource(R.string.auth_to_registration),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // Запрос фокуса после того, как компонент будет отрисован
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
