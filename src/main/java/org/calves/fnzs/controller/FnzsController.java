package org.calves.fnzs.controller;

import controller.AccountsController;
import controller.MongoDbController;
import dto.canonical.Account;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.calves.yunite4j.YuniteApi;
import org.calves.yunite4j.dto.ApiConfig;
import org.calves.yunite4j.dto.MatchSession;
import org.calves.yunite4j.dto.SessionLeaderboard;
import org.calves.yunite4j.dto.Team;
import org.calves.yunite4j.dto.Tournament;
import org.calves.yunite4j.utils.DeserializationUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * @author carlos.pedroalves
 */
public class FnzsController {

    private static final YuniteApi API = new YuniteApi(new ApiConfig(System.getenv("YUNITE_API_KEY")));
    private static final Logger LOGGER = LogManager.getLogger(FnzsController.class);
    private static final String DEFAULT_GUILD_ID = "1213253795333541960";
    private static final HashMap<String, List<SessionLeaderboard>> SESSIONS_LEADERBOARD = new HashMap<>();

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
        enrichTournamentMatches(tournament, API.getTournamentMatches(DEFAULT_GUILD_ID, tournament.getId()), teams);

        // Then we split team members (while merging member data to make sure the totals and averages are still correct)
        List<Team> resultingTeams = splitAndMergeTeams(teams);
        recalculateCountedStats(tournament, resultingTeams);
        LOGGER.debug("Finished splitting and merging leaderboard for individual rankings");

        // Then we sort team members by score and set placements accordingly
        sortAndSetPlacement(tournament.getTiebreakers(), resultingTeams);
        LOGGER.debug("Finished sorting leaderboard");

        return resultingTeams;
    }

    public static int getTeamsInMatch(String sessionId) {
        try {
            return SESSIONS_LEADERBOARD.get(sessionId).size();
        } catch (Exception ex) {
            LOGGER.error("No session leaderboard for match {}", sessionId);
            return -1;
        }
    }

    private static List<Team> splitAndMergeTeams(List<Team> teams) {
        List<Team> individualTeams = new ArrayList<>();
        for (Team team : teams) {
            for (Team.User user : team.getUsers()) {
                Team existingTeam = findTeamByUser(individualTeams, user);
                if (existingTeam != null) {
                    // Sum the relevant fields
                    existingTeam.setKills(existingTeam.getKills() + team.getKills());
                    existingTeam.setGames(existingTeam.getGames() + team.getGames());
                    existingTeam.setWins(existingTeam.getWins() + team.getWins());
                    existingTeam.setSumSecondsSurvived(existingTeam.getSumSecondsSurvived() + team.getSumSecondsSurvived());
                    // Merge gameList and corrections
                    existingTeam.getGameList().addAll(team.getGameList());
                    existingTeam.getCorrections().addAll(team.getCorrections());
                    // Recalculate averages
                    existingTeam.setAveragePlacement(calculateAveragePlacement(existingTeam.getGameList()));
                    existingTeam.setAverageSecondsSurvived(existingTeam.getSumSecondsSurvived() / existingTeam.getGames());
                    existingTeam.setKpm(existingTeam.getKills() / existingTeam.getSumSecondsSurvived() * 60);
                } else {
                    Team newTeam = new Team();
                    newTeam.setUsers(new ArrayList<>(List.of(user)));
                    newTeam.setKills(team.getKills());
                    newTeam.setGames(team.getGames());
                    newTeam.setWins(team.getWins());
                    newTeam.setSumSecondsSurvived(team.getSumSecondsSurvived());
                    newTeam.setGameList(new ArrayList<>(team.getGameList()));
                    newTeam.setCorrections(new ArrayList<>(team.getCorrections()));
                    newTeam.setAveragePlacement(calculateAveragePlacement(team.getGameList()));
                    newTeam.setAverageSecondsSurvived(team.getSumSecondsSurvived() / team.getGames());
                    newTeam.setKpm(team.getKills() / team.getSumSecondsSurvived() * 60);
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

            // We ignore matches that are not fully scored
            copy.stream().filter(x -> !x.getSession().getStatus().equals(MatchSession.Status.SCORED)).forEach(x -> x.setCounts(false));

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

    private static void sortAndSetPlacement(List<Tournament.TieBreaker> tieBreakers, List<Team> teams) {

        Comparator<Team> comparator = Comparator.comparingInt(Team::getScore).reversed(); // Primary sort by score (descending)
        for (Tournament.TieBreaker tieBreaker : tieBreakers) {
            comparator = switch (tieBreaker) {
                case WINS -> comparator.thenComparing(Team::getWins, Comparator.reverseOrder()); // Higher wins first
                case AVERAGE_ELIMINATIONS ->
                        comparator.thenComparing(team -> (double) team.getKills() / team.getGameList().size(), Comparator.reverseOrder()); // Higher average eliminations first
                case SUM_ELIMINATIONS ->
                        comparator.thenComparing(Team::getKills, Comparator.reverseOrder()); // Higher sum eliminations first
                case AVERAGE_PLACEMENT ->
                        comparator.thenComparing(Team::getAveragePlacement); // Lower average placement first
                case SCORE_PER_MATCH ->
                        comparator.thenComparing(team -> (double) team.getScore() / team.getGameList().size(), Comparator.reverseOrder()); // Higher score per match first
                case SUM_TIME_SURVIVED ->
                        comparator.thenComparing(Team::getSumSecondsSurvived, Comparator.reverseOrder()); // Higher sum time survived first
                case AVERAGE_TIME_SURVIVED ->
                        comparator.thenComparing(Team::getAverageSecondsSurvived, Comparator.reverseOrder()); // Higher average time survived first
            };
        }

        // Sort using the dynamic comparator
        teams.sort(comparator);

        // Assign placements
        int currentPlacement = 1;
        for (int i = 0; i < teams.size(); i++) {
            if (i == 0 || comparator.compare(teams.get(i), teams.get(i - 1)) != 0) {
                teams.get(i).setPlacement(currentPlacement);
            } else {
                teams.get(i).setPlacement(teams.get(i - 1).getPlacement());
            }
            currentPlacement++;
        }
    }

    private static double calculateAveragePlacement(List<Team.Game> games) {
        return games.stream().mapToDouble(Team.Game::getPlacement).sum() / games.size();
    }

    private static void enrichTournamentMatches(Tournament tournament, List<MatchSession> matches, List<Team> teams) {
        for (Team team : teams) {
            for (Team.Game game : team.getGameList()) {
                game.setSession(matches.stream().filter(x -> x.getSessionId().equals(game.getSessionId())).findFirst().get());
                enrichMatchScore(tournament.getPointSystem(), game);
                if (!SESSIONS_LEADERBOARD.containsKey(game.getSessionId())) {
                    SESSIONS_LEADERBOARD.put(game.getSessionId(), API.getMatchLeaderboard(DEFAULT_GUILD_ID, tournament.getId(), game.getSessionId()));
                }
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
            LOGGER.error("Calculated score has a difference of {} compared to score from Yunite", game.getScore() - initialScore);
        }
    }
}
