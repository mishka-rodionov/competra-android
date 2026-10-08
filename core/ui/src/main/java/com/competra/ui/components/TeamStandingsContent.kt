package com.competra.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.competra.designsystem.theme.Dimens
import com.competra.domain.models.ResultStatus
import com.competra.domain.models.orienteering.GroupTeam
import com.competra.domain.models.orienteering.OverallTeam
import com.competra.domain.models.orienteering.TeamMemberResult
import com.competra.domain.models.orienteering.TeamOverallScope
import com.competra.domain.models.orienteering.TeamStandings
import com.competra.utils.orienteering.toRaceTime

/** Подпись общего командного зачёта. */
fun TeamOverallScope.title(): String = when (this) {
    TeamOverallScope.MEN -> "Мужчины"
    TeamOverallScope.WOMEN -> "Женщины"
    TeamOverallScope.ALL -> "Общий"
}

/** Ключ вкладки «По группам» среди чипов зачётов. */
private const val GROUPS_TAB = "GROUPS"

/**
 * Командный зачёт (docs/specs/team-scoring.md): чипы общих зачётов («Мужчины», «Женщины», «Общий»)
 * и «По группам»; строка команды раскрывается результатами — вошедшими в зачёт и (серым) остальными.
 * Переиспользуется результатами в `:feature:center` и в `:core:eventdetails`.
 */
@Composable
fun TeamStandingsContent(standings: TeamStandings, modifier: Modifier = Modifier) {
    val tabs = standings.overallStandings.map { it.scope.name } +
        listOfNotNull(GROUPS_TAB.takeIf { standings.groupStandings.isNotEmpty() })
    var selected by rememberSaveable { mutableStateOf(tabs.firstOrNull()) }
    val current = selected?.takeIf { it in tabs } ?: tabs.firstOrNull()

    Column(modifier = modifier.fillMaxSize()) {
        if (tabs.isEmpty()) {
            Text(
                text = "Командный зачёт пока пуст: нет результатов участников с командой",
                modifier = Modifier.padding(Dimens.SIZE_BASE.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        if (tabs.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.SIZE_BASE.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)
            ) {
                items(tabs) { tab ->
                    FilterChip(
                        selected = tab == current,
                        onClick = { selected = tab },
                        label = { Text(if (tab == GROUPS_TAB) "По группам" else TeamOverallScope.valueOf(tab).title()) }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimens.SIZE_BASE.dp)
        ) {
            if (current == GROUPS_TAB) {
                // Ключи — по индексу группы: в деталях события у групп с сервера groupId не заполнен
                // (одинаков у всех), уникален только remoteId.
                standings.groupStandings.forEachIndexed { groupIndex, groupStanding ->
                    item(key = "group-$groupIndex") {
                        Text(
                            text = groupStanding.group.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Dimens.SIZE_BASE.dp, bottom = Dimens.SIZE_QUARTER.dp)
                        )
                    }
                    items(groupStanding.teams, key = { "team-$groupIndex-${it.teamName}" }) { team ->
                        GroupTeamRow(team)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            } else {
                val overall = standings.overallStandings.firstOrNull { it.scope.name == current }
                items(overall?.teams.orEmpty(), key = { "overall-${it.teamName}" }) { team ->
                    OverallTeamRow(team)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun TeamRowHeader(place: Int?, name: String, total: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = place?.toString() ?: "—",
            modifier = Modifier.width(36.dp),
            fontWeight = FontWeight.Bold
        )
        Text(text = name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(text = total, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GroupTeamRow(team: GroupTeam) {
    var expanded by remember { mutableStateOf(false) }
    val total = team.points?.let { "$it оч." }
        ?: team.timeSeconds?.toRaceTime()
        ?: "вне зачёта"
    Column {
        TeamRowHeader(team.place, team.teamName, total) { expanded = !expanded }
        if (expanded) team.members.forEach { MemberRow(it) }
    }
}

@Composable
private fun MemberRow(member: TeamMemberResult) {
    val color = if (member.counted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val result = when {
        member.status != ResultStatus.FINISHED -> member.status.name
        member.points > 0 -> "${member.place} м. · ${member.points} оч."
        else -> listOfNotNull(member.place?.let { "$it м." }, member.timeSeconds?.toRaceTime()).joinToString(" · ")
    }
    Row(modifier = Modifier.fillMaxWidth().padding(start = 36.dp, bottom = Dimens.SIZE_QUARTER.dp)) {
        Text(
            text = "${member.participant.lastName} ${member.participant.firstName}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
        Text(text = result, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun OverallTeamRow(team: OverallTeam) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TeamRowHeader(team.place, team.teamName, "${team.points} оч.") { expanded = !expanded }
        if (expanded) {
            team.groups.forEach { group ->
                Row(modifier = Modifier.fillMaxWidth().padding(start = 36.dp, bottom = Dimens.SIZE_QUARTER.dp)) {
                    Text(text = group.group.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(text = "${group.place} м. · ${group.points} оч.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Переключатель «Личный / Командный» над результатами — показывается, если в соревновании есть командный зачёт. */
@Composable
fun ResultsModeToggle(showTeam: Boolean, onChange: (showTeam: Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Dimens.SIZE_HALF.dp)) {
        FilterChip(selected = !showTeam, onClick = { onChange(false) }, label = { Text("Личный") })
        FilterChip(selected = showTeam, onClick = { onChange(true) }, label = { Text("Командный") })
    }
}
