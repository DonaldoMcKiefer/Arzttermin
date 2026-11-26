package com.kiefer.arzttermin.ui;

import com.kiefer.arzttermin.domain.AppointmentType;
import com.kiefer.arzttermin.domain.AvailabilityWindow;
import com.kiefer.arzttermin.domain.BillingType;
import com.kiefer.arzttermin.domain.BookingStatus;
import com.kiefer.arzttermin.domain.FeeStatus;
import com.kiefer.arzttermin.domain.OfferStatus;
import com.kiefer.arzttermin.domain.SearchRequestStatus;
import com.kiefer.arzttermin.domain.ServiceTier;
import com.kiefer.arzttermin.domain.Specialty;
import com.kiefer.arzttermin.service.PortalException;
import com.kiefer.arzttermin.service.PortalWorkflowService;
import com.kiefer.arzttermin.service.dto.PortalViews;
import com.kiefer.arzttermin.service.dto.SearchRequestCommand;
import com.kiefer.arzttermin.ui.component.StatusBadge;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Route(value = "patient", layout = MainLayout.class)
@PageTitle("Meine Terminsuche | Terminfinder Demo")
@RolesAllowed("PATIENT")
@CssImport("./themes/terminfinder/views/patient-view.css")
public class PatientView extends Composite<Div> {

    private static final Locale GERMAN = Locale.GERMANY;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("EEEE, d. MMMM yyyy 'um' HH:mm 'Uhr'", GERMAN);
    private static final DateTimeFormatter SHORT_DATE_TIME = DateTimeFormatter
            .ofPattern("dd.MM.yyyy, HH:mm 'Uhr'", GERMAN);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", GERMAN);

    private final PortalWorkflowService workflow;

    private final ComboBox<Specialty> specialty = new ComboBox<>("Fachrichtung");
    private final ComboBox<AppointmentType> appointmentType = new ComboBox<>("Terminart");
    private final TextField city = new TextField("Ort (oder PLZ)");
    private final TextField postalCode = new TextField("Postleitzahl (oder Ort)");
    private final IntegerField radius = new IntegerField("Suchradius");
    private final Checkbox shortNotice = new Checkbox("Ich kann auch sehr kurzfristig erscheinen");
    private final Checkbox newPatient = new Checkbox("Ich bin Neupatientin oder Neupatient in der Praxis");
    private final RadioButtonGroup<BillingType> billingType = new RadioButtonGroup<>();
    private final RadioButtonGroup<ServiceTier> serviceTier = new RadioButtonGroup<>();
    private final DatePicker neededBy = new DatePicker("Termin benötigt bis (optional)");
    private final Div windowsContainer = new Div();
    private final List<WindowRow> windowRows = new ArrayList<>();
    private final Button addWindowButton = new Button("Weiteres Zeitfenster", VaadinIcon.PLUS.create());
    private final Button createRequestButton = new Button("Suche verbindlich starten", VaadinIcon.SEARCH.create());
    private final Div costPreview = new Div();

    private final Div requestsContent = new Div();
    private final Div offersContent = new Div();
    private final Div bookingsContent = new Div();
    private final Span requestCount = new Span();
    private final Span offerCount = new Span();
    private final Span bookingCount = new Span();

    public PatientView(PortalWorkflowService workflow) {
        this.workflow = workflow;
        Div page = getContent();
        page.addClassNames("content-page", "patient-view");
        page.add(createPageHeader(), createSearchForm(), createResultsArea());
        configureFields();
        addWindowRow(false);
        updateCostPreview();
        refreshData();
    }

    private Component createPageHeader() {
        Div copy = new Div();
        Div eyebrow = new Div("PATIENTENBEREICH · LOKALE DEMO");
        eyebrow.addClassName("eyebrow");
        H1 title = new H1("Meine Terminsuche");
        Paragraph intro = new Paragraph("Einmal anfragen und ein passendes Angebot direkt von einer Praxis erhalten.");
        intro.addClassName("page-lead");
        copy.add(eyebrow, title, intro);

        Button reload = new Button("Aktualisieren", VaadinIcon.REFRESH.create());
        reload.addClassName("refresh-button");
        reload.addClickListener(event -> refreshData());

        Div header = new Div(copy, reload);
        header.addClassName("patient-page-header");
        return header;
    }

