package org.calves.fnzs.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@org.springframework.web.bind.annotation.RestController
public class RestController {

    private static final String ERROR_MESSAGE = "Error, reach out to calves07 on Discord";

    @GetMapping("/")
    public String helloWorld() {
        return "Hello world";
    }

    @GetMapping("/key")
    public int key() {
        return FnzsController.getKeyLength();
    }

    @GetMapping("/{guildId}/leaderboard/{tournamentId}/individual")
    public String getIndividualLeaderboard(@PathVariable String guildId, @PathVariable String tournamentId) {
        FnzsController.getLeaderboard(guildId, tournamentId);
        return "lorem ipsum dolor sit amet";
    }

}