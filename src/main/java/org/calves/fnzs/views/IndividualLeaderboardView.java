package org.calves.fnzs.views;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.dom.ThemeList;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import com.vaadin.flow.theme.material.Material;
import org.calves.fnzs.controller.FnzsController;
import org.calves.yunite4j.dto.Team;
import org.calves.yunite4j.dto.Tournament;
import utils.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author carlos.pedroalves
 */
@Route("leaderboard/individual")
public class IndividualLeaderboardView extends VerticalLayout implements HasUrlParameter<String> {

    private Grid<Team> grid = new Grid<>(Team.class, false);
    private H1 tournamentTitle = new H1("Loading tournament data");
    private Tournament tournament;
    private List<Team> leaderboard = new ArrayList<>();

    public IndividualLeaderboardView() {
        grid.addColumn(Team::getPlacement).setHeader("Rank");
        grid.addColumn(team -> team.getUsers().getFirst().getEpicUsername()).setHeader("Player");
        grid.addColumn(Team::getGames).setHeader("Games");
        grid.addColumn(Team::getKills).setHeader("Eliminations");
        grid.addColumn(Team::getWins).setHeader("Wins");
        grid.addColumn(Team::getScore).setHeader("Score");
        grid.addColumn(team -> MathUtils.roundToDecimalPlaces(team.getAveragePlacement(), 1)).setHeader("Average Placement");
        grid.addColumn(team -> MathUtils.roundToDecimalPlaces(team.getAverageSecondsSurvived() / 60, 1)).setHeader("Average Minutes Survived");

        grid.getColumns().forEach(column -> {
            column.setSortable(true);
            column.setTextAlign(ColumnTextAlign.CENTER);
        });
        
        grid.setItemDetailsRenderer(createTeamDetailsRenderer());
        add(tournamentTitle, grid);
    }

    @Override
    public void setParameter(BeforeEvent event, String tournamentId) {
        // Fetch data based on the parameter
        tournament = FnzsController.getTournament(null, tournamentId);
        if (tournament == null) {
            new NotificationComponent(NotificationVariant.LUMO_ERROR, "Tournament could not be retrieved. Are you sure it's valid?");
            return;
        }
        tournamentTitle.setText(tournament.getName());
        leaderboard = FnzsController.getTournamentLeaderboard(tournament);
        if (leaderboard.isEmpty()) {
            new NotificationComponent(NotificationVariant.LUMO_WARNING, "Leaderboard is empty. Has the tournament started?");
        }

        // Update the grid with the fetched data
        updateView();
    }

    private void updateView() {
        // Set grid items and adjust columns
        grid.setItems(leaderboard);
        grid.getColumns().forEach(column -> column.setAutoWidth(true));

        // First we need to set height for the parent (vertical layout)
        setSizeFull();
        grid.setHeightFull();
    }

    private static ComponentRenderer<TeamDetailsFormLayout, Team> createTeamDetailsRenderer() {
        return new ComponentRenderer<>(TeamDetailsFormLayout::new,
                TeamDetailsFormLayout::setTeam);
    }

    private static class TeamDetailsFormLayout extends FormLayout {
        private final TextField countedKillsField = new TextField("Counted Kills");
        private final TextField countedWinsField = new TextField("Counted Wins");
        private final TextField countedGamesField = new TextField("Counted Games");
        private final TextField placementScoreField = new TextField("Placement Score");
        private final TextField eliminationScoreField = new TextField("Elimination Score");

        public TeamDetailsFormLayout() {
            Stream.of(countedKillsField, countedWinsField, countedGamesField, placementScoreField, eliminationScoreField).forEach(field -> {
                field.setReadOnly(true);
                add(field);
            });

            setResponsiveSteps(new ResponsiveStep("0", 5));
            //  setColspan(emailField, 3);
            //  setColspan(phoneField, 3);
            //  setColspan(streetField, 3);
        }

        public void setTeam(Team team) {
            countedKillsField.setValue(Integer.toString(team.getCountedKills()));
            countedWinsField.setValue(Integer.toString(team.getCountedWins()));
            countedGamesField.setValue(Integer.toString(team.getCountedGames()));
            placementScoreField.setValue(String.format("%d (%.1f%%)", team.getPlacementScore(), (double) team.getPlacementScore() / team.getScore() * 100));
            eliminationScoreField.setValue(String.format("%d (%.1f%%)", team.getEliminationScore(), (double) team.getEliminationScore() / team.getScore() * 100));
        }
    }
}
