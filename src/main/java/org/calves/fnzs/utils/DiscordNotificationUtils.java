package org.calves.fnzs.utils;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.calves.yunite4j.dto.Team;
import org.calves.yunite4j.dto.Tournament;
import utils.MathUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author carlos.pedroalves
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DiscordNotificationUtils {

    private static final Logger LOGGER = LogManager.getLogger(DiscordNotificationUtils.class);
    @Getter
    private static final WebhookClient CLIENT = setupClient(System.getenv("DISCORD_WEBHOOK"));
    private static final String THUMBNAIL_URL = "https://cdn.discordapp.com/icons/1213253795333541960/37779228e58038571549819a2c6aa362.png";
    private static final Integer EMBED_COLOR = 0xFFFFFF;

    public static void logRanking(Tournament tournament, List<Team> teams) {
        StringBuilder sb = new StringBuilder();
        WebhookEmbed.EmbedFooter footer = new WebhookEmbed.EmbedFooter("Developed by @calves07", "https://cdn.discordapp.com/icons/1213253795333541960/37779228e58038571549819a2c6aa362.png");
        footer = null;
       // int count = 0;
       sb.append(String.format("```%-3s %-16s %-5s %-5s %-5s %-5s```", "#", "Username", "Kills", "Wins", "AvgP", "Score"));
        for (Team team : teams) {
            //if(count < 20){
                sb.append(String.format("```%-3s %-16s %-5s %-5s %-5s %-5s```", team.getPlacement(), team.getUsers().getFirst().getEpicUsername(), team.getKills(), team.getWins(), MathUtils.roundToDecimalPlaces(team.getAveragePlacement(), 1), team.getScore()));
         //   }
          //  count++;
            // sb.append(String.format("\n%d.  %s, %d points", team.getPlacement(), team.getUsers().getFirst().getEpicUsername(), team.getScore()));
        }
        WebhookEmbed embed = new WebhookEmbed(OffsetDateTime.now(), EMBED_COLOR, sb.toString(), THUMBNAIL_URL,
                null, footer, new WebhookEmbed.EmbedTitle(tournament.getName(),
                String.format("https://yunite.xyz/leaderboard/%s", tournament.getId())), null, new ArrayList<>());

        CLIENT.send(embed);
    }

    private static WebhookClient setupClient(String webhookURL) {
        WebhookClientBuilder builder = new WebhookClientBuilder(webhookURL);
        builder.setThreadFactory(job -> {
            Thread thread = new Thread(job);
            thread.setName("Discord Notifications Thread");
            thread.setDaemon(true);
            return thread;
        });
        builder.setWait(true);
        return builder.build();
    }

}
