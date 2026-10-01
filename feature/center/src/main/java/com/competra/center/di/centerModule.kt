package com.competra.center.di

import com.competra.center.data.interactors.OrienteeringCompetitionInteractor
import com.competra.center.presentation.draw.DrawViewModel
import com.competra.center.presentation.event_control.orienteering.OrienteeringEventControlViewModel
import com.competra.center.presentation.get_chip.GetOrienteeringChipViewModel
import com.competra.center.presentation.group_splits.GroupSplitsTableViewModel
import com.competra.center.presentation.race_graph.RaceGraphViewModel
import com.competra.center.presentation.score_graph.ScoreGraphViewModel
import com.competra.center.presentation.main.CenterViewModel
import com.competra.center.presentation.orientiring_competition_create.OrienteeringCreatorViewModel
import com.competra.center.presentation.participant_list.ParticipantListViewModel
import com.competra.center.presentation.read_card.OrientReadCardViewModel
import com.competra.center.presentation.results.OrienteeringCompetitionResultsViewModel
import com.competra.center.presentation.splits.ParticipantSplitsViewModel
import com.competra.center.presentation.start_grid.StartGridViewModel
import com.competra.center.presentation.write_chip.WriteChipViewModel
import com.competra.center.presentation.chip_inspect.ChipInspectViewModel
import com.competra.center.presentation.station_tools.StationToolsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val centerModule = module {
    viewModelOf(::CenterViewModel)
    viewModelOf(::OrienteeringCreatorViewModel)
    viewModelOf(::OrienteeringEventControlViewModel)
    viewModelOf(::OrientReadCardViewModel)
    viewModelOf(::ParticipantListViewModel)
    viewModelOf(::DrawViewModel)
    viewModelOf(::OrienteeringCompetitionResultsViewModel)
    viewModelOf(::GetOrienteeringChipViewModel)
    viewModelOf(::ParticipantSplitsViewModel)
    viewModelOf(::GroupSplitsTableViewModel)
    viewModelOf(::RaceGraphViewModel)
    viewModelOf(::ScoreGraphViewModel)
    viewModelOf(::StartGridViewModel)
    viewModelOf(::WriteChipViewModel)
    viewModelOf(::StationToolsViewModel)
    viewModelOf(::ChipInspectViewModel)
    factoryOf(::OrienteeringCompetitionInteractor)
}