package org.calves.fnzs.views;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.Lumo;
import org.calves.fnzs.controller.FnzsController;
import org.calves.yunite4j.dto.Tournament;

import java.util.List;

/**
 * @author carlos.pedroalves
 */
@Route("")
public class WelcomeView extends VerticalLayout {

    public WelcomeView() {

        getElement().executeJs("document.documentElement.setAttribute('theme', $0)", Lumo.DARK);

        H1 welcomeMessage = new H1("Tournaments by z3rgtv!");

        String twitchEmbedCode = "<iframe "
                + "src=\"https://player.twitch.tv/?channel=z3rgtv&parent=zergttv.pt&parent=www.zergttv.pt&parent=localhost&parent=fnzs-d3ddd7666fef.herokuapp.com\" "
                + "height=\"400\" "
                + "layout=\"video-with-chat\" "
                + "width=\"95%\" "
                + "muted=\"true\" "
                + "</iframe>";
        Html twitchEmbed = new Html(twitchEmbedCode);

        List<Tournament> tournaments = FnzsController.getTournaments();
        tournaments.sort((t1, t2) -> t2.getStartDate().compareTo(t1.getStartDate()));

        // Create a layout to display tournament links
        VerticalLayout tournamentLayout = new VerticalLayout();
        for (Tournament tournament : tournaments) {
            // Create a link for each tournament
            RouterLink tournamentLink = new RouterLink(
                    tournament.getName(),
                    IndividualLeaderboardView.class,
                    tournament.getId()
            );
            tournamentLayout.add(tournamentLink);
        }

        // Add components to the layout
        add(welcomeMessage, twitchEmbed, tournamentLayout);
    }
}
