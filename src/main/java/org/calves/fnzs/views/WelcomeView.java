package org.calves.fnzs.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import org.calves.fnzs.controller.FnzsController;
import org.calves.yunite4j.dto.Tournament;

import java.util.List;

/**
 * @author carlos.pedroalves
 */
@Route("")
public class WelcomeView extends VerticalLayout {

    public WelcomeView() {

        H1 welcomeMessage = new H1("Welcome to My Application!");
        add(welcomeMessage);

        List<Tournament> tournaments = FnzsController.getTournaments();

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
        add(welcomeMessage, tournamentLayout);
    }
}
