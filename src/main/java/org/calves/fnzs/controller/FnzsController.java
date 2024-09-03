package org.calves.fnzs.controller;

import controller.AccountsController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.calves.yunite4j.YuniteApi;
import org.calves.yunite4j.dto.ApiConfig;
import org.calves.yunite4j.dto.Team;
import org.calves.yunite4j.dto.Tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author carlos.pedroalves
 */
public class FnzsController {

    private static final YuniteApi API = new YuniteApi(new ApiConfig(System.getenv("YUNITE_API_KEY")));
    private static final Logger LOGGER = LogManager.getLogger(FnzsController.class);
    private static final String DEFAULT_GUILD_ID = "1213253795333541960";

    public static List<Tournament> getTournaments() {
        LOGGER.info("Retrieving tournaments");
        return API.getTournaments(DEFAULT_GUILD_ID);
    }

    public static Tournament getTournament(String guildId, String tournamentId) {
        LOGGER.info("Retrieving tournament {} from guild {}", tournamentId, DEFAULT_GUILD_ID);
        try {
            return API.getTournament(DEFAULT_GUILD_ID, tournamentId);
        } catch (Exception ex) {
            return null;
        }
    }

    public static List<Team> getTournamentLeaderboard(Tournament tournament) {

        LOGGER.info("Retrieving leaderboard from tournament {}", tournament.getId());
        List<Team> teams = API.getTournamentLeaderboard(DEFAULT_GUILD_ID, tournament.getId());
        LOGGER.info("Retrieved a total of {} teams", teams.size());

        // todo: fix missing names
        // First we enrich data with Epic usernames

        LOGGER.info("Enriching data Epic usernames");
        for (Team team : teams) {
            for (Team.User user : team.getUsers()) {
                if (user.getEpicUsername() == null) {
                    LOGGER.debug("Trying to enrich username for account {}", user.getEpicId());
                    try {
                        user.setEpicUsername(AccountsController.getUsernameFromAccountId(user.getEpicId()));
                        if (user.getEpicUsername() == null || user.getEpicUsername().isBlank()) {
                            LOGGER.warn("Username for {} is NULL or BLANK: ", user.getEpicId(), user.getEpicUsername());
                        }
                    } catch (Exception ex) {
                        LOGGER.warn("Failed to update username for {}", user.getEpicId(), ex);
                    }
                }
            }
        }

        // Then we split team members (while merging member data to make sure the totals and averages are still correct)
        List<Team> resultingTeams = splitAndMergeTeams(teams);
        LOGGER.debug("Finished splitting and merging leaderboard for individual rankings");

        // Then we sort team members by score and set placements accordingly
        sortAndSetPlacement(resultingTeams);
        LOGGER.debug("Finished sorting leaderboard");

        return resultingTeams;

        // DiscordNotificationUtils.logRanking(tournament, resultingTeams);
    }

    public static List<Team> splitAndMergeTeams(List<Team> teams) {
        List<Team> individualTeams = new ArrayList<>();

        for (Team team : teams) {
            for (Team.User user : team.getUsers()) {
                Team existingTeam = findTeamByUser(individualTeams, user);

                // todo: update counted kills, wins, etc
                if (existingTeam != null) {
                    // Sum the relevant fields
                    existingTeam.setKills(existingTeam.getKills() + team.getKills());
                    existingTeam.setCountedKills(existingTeam.getCountedKills() + team.getCountedKills());
                    existingTeam.setGames(existingTeam.getGames() + team.getGames());
                    existingTeam.setCountedGames(existingTeam.getCountedGames() + team.getCountedGames());
                    existingTeam.setWins(existingTeam.getWins() + team.getWins());
                    existingTeam.setCountedWins(existingTeam.getCountedWins() + team.getCountedWins());
                    existingTeam.setPlacementScore(existingTeam.getPlacementScore() + team.getPlacementScore());
                    existingTeam.setEliminationScore(existingTeam.getEliminationScore() + team.getEliminationScore());
                    existingTeam.setScore(existingTeam.getScore() + team.getScore());
                    existingTeam.setSumSecondsSurvived(existingTeam.getSumSecondsSurvived() + team.getSumSecondsSurvived());
                    // Recalculate averages
                    existingTeam.setAveragePlacement(calculateAveragePlacement(existingTeam.getGameList()));
                    existingTeam.setAverageSecondsSurvived(existingTeam.getSumSecondsSurvived() / existingTeam.getGames());
                    existingTeam.setKpm(existingTeam.getKills() / existingTeam.getSumSecondsSurvived()); // Kills per minute
                    // Merge gameList and corrections
                    existingTeam.getGameList().addAll(team.getGameList());
                    existingTeam.getCorrections().addAll(team.getCorrections());
                } else {
                    // Create a new team with the individual user
                    Team newTeam = new Team();
                    //newTeam.setTeamId(team.getTeamId());
                    newTeam.setUsers(new ArrayList<>(List.of(user)));
                    newTeam.setKills(team.getKills());
                    newTeam.setCountedKills(team.getCountedKills());
                    newTeam.setGames(team.getGames());
                    newTeam.setCountedGames(team.getCountedGames());
                    newTeam.setWins(team.getWins());
                    newTeam.setCountedWins(team.getCountedWins());
                    newTeam.setPlacementScore(team.getPlacementScore());
                    newTeam.setEliminationScore(team.getEliminationScore());
                    newTeam.setScore(team.getScore());
                    newTeam.setSumSecondsSurvived(team.getSumSecondsSurvived());
                    newTeam.setAveragePlacement(calculateAveragePlacement(team.getGameList()));
                    newTeam.setAverageSecondsSurvived(team.getSumSecondsSurvived() / team.getGames());
                    newTeam.setKpm(team.getKills() / team.getSumSecondsSurvived());
                    newTeam.setGameList(new ArrayList<>(team.getGameList()));  // Copy gameList
                    newTeam.setCorrections(new ArrayList<>(team.getCorrections()));  // Copy corrections
                    individualTeams.add(newTeam);
                }
            }
        }

        return individualTeams;
    }

    private static Team findTeamByUser(List<Team> teams, Team.User user) {
        for (Team team : teams) {
            if (team.getUsers().contains(user)) {
                return team;
            }
        }
        return null;
    }


    // todo: apply tie breakers
    private static void sortAndSetPlacement(List<Team> teams) {
        // Sort teams by score in descending order
        teams.sort(Comparator.comparingInt(Team::getScore).reversed());

        int currentPlacement = 1;

        for (int i = 0; i < teams.size(); i++) {
            // For the first team, or if the current team's score is different from the previous team
            if (i == 0 || teams.get(i).getScore() != teams.get(i - 1).getScore()) {
                teams.get(i).setPlacement(currentPlacement);
            } else {
                // If the current team has the same score as the previous team, give them the same placement
                teams.get(i).setPlacement(teams.get(i - 1).getPlacement());
            }
            // Increment the placement counter only when moving to the next unique rank
            currentPlacement++;
        }
    }

    private static double calculateAveragePlacement(List<Team.Game> games) {
        // todo: take all games into consideration or only the ones that count?
        return games.stream().mapToDouble(Team.Game::getPlacement).sum() / games.size();
    }
}
