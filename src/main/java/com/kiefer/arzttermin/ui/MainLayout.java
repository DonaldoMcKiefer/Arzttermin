package com.kiefer.arzttermin.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;

@PermitAll
public class MainLayout extends AppLayout {

    private final AuthenticationContext authenticationContext;

    public MainLayout(AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
        addClassName("main-layout");
        setPrimarySection(Section.DRAWER);
        addToNavbar(createHeader());
        addToDrawer(createDrawer());
    }

    private Component createHeader() {
        Header header = new Header();
        header.addClassName("app-header");

        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Navigation öffnen");

        Div mobileBrand = createBrand();
        mobileBrand.addClassName("mobile-brand");

        Div spacer = new Div();
        spacer.addClassName("header-spacer");

        Authentication authentication = currentAuthentication();
        Span role = new Span(roleLabel(authentication.getAuthorities()));
        role.addClassName("current-role");
        Span username = new Span(displayName(authentication.getName()));
        username.addClassName("current-user");

        Button logout = new Button("Abmelden", VaadinIcon.SIGN_OUT.create());
        logout.addClassName("logout-button");
        logout.addClickListener(event -> authenticationContext.logout());

        Div user = new Div(role, username);
        user.addClassName("user-summary");
        header.add(toggle, mobileBrand, spacer, user, logout);
        return header;
    }

    private Component createDrawer() {
        Div drawer = new Div();
        drawer.addClassName("app-drawer");

        Div brand = createBrand();
        brand.addClassName("drawer-brand");

        Div demo = new Div("LOKALE DEMO");
        demo.addClassName("drawer-demo-label");

        Nav navigation = new Nav();
        navigation.setAriaLabel("Hauptnavigation");
        navigation.addClassName("main-navigation");
        navigation.add(navLink("Übersicht", "", VaadinIcon.HOME));

        Collection<? extends GrantedAuthority> authorities = currentAuthentication().getAuthorities();
        if (hasRole(authorities, "PATIENT")) {
            navigation.add(navLink("Meine Terminsuche", "patient", VaadinIcon.SEARCH));
        }
        if (hasRole(authorities, "PRACTICE")) {
            navigation.add(navLink("Praxis-Termine", "practice", VaadinIcon.CALENDAR));
        }
        if (hasRole(authorities, "STAFF")) {
            navigation.add(navLink("Persönliche Suche", "staff", VaadinIcon.USERS));
        }

        Div privacy = new Div("Pilotbetrieb · Nur synthetische Daten");
        privacy.addClassName("drawer-footnote");
        drawer.add(brand, demo, navigation, privacy);
        return drawer;
    }

    private Anchor navLink(String label, String href, VaadinIcon iconType) {
        Anchor link = new Anchor(href, label);
        link.addClassName("nav-link");
        Icon icon = iconType.create();
        icon.setSize("1.15rem");
        link.addComponentAsFirst(icon);
        return link;
    }

    private Div createBrand() {
        Div brand = new Div();
        brand.addClassName("brand-lockup");
        Div mark = new Div("+");
        mark.addClassName("brand-mark");
        Div text = new Div("Terminfinder");
        text.addClassName("brand-name");
        brand.add(mark, text);
        return brand;
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean hasRole(Collection<? extends GrantedAuthority> authorities, String role) {
        return authorities.stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    private String roleLabel(Collection<? extends GrantedAuthority> authorities) {
        if (hasRole(authorities, "PRACTICE")) {
            return "Praxis";
        }
        if (hasRole(authorities, "STAFF")) {
            return "Portalteam";
        }
        return "Patientenzugang";
    }

    private String displayName(String username) {
        return switch (username) {
            case "patient.anna" -> "Anna Demo";
            case "patient.ben" -> "Ben Demo";
            case "patient.clara" -> "Clara Demo";
            case "praxis.haut" -> "Hautarztpraxis Demo";
            case "portal.mia" -> "Mia vom Portalteam";
            default -> username;
        };
    }
}