    private Component createSearchForm() {
        Div card = new Div();
        card.addClassName("patient-search-card");

        Div heading = new Div();
        heading.addClassName("section-heading");
        Div icon = iconTile(VaadinIcon.SEARCH);
        Div text = new Div();
        H2 title = new H2("Neue Suchanfrage");
        Paragraph subtitle = new Paragraph("Ihre Anfrage bleibt aktiv, bis Sie ein passendes Praxisangebot buchen.");
        text.add(title, subtitle);
        heading.add(icon, text);

        Div basics = new Div(specialty, appointmentType, city, postalCode, radius);
        basics.addClassNames("patient-form-grid", "patient-basics-grid");

        Div availability = formSection(
                "Wann können Sie?",
                "Mindestens ein Zeitfenster ist erforderlich. Der angebotene Termin muss vollständig darin liegen.",
                windowsContainer, addWindowButton);

        Div flexibility = new Div(shortNotice, newPatient);
        flexibility.addClassName("patient-checkboxes");
        availability.add(flexibility);

        Div billing = formSection(
                "Wie soll die Behandlung abgerechnet werden?",
                "Diese Auswahl gilt nur für diese Suchanfrage – unabhängig von Ihrem Versicherungsstatus.",
                billingType);
        billing.addClassName("required-section");

        Div tariffFields = new Div(serviceTier, neededBy);
        tariffFields.addClassName("tariff-fields");
        Div tariff = formSection(
                "Welchen Suchservice wünschen Sie?",
                "Beide Portalgebühren entstehen ausschließlich bei erfolgreicher Buchung. Der Tarif ändert weder die Reihenfolge noch die medizinische Priorität.",
                tariffFields);

        costPreview.addClassName("cost-preview");
        Div actions = new Div(costPreview, createRequestButton);
        actions.addClassName("search-form-actions");

        card.add(heading, basics, availability, billing, tariff, actions);
        return card;
    }

    private Component createResultsArea() {
        requestsContent.addClassName("patient-card-list");
        offersContent.addClassName("patient-card-list");
        bookingsContent.addClassName("patient-card-list");

        Div requests = resultSection(
                "Meine Anfragen", "Alle laufenden und abgeschlossenen Suchen.",
                VaadinIcon.CLIPBOARD_TEXT, requestCount, requestsContent);
        Div offers = resultSection(
                "Praxisangebote", "Offene Angebote können Sie innerhalb der angezeigten Frist annehmen oder ablehnen.",
                VaadinIcon.CALENDAR_CLOCK, offerCount, offersContent);
        Div bookings = resultSection(
                "Buchungen & Gebühren", "Erst mit Ihrer Annahme wird die Buchung bestätigt und die Portalgebühr fällig.",
                VaadinIcon.CHECK_CIRCLE, bookingCount, bookingsContent);

        Div results = new Div(offers, bookings, requests);
        results.addClassName("patient-results");
        return results;
    }

    private Div resultSection(String title, String subtitle, VaadinIcon iconType, Span count, Div content) {
        Div section = new Div();
        section.addClassName("patient-result-section");

        Div heading = new Div();
        heading.addClassName("result-heading");
        Div titleWrap = new Div();
        H2 h2 = new H2(title);
        Paragraph paragraph = new Paragraph(subtitle);
        titleWrap.add(h2, paragraph);
        count.addClassName("result-count");
        heading.add(iconTile(iconType), titleWrap, count);

        section.add(heading, content);
        return section;
    }

