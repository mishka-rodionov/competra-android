package com.competra.profile.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.competra.domain.models.Gender
import com.competra.resources.R

/**
 * Выбор пола пользователя: переключатель из двух сегментов «Мужской» / «Женский».
 *
 * @param selected выбранный пол, null — ещё не выбран
 * @param onSelected обработчик выбора
 * @param modifier модификатор контейнера
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GenderSelector(
    selected: Gender?,
    onSelected: (Gender) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        Gender.MALE to stringResource(R.string.gender_user_male),
        Gender.FEMALE to stringResource(R.string.gender_user_female)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.label_gender),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (gender, title) ->
                SegmentedButton(
                    selected = selected == gender,
                    onClick = { onSelected(gender) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(title) }
                )
            }
        }
    }
}
