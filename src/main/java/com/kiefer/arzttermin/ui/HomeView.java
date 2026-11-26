package com.kiefer.arzttermin.ui;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Übersicht | Terminfinder Demo")
@PermitAll
public class HomeView extends Composite<Div> {

    public HomeView() {
        Div page = getContent();
        page.addClassNames("content-page", "home-view");

        Div eyebrow = new Div("TERMINFINDER · LOKALER PILOT");
        eyebrow.addClassName("eyebrow");
        H1 title = new H1("Guten Tag!");
        Paragraph intro = new Paragraph("Hier beginnt Ihr Weg zum passenden, kurzfristig freien Termin.");
        intro.addClassName("page-lead");

        Div hero = new Div(eyebrow, title, intro, createRoleCard());
        hero.addClassName("home-hero");

        Div info = new Div();
        info.addClassName("pilot-info-card");
        H2 infoTitle = new H2("Sicher testen");
        Paragraph infoCopy = new Paragraph("Diese Anwendung läuft als lokale Demo. Alle Namen, Anfragen, Praxen und Zahlungen sind vollständig synthetisch.");
        info.add(VaadinIcon.SHIELD.create(), new Div(infoTitle, infoCopy));

        page.add(hero, info);
    }

    private Div createRoleCard() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean practice = hasRole(authentication, "PRACTICE");
        boolean staff = hasRole(authentication, "STAFF");

        String heading;
        String text;
        String buttonLabel;
        String target;
        if (practice) {
            heading = "Freie Termine passend vergeben";
            text = "Melden Sie einen frei gewordenen Termin und wählen Sie aus passenden offenen Anfragen aus.";
            buttonLabel = "Zur Praxisansicht";
            target = "practice";
        } else if (staff) {
            heading = "Persönliche Suchen begleiten";
            text = "Dokumentieren Sie Praxiskontakte und Rückmeldungen für persönliche Suchaufträge.";
            buttonLabel = "Zur Arbeitsliste";
            target = "staff";
        } else {
            heading = "Einmal suchen, gezielt angeboten bekommen";
            text = "Erstellen Sie Ihre Anfrage und entscheiden Sie selbst, ob Sie ein passendes Praxisangebot annehmen.";
            buttonLabel = "Meine Terminsuche öffnen";
            target = "patient";
        }

        Div card = new Div();
        card.addClassName("role-entry-card");
        H2 title = new H2(heading);
        Paragraph copy = new Paragraph(text);
        Button button = new Button(buttonLabel);
        button.addClassName("primary-action");
        button.addClickListener(event -> UI.getCurrent().navigate(target));
        card.add(title, copy, button);
        return card;
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}