    private void configureFields() {
        specialty.setItems(Specialty.values());
        specialty.setItemLabelGenerator(Specialty::getLabel);
        specialty.setRequiredIndicatorVisible(true);
        specialty.setPlaceholder("Bitte auswählen");

        appointmentType.setItems(AppointmentType.values());
        appointmentType.setItemLabelGenerator(AppointmentType::getLabel);
        appointmentType.setRequiredIndicatorVisible(true);
        appointmentType.setPlaceholder("Bitte auswählen");

        city.setPlaceholder("z. B. Berlin");
        city.setValue("Berlin");
        city.setMaxLength(80);
        city.setClearButtonVisible(true);
        city.setHelperText("Lokaler Pilot mit synthetischen Berliner Ortsdaten");

        postalCode.setPlaceholder("z. B. 10115");
        postalCode.setValue("10115");
        postalCode.setAllowedCharPattern("[0-9]");
        postalCode.setPattern("[0-9]{5}");
        postalCode.setMinLength(5);
        postalCode.setMaxLength(5);
        postalCode.setClearButtonVisible(true);
        postalCode.setHelperText("Exakt hinterlegt: 10115, 10117, 10119 und 10999");

        radius.setRequiredIndicatorVisible(true);
        radius.setMin(1);
        radius.setMax(100);
        radius.setStep(5);
        radius.setStepButtonsVisible(true);
        radius.setValue(20);
        radius.setSuffixComponent(new Span("km"));

        shortNotice.setHelperText("Damit passen auch Termine in weniger als 48 Stunden.");
        newPatient.setValue(true);

        billingType.setLabel("Abrechnungsart");
        billingType.setItems(BillingType.values());
        billingType.setItemLabelGenerator(BillingType::getLabel);
        billingType.setRequiredIndicatorVisible(true);
        billingType.addValueChangeListener(event -> updateCostPreview());

        serviceTier.setLabel("Portalservice");
        serviceTier.setItems(ServiceTier.values());
        serviceTier.setItemLabelGenerator(tier -> switch (tier) {
            case AUTOMATIC_5 -> "Automatische Suche · 5 € bei Buchung";
            case PERSONAL_25 -> "Persönliche Suche · 25 € bei Buchung";
        });
        serviceTier.setRequiredIndicatorVisible(true);
        serviceTier.setValue(ServiceTier.AUTOMATIC_5);
        serviceTier.addValueChangeListener(event -> {
            boolean personal = event.getValue() == ServiceTier.PERSONAL_25;
            neededBy.setVisible(personal);
            if (!personal) {
                neededBy.clear();
            }
            updateCostPreview();
        });

        neededBy.setLocale(GERMAN);
        neededBy.setMin(LocalDate.now());
        neededBy.setClearButtonVisible(true);
        neededBy.setVisible(false);
        neededBy.setHelperText("Das Portalteam berücksichtigt dieses Wunschdatum bei der Kontaktaufnahme.");

        windowsContainer.addClassName("availability-windows");
        addWindowButton.addClassName("add-window-button");
        addWindowButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        addWindowButton.addClickListener(event -> addWindowRow(true));

        createRequestButton.addClassName("create-request-button");
        createRequestButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createRequestButton.addClickListener(event -> createRequest());
    }

    private void addWindowRow(boolean removable) {
        if (windowRows.size() >= 3) {
            return;
        }
        WindowRow row = new WindowRow(windowRows.size() + 1, removable);
        windowRows.add(row);
        windowsContainer.add(row);
        addWindowButton.setVisible(windowRows.size() < 3);
    }

    private void removeWindowRow(WindowRow row) {
        windowRows.remove(row);
        windowsContainer.remove(row);
        for (int index = 0; index < windowRows.size(); index++) {
            windowRows.get(index).setNumber(index + 1);
        }
        addWindowButton.setVisible(true);
    }

    private void createRequest() {
        if (!validateForm()) {
            showError("Bitte prüfen Sie die markierten Pflichtfelder und Zeitfenster.");
            return;
        }

        List<AvailabilityWindow> windows = windowRows.stream().map(WindowRow::toWindow).toList();
        SearchRequestCommand command = new SearchRequestCommand(
                specialty.getValue(), appointmentType.getValue(), city.getValue(), postalCode.getValue(),
                radius.getValue(), windows, shortNotice.getValue(), newPatient.getValue(),
                billingType.getValue(), serviceTier.getValue(), neededBy.getValue());

        createRequestButton.setEnabled(false);
        try {
            workflow.createSearchRequest(command);
            Notification notification = Notification.show(
                    "Ihre Suchanfrage wurde gestartet. Eine Gebühr entsteht erst bei bestätigter Buchung.",
                    5_000, Notification.Position.TOP_CENTER);
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshData();
        } catch (PortalException | IllegalArgumentException exception) {
            showError(exception.getMessage());
        } finally {
            createRequestButton.setEnabled(true);
        }
    }

