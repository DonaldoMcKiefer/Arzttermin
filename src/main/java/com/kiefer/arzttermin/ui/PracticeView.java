package com.kiefer.arzttermin.ui;

import com.kiefer.arzttermin.domain.*;
import com.kiefer.arzttermin.service.PortalException;
import com.kiefer.arzttermin.service.PortalWorkflowService;
import com.kiefer.arzttermin.service.dto.PortalViews;
import com.kiefer.arzttermin.service.dto.SlotCommand;
import com.kiefer.arzttermin.ui.component.StatusBadge;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Route(value = "practice", layout = MainLayout.class)
@PageTitle("Praxis-Termine | Terminfinder")
@RolesAllowed("PRACTICE")
@CssImport("./themes/terminfinder/views/workspaces.css")
public class PracticeView extends Composite<Div> {

    private final PortalWorkflowService service;
    private final Grid<PortalViews.PracticeSlotView> slotGrid = new Grid<>(PortalViews.PracticeSlotView.class, false);
    private final Div detail = new Div();
    private UUID selectedSlotId;

    public PracticeView(PortalWorkflowService service) {
        this.service = service;
        Div page = getContent();
        page.addClassNames("content-page", "workspace-page");
        page.add(header(), calendarNotice(), slotsPanel(), detail);
        detail.addClassName("workspace-detail");
        configureSlotGrid();
        refresh();
    }

    private Div header() {
        Div copy = new Div();
        copy.add(new Div("PRAXISANSICHT"), new H1("Kurzfristig freien Termin vergeben"),
                new Paragraph("Melden Sie einen Platz manuell frei und wählen Sie selbst aus chronologisch sortierten, passenden Anfragen."));
        copy.getComponentAt(0).addClassName("eyebrow");
        copy.getComponentAt(2).addClassName("page-lead");
        Button add = new Button("Termin freigeben", VaadinIcon.PLUS.create(), event -> openCreateDialog());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div header = new Div(copy, add);
        header.addClassName("workspace-header");
        return header;
    }

    private Div calendarNotice() {
        Div notice = new Div();
        notice.addClassNames("notice-card", "calendar-notice");
        Icon icon = VaadinIcon.CALENDAR_CLOCK.create();
        Div text = new Div(strong("Lokaler Demo-Kalender"),
                new Paragraph("Keine Praxissoftware verbunden. Termine werden für den Pilotbetrieb manuell verwaltet."));
        notice.add(icon, text);
        return notice;
    }

    private Div slotsPanel() {
        Div panel = new Div();
        panel.addClassName("workspace-card");
        H2 title = new H2("Freigegebene Termine");
        Paragraph hint = new Paragraph("Wählen Sie einen freien Termin, um passende Anfragen zu sehen.");
        hint.addClassName("muted");
        panel.add(title, hint, slotGrid);
        return panel;
    }

    private void configureSlotGrid() {
        slotGrid.addColumn(slot -> slot.appointmentType().getLabel()).setHeader("Termin").setAutoWidth(true).setFlexGrow(1);
        slotGrid.addColumn(slot -> UiFormat.dateTime(slot.startsAt())).setHeader("Zeitpunkt").setAutoWidth(true);
        slotGrid.addColumn(slot -> UiFormat.billing(slot.acceptedBillingTypes())).setHeader("Abrechnung").setAutoWidth(true);
        slotGrid.addColumn(new ComponentRenderer<>(slot -> status(slot.status()))).setHeader("Status").setAutoWidth(true);
        slotGrid.addColumn(new ComponentRenderer<>(slot -> {
            Button select = new Button("Öffnen", event -> selectSlot(slot.id()));
            select.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            return select;
        })).setHeader("").setAutoWidth(true).setFlexGrow(0);
        slotGrid.setAllRowsVisible(true);
        slotGrid.addClassName("workspace-grid");
    }

    private void refresh() {
        List<PortalViews.PracticeSlotView> items = service.practiceSlots();
        slotGrid.setItems(items);
        if (selectedSlotId != null) {
            items.stream().filter(slot -> slot.id().equals(selectedSlotId)).findFirst()
                    .ifPresentOrElse(this::renderDetail, () -> {
                        selectedSlotId = null;
                        detail.removeAll();
                    });
        }
    }

    private void selectSlot(UUID id) {
        selectedSlotId = id;
        service.practiceSlots().stream().filter(slot -> slot.id().equals(id)).findFirst()
                .ifPresent(this::renderDetail);
    }

