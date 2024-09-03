package org.calves.fnzs.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@org.springframework.web.bind.annotation.RestController
public class RestController {

    private static final String ERROR_MESSAGE = "Error, reach out to calves07 on Discord";
    private static final Logger LOGGER = LogManager.getLogger(RestController.class);

    //   @GetMapping("/")
    //   public String helloWorld() {
    //   return "Hello world";
    //   }

    /*

    @GetMapping("/key")
    public int key() {
        return FnzsController.getKeyLength();
    }

    @GetMapping("/{guildId}/leaderboard/{tournamentId}/individual")
    public String getIndividualLeaderboard(@PathVariable String guildId, @PathVariable String tournamentId) {
        LOGGER.debug("Received request to get individual leaderboard for guild {} and tournament {}", guildId, tournamentId);
        // FnzsController.getTournamentLeaderboard(guildId, tournamentId);
        return "lorem ipsum dolor sit amet";
    }

     */

}