    private boolean validateForm() {
        boolean valid = true;
        valid &= required(specialty, specialty.getValue() != null);
        valid &= required(appointmentType, appointmentType.getValue() != null);
        boolean cityPresent = city.getValue() != null && !city.getValue().isBlank();
        boolean postalPresent = postalCode.getValue() != null && !postalCode.getValue().isBlank();
        boolean locationPresent = cityPresent || postalPresent;
        valid &= required(city, locationPresent);
        valid &= required(postalCode, locationPresent && (!postalPresent
                || postalCode.getValue().matches("[0-9]{5}")));
        valid &= required(radius, radius.getValue() != null
                && radius.getValue() >= 1 && radius.getValue() <= 100);
        valid &= required(billingType, billingType.getValue() != null);
        valid &= required(serviceTier, serviceTier.getValue() != null);
        for (WindowRow row : windowRows) {
            valid &= row.validate();
        }
        return valid;
    }

    private boolean required(com.vaadin.flow.component.HasValidation component, boolean valid) {
        component.setInvalid(!valid);
        return valid;
    }

    private void updateCostPreview() {
        costPreview.removeAll();
        ServiceTier tier = serviceTier.getValue();
        BillingType billing = billingType.getValue();

        Div portal = new Div();
        portal.addClassName("cost-preview-item");
        Span portalLabel = new Span("Portalgebühr");
        portalLabel.addClassName("cost-label");
        Span portalValue = new Span(tier == null ? "Bitte Tarif wählen" : money(tier.getFeeCents()));
        portalValue.addClassName("cost-value");
        Span portalHint = new Span("Nur bei erfolgreicher Buchung");
        portalHint.addClassName("cost-hint");
        portal.add(portalLabel, portalValue, portalHint);

        Div treatment = new Div();
        treatment.addClassName("cost-preview-item");
        Span treatmentLabel = new Span("Mögliche Behandlungskosten");
        treatmentLabel.addClassName("cost-label");
        Span treatmentValue = new Span(treatmentCostSummary(billing));
        treatmentValue.addClassName("treatment-value");
        Span treatmentHint = new Span("Werden von der Praxis separat erläutert und abgerechnet");
        treatmentHint.addClassName("cost-hint");
        treatment.add(treatmentLabel, treatmentValue, treatmentHint);

        costPreview.add(portal, treatment);
    }

    private String treatmentCostSummary(BillingType billing) {
        if (billing == null) {
            return "Bitte Abrechnungsart wählen";
        }
        return switch (billing) {
            case STATUTORY -> "Kassenleistung; mögliche Zusatzleistungen ausgenommen";
            case PRIVATE -> "Nach privater Abrechnung / GOÄ";
            case SELF_PAY -> "Selbstzahlung nach GOÄ; nur passende Selbstzahlertermine";
        };
    }

    private void refreshData() {
        try {
            List<PortalViews.PatientRequestView> requests = workflow.patientRequests();
            List<PortalViews.PatientOfferView> offers = workflow.patientOffers();
            List<PortalViews.PatientBookingView> bookings = workflow.patientBookings();
            renderRequests(requests);
            renderOffers(offers);
            renderBookings(bookings);
            requestCount.setText(Integer.toString(requests.size()));
            long pendingOffers = offers.stream().filter(offer -> offer.status() == OfferStatus.PENDING).count();
            offerCount.setText(pendingOffers > 0 ? pendingOffers + " offen" : Integer.toString(offers.size()));
            bookingCount.setText(Integer.toString(bookings.size()));
        } catch (PortalException exception) {
            showError(exception.getMessage());
        }
    }

    private void renderRequests(List<PortalViews.PatientRequestView> requests) {
        requestsContent.removeAll();
        if (requests.isEmpty()) {
            requestsContent.add(emptyState(
                    VaadinIcon.SEARCH, "Noch keine Suchanfrage",
                    "Füllen Sie oben das Formular aus. Praxen sehen anschließend nur die für ihren Termin passenden Angaben."));
            return;
        }
        requests.forEach(request -> requestsContent.add(requestCard(request)));
    }