    private void renderDetail(PortalViews.PracticeSlotView slot) {
        detail.removeAll();
        Div heading = new Div();
        heading.addClassName("detail-heading");
        Div title = new Div(new H2(slot.appointmentType().getLabel()),
                new Paragraph(slot.specialty().getLabel() + " · " + UiFormat.dateTime(slot.startsAt())
                        + " · " + slot.durationMinutes() + " Minuten"));
        heading.add(title, status(slot.status()));
        detail.add(heading, metadata(slot));

        if (slot.status() == SlotStatus.AVAILABLE) {
            renderMatches(slot);
        } else if (slot.status() == SlotStatus.OFFERED) {
            detail.add(infoBlock("Angebot versendet",
                    "Der Platz ist bis zur Annahme, Ablehnung oder zum Ablauf der Frist reserviert. Danach kann er erneut angeboten werden."));
        } else if (slot.status() == SlotStatus.BOOKED) {
            Div booked = infoBlock("Verbindlich gebucht",
                    "Praxisangebot und Patientenannahme liegen vor. Genau eine Portalgebühr wurde erzeugt.");
            Button cancel = new Button("Termin durch Praxis absagen", VaadinIcon.CLOSE_CIRCLE.create(),
                    event -> confirmCancellation(slot));
            cancel.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            booked.add(cancel);
            detail.add(booked);
        } else {
            detail.add(infoBlock("Termin abgesagt",
                    "Die Suche des Patienten wurde wieder geöffnet. Eine bezahlte Gebühr ist zur Erstattung vorgemerkt."));
        }
    }

    private Div metadata(PortalViews.PracticeSlotView slot) {
        Div data = new Div();
        data.addClassName("metadata-grid");
        data.add(meta("Abrechnung", UiFormat.billing(slot.acceptedBillingTypes())),
                meta("Neupatienten", slot.acceptsNewPatients() ? "Zugelassen" : "Nicht zugelassen"),
                meta("Kalenderquelle", slot.calendarSourceLabel()),
                meta("Behandlungskosten", slot.treatmentCostInformation()));
        return data;
    }

