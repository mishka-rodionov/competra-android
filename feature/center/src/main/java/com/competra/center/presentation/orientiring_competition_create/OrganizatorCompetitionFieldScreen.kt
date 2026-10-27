package com.competra.center.presentation.orientiring_competition_create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.competra.designsystem.components.DSTextInput
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.club.Club
import com.competra.center.data.creator.OrienteeringCreatorAction
import com.competra.center.data.creator.OrienteeringCreatorState
import org.koin.androidx.compose.koinViewModel

/**
 * Форматирует строку цифр телефона в вид `+7 XXX XXX XX XX`.
 * Принимает только цифры, возвращает отформатированную строку.
 */
private class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = buildString {
            digits.take(11).forEachIndexed { i, c ->
                when (i) {
                    0 -> append("+$c ")
                    3 -> append("$c ")
                    6 -> append("$c ")
                    8 -> append("$c ")
                    else -> append(c)
                }
            }
        }

        // Строим маппинги позиций на основе реального отформатированного текста
        val originalToFormatted = IntArray(digits.length + 1)
        val formattedToOriginal = IntArray(formatted.length + 1)

        var rawIndex = 0
        for (fmtIndex in formatted.indices) {
            formattedToOriginal[fmtIndex] = rawIndex
            if (formatted[fmtIndex].isDigit()) {
                originalToFormatted[rawIndex] = fmtIndex
                rawIndex++
            }
        }
        originalToFormatted[rawIndex] = formatted.length
        formattedToOriginal[formatted.length] = rawIndex

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalToFormatted[offset.coerceIn(0, digits.length)]

            override fun transformedToOriginal(offset: Int): Int =
                formattedToOriginal[offset.coerceIn(0, formatted.length)]
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

/**
 * Третий экран создания соревнования: Информация об организаторе.
 *
 * @param competitionId Идентификатор соревнования.
 * @param viewModel Вьюмодель процесса создания.
 */
@Composable
fun OrganizatorCompetitionFieldScreen(
    competitionId: String,
    viewModel: OrienteeringCreatorViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize(competitionId)
        viewModel.loadMyClubs()
    }

    OrganizatorCompetitionFieldContent(
        state = state,
        onBack = viewModel::back,
        onNext = viewModel::saveStepThree,
        onUpdateMapUrl = viewModel::updateMapUrl,
        onUpdateContactPhone = viewModel::updateContactPhone,
        onUpdateContactEmail = viewModel::updateContactEmail,
        onUpdateWebsite = viewModel::updateWebsite,
        onUpdateOrganizingClubId = viewModel::updateOrganizingClubId,
        onUpdateOrganizerName = viewModel::updateOrganizerName,
        onAction = viewModel::onAction
    )
}

/**
 * Контент экрана контактов и медиа.
 * Выделен отдельно для поддержки Preview.
 */
@Composable
private fun OrganizatorCompetitionFieldContent(
    state: OrienteeringCreatorState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onUpdateMapUrl: (String) -> Unit,
    onUpdateContactPhone: (String) -> Unit,
    onUpdateContactEmail: (String) -> Unit,
    onUpdateWebsite: (String) -> Unit,
    onUpdateOrganizingClubId: (String?) -> Unit = {},
    onUpdateOrganizerName: (String) -> Unit = {},
    onAction: (OrienteeringCreatorAction) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    val mapFilePicker = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let { onAction(OrienteeringCreatorAction.UploadCompetitionMap(it)) }
    }

    Scaffold(
        bottomBar = {
            NavigationButtons(
                onBack = onBack,
                onNext = onNext
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Dimens.SIZE_BASE.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Шаг 3: Контакты и медиа",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            DSTextInput(
                modifier = Modifier.fillMaxWidth(),
                text = state.organizerName,
                label = { Text("Организатор (ФИО)") },
                supportingText = {
                    Text("По умолчанию — ваше имя из профиля. Можно указать другого человека, он не обязан быть в Competra")
                },
                onValueChanged = onUpdateOrganizerName
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

            Text(
                text = "Карта района соревнований",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
//            FieldDescription("Карта дистанций, которую увидят участники перед стартом")
            Spacer(modifier = Modifier.height(4.dp))

            DSTextInput(
                modifier = Modifier.fillMaxWidth(),
                text = state.mapUrl,
                label = { Text("Ссылка на карту") },
                supportingText = { Text("Прямая ссылка на файл карты, доступной для скачивания") },
                onValueChanged = onUpdateMapUrl
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = { mapFilePicker.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Загрузить файл карты")
            }

            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

            DSTextInput(
                modifier = Modifier.fillMaxWidth(),
                text = state.contactPhone.filter { it.isDigit() }.take(11),
                label = { Text("Контактный телефон") },
                isError = state.errors.isEmptyContactPhone,
                supportingText = {
                    if (state.errors.isEmptyContactPhone) {
                        Text("Укажите контактный телефон")
                    } else {
                        Text("Номер для оперативной связи участников с организаторами")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                visualTransformation = PhoneNumberVisualTransformation(),
                onValueChanged = { newValue ->
                    onUpdateContactPhone(newValue.filter { it.isDigit() }.take(11))
                }
            )

            Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

            DSTextInput(
                modifier = Modifier.fillMaxWidth(),
                text = state.contactEmail,
                label = { Text("Контактный Email") },
                supportingText = { Text("Адрес почты для вопросов и заявок участников") },
                onValueChanged = onUpdateContactEmail
            )

            if (false) { // на данный момент отключено
                Spacer(modifier = Modifier.height(Dimens.SIZE_HALF.dp))

                DSTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    text = state.website,
                    label = { Text("Официальный сайт") },
                    onValueChanged = onUpdateWebsite
                )
            }

            if (state.myClubs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.SIZE_BASE.dp))

                Text(
                    text = "Клуб-организатор",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                OrganizingClubPicker(
                    clubs = state.myClubs,
                    selectedClubId = state.organizingClubId,
                    onSelect = onUpdateOrganizingClubId
                )
            }
        }
    }
}

/**
 * Выбор опционального клуба-организатора из клубов пользователя, где он FOUNDER/ADMIN.
 * Пункт "Без клуба" очищает выбор — соревнование создаётся от лица пользователя, как обычно.
 */
@Composable
private fun OrganizingClubPicker(
    clubs: List<Club>,
    selectedClubId: String?,
    onSelect: (String?) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedName = clubs.firstOrNull { it.id == selectedClubId }?.name ?: "Без клуба"

    Box {
        OutlinedButton(onClick = { isExpanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedName)
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Без клуба") },
                onClick = {
                    onSelect(null)
                    isExpanded = false
                }
            )
            clubs.forEach { club ->
                DropdownMenuItem(
                    text = { Text(club.name) },
                    onClick = {
                        onSelect(club.id)
                        isExpanded = false
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OrganizatorCompetitionFieldPreview() {
    MaterialTheme {
        OrganizatorCompetitionFieldContent(
            state = OrienteeringCreatorState(
                mapUrl = "https://example.com/map",
                contactPhone = "+79991234567",
                contactEmail = "org@example.com",
                website = "https://example.com"
            ),
            onBack = {},
            onNext = {},
            onUpdateMapUrl = {},
            onUpdateContactPhone = {},
            onUpdateContactEmail = {},
            onUpdateWebsite = {},
            onAction = {}
        )
    }
}