    private Component requestCard(PortalViews.PatientRequestView request) {
        Div card = new Div();
        card.addClassNames("patient-data-card", "request-card");

        Div top = new Div();
        top.addClassName("data-card-top");
        Div title = new Div();
        H3 heading = new H3(request.specialty().getLabel());
        Paragraph type = new Paragraph(request.appointmentType().getLabel());
        title.add(heading, type);
        top.add(title, requestStatus(request.status()));

        Div details = new Div();
        details.addClassName("request-detail-grid");
        details.add(
                fact(VaadinIcon.MAP_MARKER, "Suchgebiet", request.postalCode() + " " + request.city()
                        + " · " + request.radiusKm() + " km"),
                fact(VaadinIcon.CREDIT_CARD, "Abrechnung", request.billingType().getLabel()),
                fact(VaadinIcon.CLOCK, "Verfügbarkeit", request.shortNotice()
                        ? "Auch kurzfristig" : "Mindestens 48 Stunden Vorlauf"),
                fact(VaadinIcon.USER, "Praxisstatus", request.newPatient()
                        ? "Neupatient" : "Bestandspatient"));

        Div windows = new Div();
        windows.addClassName("request-windows");
        Span label = new Span("Zeitfenster");
        label.addClassName("fact-label");
        windows.add(label);
        request.windows().forEach(window -> windows.add(new Span(formatWindow(window))));

        Div footer = new Div();
        footer.addClassName("data-card-footer");
        Span tier = new Span(request.serviceTier().getLabel() + " · "
                + money(request.serviceTier().getFeeCents()) + " nur bei Buchung");
        Span created = new Span("Gestartet am " + request.createdAt().format(SHORT_DATE_TIME));
        footer.add(tier, created);
        if (request.neededBy() != null) {
            footer.add(new Span("Benötigt bis " + request.neededBy().format(DATE)));
        }

        card.add(top, details, windows, footer);
        return card;
    }

    private void renderOffers(List<PortalViews.PatientOfferView> offers) {
        offersContent.removeAll();
        if (offers.isEmpty()) {
            offersContent.add(emptyState(
                    VaadinIcon.CALENDAR_CLOCK, "Noch kein Praxisangebot",
                    "Sobald eine Praxis einen passenden frei gewordenen Termin anbietet, erscheint er hier."));
            return;
        }
        offers.forEach(offer -> offersContent.add(offerCard(offer)));
    }

    private Component offerCard(PortalViews.PatientOfferView offer) {
        Div card = new Div();
        card.addClassNames("patient-data-card", "offer-card");
        boolean pending = offer.status() == OfferStatus.PENDING;
        boolean withinDeadline = pending && offer.expiresAt().isAfter(LocalDateTime.now());
        if (withinDeadline) {
            card.addClassName("offer-open");
        }

        Div top = new Div();
        top.addClassName("data-card-top");
        Div title = new Div();
        H3 practice = new H3(offer.practiceName());
        Paragraph address = new Paragraph(offer.practiceAddress());
        title.add(practice, address);
        top.add(title, offerStatus(offer.status(), withinDeadline));

        Div appointment = new Div();
        appointment.addClassName("offer-appointment");
        appointment.add(VaadinIcon.CALENDAR.create());
        Div appointmentText = new Div();
        Span date = new Span(capitalize(offer.startsAt().format(DATE_TIME)));
        date.addClassName("offer-date");
        Span type = new Span(offer.appointmentType().getLabel() + " · " + offer.durationMinutes() + " Minuten");
        type.addClassName("offer-type");
        appointmentText.add(date, type);
        appointment.add(appointmentText);

        Div costs = new Div();
        costs.addClassName("offer-costs");
        costs.add(costBox(
                        "Portalgebühr", money(offer.portalFeeCents()),
                        "Entsteht erst mit Ihrer Annahme und der bestätigten Buchung.", "portal-fee-box"),
                costBox(
                        "Mögliche Behandlungskosten", offer.treatmentCostInformation(),
                        "Nicht in der Portalgebühr enthalten; Abrechnung separat durch die Praxis.", "treatment-cost-box"));

        Div meta = new Div();
        meta.addClassName("offer-meta");
        meta.add(new Span("Abrechnung: " + offer.billingType().getLabel()));
        if (pending) {
            Span expiry = new Span(withinDeadline
                    ? "Angebot gültig bis " + offer.expiresAt().format(SHORT_DATE_TIME)
                    : "Die Angebotsfrist ist abgelaufen");
            expiry.addClassName(withinDeadline ? "offer-expiry" : "offer-expired");
            meta.add(expiry);
        }

        card.add(top, appointment, costs, meta);
        if (withinDeadline) {
            card.add(createOfferActions(offer));
        }
        return card;
    }

