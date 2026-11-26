package com.kiefer.arzttermin.ui;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@Route("login")
@PageTitle("Anmelden | Terminfinder Demo")
@AnonymousAllowed
public class LoginView extends Composite<Div> implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public LoginView() {
        Div page = getContent();
        page.addClassName("login-page");

        Div introduction = new Div();
        introduction.addClassName("login-introduction");
        introduction.add(createBrand(),
                new H1("Kurzfristig zum passenden Arzttermin"),
                new Paragraph("Patientinnen und Patienten suchen einmal. Praxen vergeben frei gewordene Termine gezielt und fair."),
                createDemoNotice());

        Div signInCard = new Div();
        signInCard.addClassName("login-card");
        H2 title = new H2("Willkommen zurück");
        Paragraph subtitle = new Paragraph("Melden Sie sich mit einem der Demo-Zugänge an.");
        subtitle.addClassName("muted");

        configureLoginForm();
        signInCard.add(title, subtitle, loginForm, createAccountList());

        page.add(introduction, signInCard);
    }

    private void configureLoginForm() {
        LoginI18n i18n = new LoginI18n();
        LoginI18n.Form form = new LoginI18n.Form();
        form.setTitle("");
        form.setUsername("Benutzername");
        form.setPassword("Passwort");
        form.setSubmit("Sicher anmelden");
        form.setForgotPassword("Passwort vergessen");

        LoginI18n.ErrorMessage error = new LoginI18n.ErrorMessage();
        error.setTitle("Anmeldung fehlgeschlagen");
        error.setMessage("Bitte prüfen Sie Benutzername und Passwort.");

        i18n.setForm(form);
        i18n.setErrorMessage(error);
        loginForm.setI18n(i18n);
        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);
        loginForm.addClassName("login-form");
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

    private Div createDemoNotice() {
        Div notice = new Div();
        notice.addClassName("demo-notice");
        Div label = new Div("LOKALE DEMO");
        label.addClassName("demo-label");
        Paragraph copy = new Paragraph("Alle Personen, Praxen und Termine sind synthetisch. Es werden keine echten Gesundheitsdaten verarbeitet.");
        notice.add(label, copy);
        return notice;
    }

    private Div createAccountList() {
        Div wrapper = new Div();
        wrapper.addClassName("demo-accounts");
        Paragraph password = new Paragraph("Passwort für alle Konten: ");
        password.add(new com.vaadin.flow.component.html.Span("demo"));
        password.addClassName("demo-password");
        wrapper.add(password);

        List.of(
                new DemoAccount("Patientin · automatisch", "patient.anna"),
                new DemoAccount("Patient · persönlich", "patient.ben"),
                new DemoAccount("Patientin · weiterer Fall", "patient.clara"),
                new DemoAccount("Hautarztpraxis", "praxis.haut"),
                new DemoAccount("Portalteam", "portal.mia")
        ).forEach(account -> wrapper.add(createAccountRow(account)));
        return wrapper;
    }

    private Div createAccountRow(DemoAccount account) {
        Div row = new Div();
        row.addClassName("demo-account-row");
        Div description = new Div(account.description());
        description.addClassName("demo-account-description");
        com.vaadin.flow.component.html.Span username = new com.vaadin.flow.component.html.Span(account.username());
        username.addClassName("demo-account-username");
        row.add(description, username);
        row.getElement().setAttribute("role", "button");
        row.getElement().setAttribute("tabindex", "0");
        row.getElement().setAttribute("aria-label", account.username() + " in das Benutzerfeld übernehmen");
        row.addClickListener(event -> fillUsername(account.username()));
        row.getElement().addEventListener("keydown", event -> fillUsername(account.username()))
                .setFilter("event.key === 'Enter' || event.key === ' '");
        return row;
    }

    private void fillUsername(String username) {
        loginForm.getElement().executeJs(
                "const input = this.shadowRoot.querySelector('vaadin-login-form-wrapper')"
                        + ".shadowRoot.querySelector('vaadin-text-field input');"
                        + "input.value = $0; input.dispatchEvent(new Event('input', {bubbles:true, composed:true}));",
                username);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        boolean hasError = event.getLocation().getQueryParameters().getParameters().containsKey("error");
        loginForm.setError(hasError);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            event.forwardTo("");
        }
    }

    private record DemoAccount(String description, String username) {
    }
}
