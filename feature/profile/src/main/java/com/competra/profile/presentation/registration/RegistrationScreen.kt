package com.competra.profile.presentation.registration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.competra.designsystem.components.DSButton
import com.competra.designsystem.components.DSTextInput
import com.competra.profile.data.LegalLinks
import com.competra.profile.data.registration.RegistrationAction
import com.competra.profile.data.registration.RegistrationState
import com.competra.profile.presentation.components.GenderSelector
import com.competra.resources.R
import com.competra.utils.DateTimeFormat
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.ZoneOffset

/** Дата, на которой открывается календарь, если дата рождения ещё не выбрана (01.01.2000 UTC). */
private const val DEFAULT_PICKER_DATE_MILLIS = 946_684_800_000L

/** Самый ранний год, доступный для выбора даты рождения. */
private const val MIN_BIRTH_YEAR = 1920

/**
 * Экран ввода регистрационных данных
 * [email] - адрес электронной почты
 * [firstName] - имя
 * [lastName] - фамилия
 * [bdate] - дата рождения
 * [gender] - пол
 * */
@Composable
fun RegistrationScreen(viewModel: RegistrationViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RegistrationContent(state = state, userAction = viewModel::onAction)
}

/**
 * Контент экрана регистрации: шапка, поля анкеты, согласие с политикой и кнопка отправки.
 *
 * @param state текущее состояние формы
 * @param userAction обработчик действий пользователя
 */
@Composable
fun RegistrationContent(state: RegistrationState, userAction: (RegistrationAction) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showDatePicker by remember { mutableStateOf(false) }

    val showEmailError = state.email.isNotBlank() && !state.isEmailValid

    fun submit() {
        keyboardController?.hide()
        focusManager.clearFocus()
        userAction.invoke(RegistrationAction.RegisterUser)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RegistrationHeader()

        Spacer(modifier = Modifier.height(32.dp))

        DSTextInput(
            text = state.firstName,
            onValueChanged = { userAction.invoke(RegistrationAction.UpdateFirstName(it)) },
            label = { Text(stringResource(R.string.label_first_name)) },
            leadingIcon = { FieldIcon(R.drawable.ic_person_24px) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(12.dp))

        DSTextInput(
            text = state.lastName,
            onValueChanged = { userAction.invoke(RegistrationAction.UpdateLastName(it)) },
            label = { Text(stringResource(R.string.label_last_name)) },
            leadingIcon = { FieldIcon(R.drawable.ic_person_24px) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = {
                // Дата выбирается в диалоге, поэтому сразу открываем его
                focusManager.clearFocus()
                showDatePicker = true
            }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        BirthDateField(
            bdate = state.bdate,
            onClick = {
                focusManager.clearFocus()
                showDatePicker = true
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        GenderSelector(
            selected = state.gender,
            onSelected = {
                focusManager.clearFocus()
                userAction.invoke(RegistrationAction.UpdateGender(it))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        DSTextInput(
            text = state.email,
            onValueChanged = { userAction.invoke(RegistrationAction.UpdateEmail(it)) },
            label = { Text(stringResource(R.string.label_email)) },
            leadingIcon = { FieldIcon(R.drawable.ic_mail_24px) },
            singleLine = true,
            isError = showEmailError,
            supportingText = {
                Text(
                    stringResource(
                        if (showEmailError) R.string.error_invalid_email else R.string.registration_code_hint
                    )
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                focusManager.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        PrivacyConsent(
            accepted = state.privacyAccepted,
            onAcceptedChange = { userAction.invoke(RegistrationAction.UpdatePrivacyAccepted(it)) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        DSButton(
            text = stringResource(R.string.registration_submit),
            isEnabled = state.canSubmit || state.isLoading,
            isLoading = state.isLoading,
            onClick = { submit() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        )
    }

    if (showDatePicker) {
        BirthDatePickerDialog(
            initialDate = state.bdate,
            onDateSelected = { userAction.invoke(RegistrationAction.UpdateBdate(it)) },
            onDismiss = { showDatePicker = false }
        )
    }

    // Запрос фокуса после того, как компонент будет отрисован
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/** Шапка экрана: иконка, заголовок и пояснение. */
@Composable
private fun RegistrationHeader() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_account_circle_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.registration_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = stringResource(R.string.registration_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/** Иконка в начале поля ввода. */
@Composable
private fun FieldIcon(iconRes: Int) {
    Icon(
        imageVector = ImageVector.vectorResource(iconRes),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Поле даты рождения: только для чтения, по нажатию открывает календарь.
 *
 * @param bdate дата рождения в epoch millis (UTC), 0 — не выбрана
 * @param onClick обработчик нажатия на поле
 */
@Composable
private fun BirthDateField(bdate: Long, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) onClick()
        }
    }

    DSTextInput(
        text = DateTimeFormat.transformLongToDisplayDate(bdate, ZoneOffset.UTC),
        label = { Text(stringResource(R.string.label_birth_date)) },
        leadingIcon = { FieldIcon(R.drawable.ic_date_range_24px) },
        singleLine = true,
        readOnly = true,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Диалог выбора даты рождения. Будущие даты и годы раньше [MIN_BIRTH_YEAR] недоступны.
 *
 * @param initialDate текущая дата рождения в epoch millis (UTC), 0 — не выбрана
 * @param onDateSelected вызывается с выбранной датой (полночь UTC)
 * @param onDismiss закрытие диалога
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePickerDialog(
    initialDate: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember { LocalDate.now() }
    val todayMillis = remember(today) {
        today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.takeIf { it != 0L },
        initialDisplayedMonthMillis = initialDate.takeIf { it != 0L } ?: DEFAULT_PICKER_DATE_MILLIS,
        yearRange = MIN_BIRTH_YEAR..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = datePickerState.selectedDateMillis != null,
                onClick = {
                    datePickerState.selectedDateMillis?.let(onDateSelected)
                    onDismiss()
                }
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

/**
 * Согласие с обработкой персональных данных. Нажатие на текст переключает чекбокс,
 * нажатие на ссылку открывает политику конфиденциальности.
 *
 * @param accepted текущее значение согласия
 * @param onAcceptedChange обработчик изменения согласия
 */
@Composable
private fun PrivacyConsent(accepted: Boolean, onAcceptedChange: (Boolean) -> Unit) {
    val prefix = stringResource(R.string.label_privacy_consent_prefix)
    val policy = stringResource(R.string.label_privacy_policy)
    val linkColor = MaterialTheme.colorScheme.primary

    val text = remember(prefix, policy, linkColor) {
        buildAnnotatedString {
            append(prefix)
            append(" ")
            withLink(
                LinkAnnotation.Url(
                    url = LegalLinks.PRIVACY_POLICY_URL,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    )
                )
            ) {
                append(policy)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onAcceptedChange(!accepted) }
            .padding(vertical = 4.dp)
    ) {
        Checkbox(checked = accepted, onCheckedChange = onAcceptedChange)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
