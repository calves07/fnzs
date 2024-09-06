package org.calves.fnzs.controller;

import controller.AccountsController;
import controller.MongoDbController;
import dto.canonical.Account;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.calves.yunite4j.YuniteApi;
import org.calves.yunite4j.dto.ApiConfig;
import org.calves.yunite4j.dto.MatchSession;
import org.calves.yunite4j.dto.Team;
import org.calves.yunite4j.dto.Tournament;
import org.calves.yunite4j.utils.DeserializationUtils;

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

        // First we enrich data with Epic usernames
        LOGGER.info("Enriching data Epic usernames");
        for (Team team : teams) {
            for (Team.User user : team.getUsers()) {
                Account account = Account.builder().username(String.format("Unknown(%s)", user.getEpicId())).build();
                try {
                    account = MongoDbController.getAccount(user.getEpicId());
                } catch (Exception ex) {
                    System.out.println();
                }
                if (account == null) {
                    account = AccountsController.insertAccountWithId(user.getEpicId(), null, null);
                }
                user.setEpicUsername(account.getUsername());
            }
        }

        // Then we enrich games data
        enrichTournamentMatches(tournament.getPointSystem(), API.getTournamentMatches(DEFAULT_GUILD_ID, tournament.getId()), teams);

        // Then we split team members (while merging member data to make sure the totals and averages are still correct)
        List<Team> resultingTeams = splitAndMergeTeams(teams);
        recalculateCountedStats(tournament, resultingTeams);
        LOGGER.debug("Finished splitting and merging leaderboard for individual rankings");

        // Then we sort team members by score and set placements accordingly
        sortAndSetPlacement(resultingTeams);
        LOGGER.debug("Finished sorting leaderboard");

        return resultingTeams;
    }

    // todo: leaderboard está incompleto!!!!
    public static List<Team> splitAndMergeTeams(List<Team> teams) {
        List<Team> individualTeams = new ArrayList<>();
        for (Team team : teams) {
            for (Team.User user : team.getUsers()) {
                Team existingTeam = findTeamByUser(individualTeams, user);

                if (existingTeam != null) {
                    // Sum the relevant fields
                    existingTeam.setKills(existingTeam.getKills() + team.getKills());
                    // existingTeam.setCountedKills(existingTeam.getCountedKills() + team.getCountedKills());
                    existingTeam.setGames(existingTeam.getGames() + team.getGames());
                    // existingTeam.setCountedGames(existingTeam.getCountedGames() + team.getCountedGames());
                    existingTeam.setWins(existingTeam.getWins() + team.getWins());
                    // existingTeam.setCountedWins(existingTeam.getCountedWins() + team.getCountedWins());
                    // existingTeam.setPlacementScore(existingTeam.getPlacementScore() + team.getPlacementScore());
                    // existingTeam.setEliminationScore(existingTeam.getEliminationScore() + team.getEliminationScore());
                    // existingTeam.setScore(existingTeam.getScore() + team.getScore());
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
                    // newTeam.setCountedKills(team.getCountedKills());
                    newTeam.setGames(team.getGames());
                    // newTeam.setCountedGames(team.getCountedGames());
                    newTeam.setWins(team.getWins());
                    // newTeam.setCountedWins(team.getCountedWins());
                    // newTeam.setPlacementScore(team.getPlacementScore());
                    // newTeam.setEliminationScore(team.getEliminationScore());
                    newTeam.setScore(team.getScore());
                    newTeam.setSumSecondsSurvived(team.getSumSecondsSurvived());
                    // newTeam.setAveragePlacement(calculateAveragePlacement(team.getGameList()));
                    // newTeam.setAverageSecondsSurvived(team.getSumSecondsSurvived() / team.getGames());
                    // newTeam.setKpm(team.getKills() / team.getSumSecondsSurvived());
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
            if (team.getUsers().stream().anyMatch(x -> x.getEpicId().equals(user.getEpicId()))) {
                return team;
            }
        }
        return null;
    }

    // todo: check match.status? SCORED/PARTIALLY_SCORED/NOT_SCORED
    private static void recalculateCountedStats(Tournament tournament, List<Team> teams) {

        int maxGamesScored = tournament.getMaxGamesScored();
        if (maxGamesScored == 0) {
            // 0 means there is no limit
            maxGamesScored = Integer.MAX_VALUE;
        }
        int minPlayersInGame = tournament.getConsensusMin();

        for (Team team : teams) {

            List<Team.Game> copy = team.getGameList().stream().map(DeserializationUtils::createDeepCopy).toList();

            // We start by assuming every match counts (to reset whatever came from Yunite API)
            copy.forEach(x -> x.setCounts(true));

            // Games with fewer players that expected do not count
            copy.stream().filter(x -> x.getSession().getPlayers() < minPlayersInGame).forEach(x -> x.setCounts(false));

            // Calculate how many games we still need to ignore
            int gamesToRemove = (int) (copy.stream().filter(Team.Game::isCounts).count() - maxGamesScored);
            if (gamesToRemove > 0) {
                copy.stream().sorted(Comparator.comparing(Team.Game::getScore)).limit(gamesToRemove).forEach(dto -> dto.setCounts(false));
            }

            team.setCountedKills(copy.stream().filter(Team.Game::isCounts).mapToInt(Team.Game::getKills).sum());
            team.setCountedGames((int) copy.stream().filter(Team.Game::isCounts).count());
            team.setCountedWins((int) copy.stream().filter(x -> x.isCounts() && x.getPlacement() == 1).count());
            team.setScore(copy.stream().filter(Team.Game::isCounts).mapToInt(Team.Game::getScore).sum());
            team.setPlacementScore(copy.stream().filter(Team.Game::isCounts).mapToInt(Team.Game::getPlacementScore).sum());
            team.setEliminationScore(copy.stream().filter(Team.Game::isCounts).mapToInt(Team.Game::getEliminationScore).sum());

            team.setGameList(copy);
        }
    }

    // todo: apply tie breakers. is it worth tho? easy to do fixed order but if we are going to make this dynamic to respect yunite configuration, it adds complexity
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
        return games.stream().mapToDouble(Team.Game::getPlacement).sum() / games.size();
    }

    private static void enrichTournamentMatches(Tournament.PointSystem pointSystem, List<MatchSession> matches, List<Team> teams) {
        for (Team team : teams) {
            for (Team.Game game : team.getGameList()) {
                game.setSession(matches.stream().filter(x -> x.getSessionId().equals(game.getSessionId())).findFirst().get());
                enrichMatchScore(pointSystem, game);
            }
        }
    }

    private static void enrichMatchScore(Tournament.PointSystem pointSystem, Team.Game game) {
        if (pointSystem.getKillCap() == 0) {
            // 0 means no limit
            pointSystem.setKillCap(Integer.MAX_VALUE);
        }
        int initialScore = game.getScore();
        int validKills = Math.min(game.getKills(), pointSystem.getKillCap());
        int eliminationScore = validKills * pointSystem.getPointsPerKill();
        int placementScore = pointSystem.getCompletePointsPerPlacement().get(game.getPlacement());
        game.setEliminationScore(eliminationScore);
        game.setPlacementScore(placementScore);
        game.setScore(eliminationScore + placementScore);
        if (initialScore != game.getScore()) {
            System.out.println("DIFFERENT SCORE!!!!");
        }
    }
}