    private void renderMatches(PortalViews.PracticeSlotView slot) {
        List<PortalViews.PracticeMatchView> matches = service.matchingRequests(slot.id());
        H3 title = new H3("Passende offene Anfragen");
        Paragraph privacy = new Paragraph("Älteste Anfrage zuerst. Portaltarif und Portalgebühr sind für die Auswahl bewusst nicht sichtbar.");
        privacy.addClassName("muted");
        detail.add(title, privacy);
        if (matches.isEmpty()) {
            detail.add(emptyState("Noch keine passende Anfrage", "Der Termin bleibt frei und kann später erneut geprüft werden."));
            return;
        }

        Grid<PortalViews.PracticeMatchView> grid = new Grid<>(PortalViews.PracticeMatchView.class, false);
        grid.addColumn(PortalViews.PracticeMatchView::patientDisplayName).setHeader("Patient:in").setAutoWidth(true);
        grid.addColumn(match -> UiFormat.dateTime(match.createdAt())).setHeader("Eingang").setAutoWidth(true);
        grid.addColumn(match -> match.location() + " · " + match.distanceKm() + " km").setHeader("Ort").setAutoWidth(true);
        grid.addColumn(match -> match.billingType().getLabel()).setHeader("Abrechnung").setAutoWidth(true);
        grid.addColumn(match -> formatWindows(match.windows())).setHeader("Passendes Zeitfenster").setAutoWidth(true);
        grid.addColumn(match -> match.shortNotice() ? "Ja" : "Nein").setHeader("Kurzfristig").setAutoWidth(true);
        grid.addColumn(match -> match.newPatient() ? "Neupatient:in" : "Bestand").setHeader("Patientenstatus").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(match -> {
            Button offer = new Button("Termin anbieten", event -> confirmOffer(slot, match));
            offer.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            return offer;
        })).setHeader("").setAutoWidth(true);
        grid.setItems(matches);
        grid.setAllRowsVisible(true);
        grid.addClassName("workspace-grid");
        detail.add(grid);
    }

    private void confirmOffer(PortalViews.PracticeSlotView slot, PortalViews.PracticeMatchView match) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Termin verbindlich anbieten?");
        dialog.setText("Das Angebot geht an " + match.patientDisplayName() + ". Während der Angebotsfrist ist der Platz reserviert.");
        dialog.setCancelable(true);
        dialog.setCancelText("Zurück");
        dialog.setConfirmText("Angebot senden");
        dialog.addConfirmListener(event -> runAction(() -> service.offerSlot(slot.id(), match.requestId()),
                "Angebot wurde versendet."));
        dialog.open();
    }

    private void confirmCancellation(PortalViews.PracticeSlotView slot) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Gebuchten Termin absagen?");
        dialog.setText("Die Patientensuche wird wieder geöffnet. Die simuliert bezahlte Portalgebühr wird zur Erstattung vorgemerkt.");
        dialog.setCancelable(true);
        dialog.setCancelText("Behalten");
        dialog.setConfirmText("Termin absagen");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> runAction(() -> service.cancelBooking(slot.bookingId()),
                "Termin abgesagt; Erstattung ist vorgemerkt."));
        dialog.open();
    }

    private void openCreateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Termin manuell freigeben");
        dialog.setWidth("min(46rem, calc(100vw - 2rem))");

        ComboBox<Specialty> specialty = new ComboBox<>("Fachrichtung");
        specialty.setItems(Specialty.values());
        specialty.setItemLabelGenerator(Specialty::getLabel);
        specialty.setValue(Specialty.DERMATOLOGY);
        ComboBox<AppointmentType> type = new ComboBox<>("Terminart");
        type.setItems(AppointmentType.values());
        type.setItemLabelGenerator(AppointmentType::getLabel);
        type.setValue(AppointmentType.SKIN_CANCER_SCREENING);
        DatePicker date = new DatePicker("Datum");
        date.setLocale(UiFormat.GERMAN);
        date.setMin(LocalDate.now());
        date.setValue(LocalDate.now().plusDays(1));
        TimePicker time = new TimePicker("Uhrzeit");
        time.setStep(Duration.ofMinutes(15));
        time.setValue(LocalTime.of(10, 30));
        IntegerField duration = new IntegerField("Dauer (Minuten)");
        duration.setMin(5);
        duration.setMax(240);
        duration.setStepButtonsVisible(true);
        duration.setValue(20);
        MultiSelectComboBox<BillingType> billing = new MultiSelectComboBox<>("Akzeptierte Abrechnungsarten");
        billing.setItems(BillingType.values());
        billing.setItemLabelGenerator(BillingType::getLabel);
        billing.select(BillingType.STATUTORY);
        Checkbox newPatients = new Checkbox("Neupatientinnen und Neupatienten zulassen", true);
        TextArea costs = new TextArea("Hinweis zu möglichen Behandlungskosten");
        costs.setPlaceholder("Leer lassen für einen passenden Standardhinweis");
        costs.setMaxLength(300);

        Div form = new Div(specialty, type, date, time, duration, billing, newPatients, costs);
        form.addClassName("form-grid");
        costs.addClassName("full-span");
        newPatients.addClassName("full-span");
        dialog.add(form);

        Button save = new Button("Termin freigeben", event -> {
            try {
                LocalDateTime startsAt = LocalDateTime.of(require(date.getValue(), "Datum"), require(time.getValue(), "Uhrzeit"));
                service.createSlot(new SlotCommand(
                        require(specialty.getValue(), "Fachrichtung"), require(type.getValue(), "Terminart"), startsAt,
                        require(duration.getValue(), "Dauer"), Set.copyOf(billing.getSelectedItems()),
                        newPatients.getValue(), costs.getValue()));
                dialog.close();
                selectedSlotId = null;
                refresh();
                success("Termin wurde im lokalen Demo-Kalender freigegeben.");
            } catch (RuntimeException exception) {
                error(exception.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button close = new Button("Abbrechen", event -> dialog.close());
        dialog.getFooter().add(close, save);
        dialog.open();
    }

    private void runAction(Runnable action, String successMessage) {
        try {
            action.run();
            refresh();
            success(successMessage);
        } catch (PortalException exception) {
            error(exception.getMessage());
        }
    }

    private StatusBadge status(SlotStatus status) {
        StatusBadge.Tone tone = switch (status) {
            case AVAILABLE -> StatusBadge.Tone.INFO;
            case OFFERED -> StatusBadge.Tone.WARNING;
            case BOOKED -> StatusBadge.Tone.SUCCESS;
            case CANCELLED -> StatusBadge.Tone.DANGER;
        };
        return StatusBadge.of(UiFormat.slotStatus(status), tone);
    }

    private Div meta(String label, String value) {
        Div item = new Div(new Span(label), strong(value));
        item.addClassName("metadata-item");
        return item;
    }

    private String formatWindows(List<AvailabilityWindow> windows) {
        return String.join("; ", windows.stream()
                .map(window -> window.getStart().format(UiFormat.DATE_TIME)
                        + " bis " + window.getEnd().format(UiFormat.TIME) + " Uhr")
                .toList());
    }

    private Div infoBlock(String title, String text) {
        Div block = new Div(new H3(title), new Paragraph(text));
        block.addClassName("info-block");
        return block;
    }

    private Div emptyState(String title, String text) {
        Div empty = new Div(VaadinIcon.SEARCH.create(), new Div(strong(title), new Paragraph(text)));
        empty.addClassName("empty-state");
        return empty;
    }

    private <T> T require(T value, String label) {
        if (value == null) throw new PortalException(label + " ist erforderlich.");
        return value;
    }

    private Span strong(String text) {
        Span span = new Span(text);
        span.addClassName("strong-text");
        return span;
    }

    private void success(String message) {
        Notification notification = Notification.show(message, 3500, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void error(String message) {
        Notification notification = Notification.show(message == null ? "Die Aktion konnte nicht ausgeführt werden." : message,
                5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
