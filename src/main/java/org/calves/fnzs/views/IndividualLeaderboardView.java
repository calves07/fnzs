package org.calves.fnzs.views;

import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import org.calves.fnzs.controller.FnzsController;
import org.calves.yunite4j.dto.Team;
import org.calves.yunite4j.dto.Tournament;
import utils.MathUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author carlos.pedroalves
 */
@Route("leaderboard/individual")
public class IndividualLeaderboardView extends VerticalLayout implements HasUrlParameter<String> {

    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Lisbon");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH'h'mm");

    private Grid<Team> grid = new Grid<>(Team.class, false);
    private H1 tournamentTitle = new H1("Loading tournament data");
    private Tournament tournament;
    private List<Team> leaderboard = new ArrayList<>();

    public IndividualLeaderboardView() {
        grid.addColumn(Team::getPlacement).setHeader("Rank");
        grid.addColumn(team -> team.getUsers().getFirst().getEpicUsername()).setHeader("Player");
        grid.addColumn(Team::getScore).setHeader("Score");
        grid.addColumn(Team::getGames).setHeader("Games");
        grid.addColumn(Team::getKills).setHeader("Eliminations");
        grid.addColumn(Team::getWins).setHeader("Wins");
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
        private final TextField epicIdField = new TextField("Epic ID");
        private final TextField discordIdField = new TextField("Discord ID");
        private final VerticalLayout matches = new VerticalLayout();

        public TeamDetailsFormLayout() {
            Stream.of(countedKillsField, countedWinsField, countedGamesField, placementScoreField, eliminationScoreField, epicIdField, discordIdField).forEach(field -> {
                field.setReadOnly(true);
                add(field);
            });

            setResponsiveSteps(
                    new ResponsiveStep("0", 1),
                    new ResponsiveStep("1200px", 7)
            );
            add(matches);
        }

        public void setTeam(Team team) {
            countedKillsField.setValue(Integer.toString(team.getCountedKills()));
            countedWinsField.setValue(Integer.toString(team.getCountedWins()));
            countedGamesField.setValue(Integer.toString(team.getCountedGames()));
            placementScoreField.setValue(String.format("%d (%.1f%%)", team.getPlacementScore(), (double) team.getPlacementScore() / team.getScore() * 100));
            eliminationScoreField.setValue(String.format("%d (%.1f%%)", team.getEliminationScore(), (double) team.getEliminationScore() / team.getScore() * 100));
            epicIdField.setValue(team.getUsers().getFirst().getEpicId());
            discordIdField.setValue(team.getUsers().getFirst().getDiscordId());

            // Clear existing matches from the layout
            matches.removeAll();

            // Add each match to the layout
            for (Team.Game game : team.getGameList()) {
                HorizontalLayout gameLayout = new HorizontalLayout();

                TextField gameIdField = new TextField("Match ID");
                gameIdField.setValue(game.getSessionId());
                gameIdField.setReadOnly(true);
                gameIdField.setMinWidth(320, Unit.PIXELS);

                TextField timestampField = new TextField("Timestamp");
                timestampField.setValue(game.getTimestamp().atZone(ZONE_ID).format(DATE_TIME_FORMATTER));
                timestampField.setReadOnly(true);
                timestampField.setMaxWidth(165, Unit.PIXELS);

                TextField gameScoreField = new TextField("Score");
                gameScoreField.setValue(Integer.toString(game.getScore()));
                gameScoreField.setReadOnly(true);
                gameScoreField.setMaxWidth(50, Unit.PIXELS);

                TextField countsField = new TextField("Counts?");
                countsField.setValue(game.isCounts() ? "Yes ✅" : "No ❌");
                countsField.setReadOnly(true);
                countsField.setMaxWidth(75, Unit.PIXELS);

                TextField killsField = new TextField("Kills");
                killsField.setValue(Integer.toString(game.getKills()));
                killsField.setReadOnly(true);
                killsField.setMaxWidth(50, Unit.PIXELS);

                TextField placementField = new TextField("Placement");
                placementField.setValue(String.format("%d/%d", game.getPlacement(), FnzsController.getTeamsInMatch(game.getSessionId())));
                placementField.setReadOnly(true);
                placementField.setMaxWidth(83, Unit.PIXELS);

                // Add all fields to the game's horizontal layout
                gameLayout.add(gameIdField, timestampField, gameScoreField, countsField, killsField, placementField);

                // Add the game layout to the matches layout
                matches.add(gameLayout);
            }
        }
    }
}