    private Component createOfferActions(PortalViews.PatientOfferView offer) {
        Button decline = new Button("Ablehnen");
        decline.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        Button accept = new Button("Termin annehmen", VaadinIcon.CHECK.create());
        accept.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        accept.addClickListener(event -> showAcceptDialog(offer, accept, decline));
        decline.addClickListener(event -> showDeclineDialog(offer, accept, decline));

        Div actions = new Div(decline, accept);
        actions.addClassName("offer-actions");
        return actions;
    }

    private void showAcceptDialog(PortalViews.PatientOfferView offer, Button accept, Button decline) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Termin verbindlich annehmen?");
        Div content = new Div();
        content.addClassName("patient-dialog-content");
        content.add(
                new Paragraph(offer.practiceName() + " · " + capitalize(offer.startsAt().format(DATE_TIME))),
                dialogCostRow("Portalgebühr", money(offer.portalFeeCents())),
                dialogCostRow("Behandlungskosten", "separat durch die Praxis"),
                new Paragraph("Mit Ihrer Annahme entsteht eine bestätigte Buchung. Erst dann wird die Portalgebühr einmalig über den simulierten Zahlungsanbieter erhoben."));

        Button cancel = new Button("Zurück", event -> dialog.close());
        Button confirm = new Button("Jetzt verbindlich buchen", VaadinIcon.CHECK.create());
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.addClickListener(event -> {
            accept.setEnabled(false);
            decline.setEnabled(false);
            confirm.setEnabled(false);
            dialog.setCloseOnEsc(false);
            dialog.setCloseOnOutsideClick(false);
            try {
                PortalViews.BookingReceipt receipt = workflow.acceptOffer(offer.id());
                dialog.close();
                Notification notification = Notification.show(
                        "Termin bestätigt. Portalgebühr: " + money(receipt.amountCents()) + " · "
                                + feeStatusLabel(receipt.feeStatus()),
                        6_000, Notification.Position.TOP_CENTER);
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (PortalException exception) {
                dialog.close();
                showError(exception.getMessage());
            } finally {
                refreshData();
            }
        });
        dialog.add(content);
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private void showDeclineDialog(PortalViews.PatientOfferView offer, Button accept, Button decline) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Angebot ablehnen?");
        dialog.setText("Die Praxis kann den frei gewordenen Platz anschließend einer anderen passenden Anfrage anbieten. Ihre Suche bleibt offen.");
        dialog.setCancelable(true);
        dialog.setCancelText("Zurück");
        dialog.setConfirmText("Angebot ablehnen");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            accept.setEnabled(false);
            decline.setEnabled(false);
            try {
                workflow.declineOffer(offer.id());
                Notification.show("Angebot abgelehnt. Ihre Suchanfrage ist wieder offen.",
                        4_000, Notification.Position.TOP_CENTER);
            } catch (PortalException exception) {
                showError(exception.getMessage());
            } finally {
                refreshData();
            }
        });
        dialog.open();
    }

    private void renderBookings(List<PortalViews.PatientBookingView> bookings) {
        bookingsContent.removeAll();
        if (bookings.isEmpty()) {
            bookingsContent.add(emptyState(
                    VaadinIcon.CHECK_CIRCLE, "Noch keine bestätigte Buchung",
                    "Eine Buchung und die zugehörige Portalgebühr entstehen erst, wenn Sie ein Praxisangebot annehmen."));
            return;
        }
        bookings.forEach(booking -> bookingsContent.add(bookingCard(booking)));
    }

    private Component bookingCard(PortalViews.PatientBookingView booking) {
        Div card = new Div();
        card.addClassNames("patient-data-card", "booking-card");
        if (booking.status() == BookingStatus.CANCELLED_BY_PRACTICE) {
            card.addClassName("booking-cancelled");
        }

        Div top = new Div();
        top.addClassName("data-card-top");
        Div title = new Div();
        H3 practice = new H3(booking.practiceName());
        Paragraph address = new Paragraph(booking.practiceAddress());
        title.add(practice, address);
        top.add(title, bookingStatus(booking.status()));

        Div appointment = new Div();
        appointment.addClassName("booking-appointment");
        appointment.add(
                fact(VaadinIcon.CALENDAR, "Termin", capitalize(booking.startsAt().format(DATE_TIME))),
                fact(VaadinIcon.STETHOSCOPE, "Terminart", booking.appointmentType().getLabel()
                        + " · " + booking.durationMinutes() + " Minuten"));

        Div fee = new Div();
        fee.addClassName("booking-fee");
        Div feeCopy = new Div();
        Span label = new Span("Portalgebühr");
        label.addClassName("fact-label");
        Span amount = new Span(money(booking.portalFeeCents()));
        amount.addClassName("booking-fee-amount");
        Span reference = new Span("Demo-Zahlungsreferenz: " + booking.paymentReference());
        reference.addClassName("booking-reference");
        feeCopy.add(label, amount, reference);
        fee.add(feeCopy, feeStatus(booking.feeStatus()));

        Paragraph treatment = new Paragraph("Behandlungskosten sind nicht Teil der Portalgebühr und werden gegebenenfalls separat von der Praxis abgerechnet.");
        treatment.addClassName("booking-treatment-note");
        card.add(top, appointment, fee, treatment);
        return card;
    }

    private Div costBox(String label, String value, String hint, String className) {
        Div box = new Div();
        box.addClassNames("offer-cost-box", className);
        Span labelSpan = new Span(label);
        labelSpan.addClassName("cost-label");
        Span valueSpan = new Span(value);
        valueSpan.addClassName("offer-cost-value");
        Span hintSpan = new Span(hint);
        hintSpan.addClassName("cost-hint");
        box.add(labelSpan, valueSpan, hintSpan);
        return box;
    }

    private Div dialogCostRow(String label, String value) {
        Div row = new Div(new Span(label), new Span(value));
        row.addClassName("dialog-cost-row");
        return row;
    }

    private Div fact(VaadinIcon iconType, String label, String value) {
        Div fact = new Div();
        fact.addClassName("patient-fact");
        fact.add(iconType.create());
        Div text = new Div();
        Span labelSpan = new Span(label);
        labelSpan.addClassName("fact-label");
        Span valueSpan = new Span(value);
        valueSpan.addClassName("fact-value");
        text.add(labelSpan, valueSpan);
        fact.add(text);
        return fact;
    }

    private Div formSection(String title, String subtitle, Component... content) {
        Div section = new Div();
        section.addClassName("patient-form-section");
        H3 heading = new H3(title);
        Paragraph explanation = new Paragraph(subtitle);
        section.add(heading, explanation);
        section.add(content);
        return section;
    }

    private Div emptyState(VaadinIcon iconType, String title, String copy) {
        Div state = new Div();
        state.addClassName("patient-empty-state");
        state.add(iconType.create(), new H3(title), new Paragraph(copy));
        return state;
    }

    private Div iconTile(VaadinIcon iconType) {
        Div tile = new Div(iconType.create());
        tile.addClassName("section-icon");
        return tile;
    }

    private StatusBadge requestStatus(SearchRequestStatus status) {
        return switch (status) {
            case OPEN -> StatusBadge.of("Suche offen", StatusBadge.Tone.INFO);
            case OFFERED -> StatusBadge.of("Angebot erhalten", StatusBadge.Tone.WARNING);
            case BOOKED -> StatusBadge.of("Gebucht", StatusBadge.Tone.SUCCESS);
            case CLOSED -> StatusBadge.of("Beendet", StatusBadge.Tone.NEUTRAL);
        };
    }

    private StatusBadge offerStatus(OfferStatus status, boolean withinDeadline) {
        if (status == OfferStatus.PENDING && !withinDeadline) {
            return StatusBadge.of("Frist abgelaufen", StatusBadge.Tone.NEUTRAL);
        }
        return switch (status) {
            case PENDING -> StatusBadge.of("Ihre Entscheidung", StatusBadge.Tone.WARNING);
            case ACCEPTED -> StatusBadge.of("Angenommen", StatusBadge.Tone.SUCCESS);
            case DECLINED -> StatusBadge.of("Abgelehnt", StatusBadge.Tone.NEUTRAL);
            case EXPIRED -> StatusBadge.of("Abgelaufen", StatusBadge.Tone.NEUTRAL);
            case WITHDRAWN -> StatusBadge.of("Zurückgezogen", StatusBadge.Tone.DANGER);
        };
    }

    private StatusBadge bookingStatus(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> StatusBadge.of("Verbindlich gebucht", StatusBadge.Tone.SUCCESS);
            case CANCELLED_BY_PRACTICE -> StatusBadge.of("Von Praxis abgesagt", StatusBadge.Tone.DANGER);
        };
    }

    private StatusBadge feeStatus(FeeStatus status) {
        StatusBadge.Tone tone = switch (status) {
            case PAID -> StatusBadge.Tone.SUCCESS;
            case REFUND_PENDING -> StatusBadge.Tone.WARNING;
            case REFUNDED, VOID -> StatusBadge.Tone.NEUTRAL;
            case OPEN -> StatusBadge.Tone.INFO;
        };
        return StatusBadge.of(feeStatusLabel(status), tone);
    }

    private String feeStatusLabel(FeeStatus status) {
        return switch (status) {
            case OPEN -> "Zahlung offen";
            case PAID -> "Bezahlt (Demo)";
            case VOID -> "Gebühr storniert";
            case REFUND_PENDING -> "Erstattung vorgemerkt";
            case REFUNDED -> "Erstattet";
        };
    }

    private String formatWindow(AvailabilityWindow window) {
        if (window.getStart().toLocalDate().equals(window.getEnd().toLocalDate())) {
            return window.getStart().format(DateTimeFormatter.ofPattern("EE, dd.MM. · HH:mm", GERMAN))
                    + "–" + window.getEnd().format(DateTimeFormatter.ofPattern("HH:mm 'Uhr'", GERMAN));
        }
        return window.getStart().format(SHORT_DATE_TIME) + " – " + window.getEnd().format(SHORT_DATE_TIME);
    }

    private String money(int cents) {
        return NumberFormat.getCurrencyInstance(GERMAN).format(cents / 100.0);
    }

    private String capitalize(String value) {
        return value == null || value.isEmpty() ? value
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void showError(String message) {
        Notification notification = Notification.show(
                message == null || message.isBlank() ? "Die Aktion konnte nicht ausgeführt werden." : message,
                5_000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private final class WindowRow extends Div {
        private final Span number = new Span();
        private final DatePicker date = new DatePicker("Datum");
        private final TimePicker start = new TimePicker("Von");
        private final TimePicker end = new TimePicker("Bis");

        private WindowRow(int rowNumber, boolean removable) {
            addClassName("availability-window-row");
            number.addClassName("window-number");
            setNumber(rowNumber);

            date.setLocale(GERMAN);
            date.setMin(LocalDate.now());
            date.setValue(LocalDate.now().plusDays(rowNumber));
            date.setRequiredIndicatorVisible(true);

            start.setLocale(GERMAN);
            start.setStep(Duration.ofMinutes(30));
            start.setValue(LocalTime.of(8, 0));
            start.setRequiredIndicatorVisible(true);

            end.setLocale(GERMAN);
            end.setStep(Duration.ofMinutes(30));
            end.setValue(LocalTime.of(18, 0));
            end.setRequiredIndicatorVisible(true);

            Div fields = new Div(date, start, end);
            fields.addClassName("window-fields");
            add(number, fields);
            if (removable) {
                Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> removeWindowRow(this));
                remove.setAriaLabel("Zeitfenster entfernen");
                remove.addClassName("remove-window-button");
                remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
                add(remove);
            }
        }

        private void setNumber(int value) {
            number.setText("Zeitfenster " + value);
        }

        private boolean validate() {
            boolean dateValid = date.getValue() != null && !date.getValue().isBefore(LocalDate.now());
            boolean startValid = start.getValue() != null;
            boolean endValid = end.getValue() != null && startValid && end.getValue().isAfter(start.getValue());
            if (dateValid && startValid && endValid) {
                endValid = date.getValue().atTime(end.getValue()).isAfter(LocalDateTime.now());
            }
            date.setInvalid(!dateValid);
            start.setInvalid(!startValid);
            end.setInvalid(!endValid);
            if (!endValid) {
                end.setErrorMessage("Das Ende muss nach dem Beginn und in der Zukunft liegen.");
            }
            return dateValid && startValid && endValid;
        }

        private AvailabilityWindow toWindow() {
            return new AvailabilityWindow(date.getValue().atTime(start.getValue()),
                    date.getValue().atTime(end.getValue()));
        }
    }
}
