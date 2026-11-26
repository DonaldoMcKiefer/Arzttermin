package com.kiefer.arzttermin.ui;

import com.kiefer.arzttermin.domain.ContactChannel;
import com.kiefer.arzttermin.domain.ContactResponse;
import com.kiefer.arzttermin.service.PortalException;
import com.kiefer.arzttermin.service.PortalWorkflowService;
import com.kiefer.arzttermin.service.dto.ContactCommand;
import com.kiefer.arzttermin.service.dto.PortalViews;
import com.kiefer.arzttermin.ui.component.StatusBadge;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
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
import java.util.UUID;

@Route(value = "staff", layout = MainLayout.class)
@PageTitle("Persönliche Suche | Terminfinder")
@RolesAllowed("STAFF")
@CssImport("./themes/terminfinder/views/workspaces.css")
public class StaffView extends Composite<Div> {

    private final PortalWorkflowService service;
    private final Grid<PortalViews.StaffRequestView> grid = new Grid<>(PortalViews.StaffRequestView.class, false);
    private final Div detail = new Div();
    private UUID selectedRequestId;

    public StaffView(PortalWorkflowService service) {
        this.service = service;
        Div page = getContent();
        page.addClassNames("content-page", "workspace-page");
        page.add(header(), worklist(), detail);
        detail.addClassName("workspace-detail");
        configureGrid();
        refresh();
    }

    private Div header() {
        Div header = new Div();
        header.addClassName("workspace-header");
        Div copy = new Div(new Div("PORTALTEAM"), new H1("Persönliche Suchen begleiten"),
                new Paragraph("Dokumentieren Sie kontaktierte Praxen und Rückmeldungen. Die Arbeitsliste verändert weder Reihenfolge noch medizinische Priorität."));
        copy.getComponentAt(0).addClassName("eyebrow");
        copy.getComponentAt(2).addClassName("page-lead");
        header.add(copy);
        return header;
    }

    private Div worklist() {
        Div panel = new Div(new H2("Arbeitsliste"),
                new Paragraph("Ausschließlich Suchaufträge mit persönlicher Kontaktaufnahme."), grid);
        panel.addClassName("workspace-card");
        panel.getComponentAt(1).addClassName("muted");
        return panel;
    }

    private void configureGrid() {
        grid.addColumn(PortalViews.StaffRequestView::patientDisplayName).setHeader("Patient:in").setAutoWidth(true);
        grid.addColumn(item -> item.specialty().getLabel()).setHeader("Fachrichtung").setAutoWidth(true);
        grid.addColumn(item -> item.appointmentType().getLabel()).setHeader("Terminart").setAutoWidth(true);
        grid.addColumn(PortalViews.StaffRequestView::location).setHeader("Suchort").setAutoWidth(true);
        grid.addColumn(item -> UiFormat.date(item.neededBy())).setHeader("Benötigt bis").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(item -> StatusBadge.of(
                UiFormat.personalStatus(item.personalSearchStatus()), statusTone(item)))).setHeader("Bearbeitung").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(item -> {
            Button open = new Button("Öffnen", event -> select(item.requestId()));
            open.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            return open;
        })).setHeader("").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.addClassName("workspace-grid");
    }

    private void refresh() {
        List<PortalViews.StaffRequestView> items = service.personalSearchWorklist();
        grid.setItems(items);
        if (selectedRequestId != null) {
            items.stream().filter(item -> item.requestId().equals(selectedRequestId)).findFirst()
                    .ifPresentOrElse(this::renderDetail, () -> detail.removeAll());
        }
    }

    private void select(UUID requestId) {
        selectedRequestId = requestId;
        service.personalSearchWorklist().stream().filter(item -> item.requestId().equals(requestId)).findFirst()
                .ifPresent(this::renderDetail);
    }

    private void renderDetail(PortalViews.StaffRequestView item) {
        detail.removeAll();
        Div title = new Div(new H2(item.patientDisplayName() + " · " + item.appointmentType().getLabel()),
                new Paragraph("Anfrage vom " + UiFormat.dateTime(item.createdAt())));
        Div heading = new Div(title, StatusBadge.of(UiFormat.personalStatus(item.personalSearchStatus()), statusTone(item)));
        heading.addClassName("detail-heading");
        detail.add(heading);

        Div metadata = new Div(
                meta("Fachrichtung", item.specialty().getLabel()),
                meta("Suchgebiet", item.location() + " · " + item.radiusKm() + " km"),
                meta("Abrechnung", item.billingType().getLabel()),
                meta("Termin benötigt bis", UiFormat.date(item.neededBy())));
        metadata.addClassName("metadata-grid");
        detail.add(metadata);

        Div contactHeading = new Div(new H3("Praxiskontakte"),
                new Button("Kontakt erfassen", VaadinIcon.PLUS.create(), event -> openContactDialog(item)));
        contactHeading.addClassName("section-heading");
        ((Button) contactHeading.getComponentAt(1)).addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        detail.add(contactHeading);
        if (item.contacts().isEmpty()) {
            detail.add(emptyState());
        } else {
            Grid<PortalViews.ContactView> contacts = new Grid<>(PortalViews.ContactView.class, false);
            contacts.addColumn(PortalViews.ContactView::practiceName).setHeader("Praxis").setAutoWidth(true);
            contacts.addColumn(contact -> UiFormat.dateTime(contact.contactedAt())).setHeader("Kontaktiert").setAutoWidth(true);
            contacts.addColumn(contact -> contact.channel().getLabel()).setHeader("Kanal").setAutoWidth(true);
            contacts.addColumn(contact -> contact.response().getLabel()).setHeader("Rückmeldung").setAutoWidth(true);
            contacts.addColumn(PortalViews.ContactView::note).setHeader("Notiz").setFlexGrow(1);
            contacts.setItems(item.contacts());
            contacts.setAllRowsVisible(true);
            contacts.addClassName("workspace-grid");
            detail.add(contacts);
        }
    }

    private void openContactDialog(PortalViews.StaffRequestView item) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Praxiskontakt erfassen");
        dialog.setWidth("min(42rem, calc(100vw - 2rem))");
        TextField practice = new TextField("Kontaktierte Praxis");
        practice.setRequired(true);
        DatePicker date = new DatePicker("Datum");
        date.setLocale(UiFormat.GERMAN);
        date.setValue(LocalDate.now());
        TimePicker time = new TimePicker("Uhrzeit");
        time.setStep(Duration.ofMinutes(15));
        time.setValue(LocalTime.now().withSecond(0).withNano(0));
        ComboBox<ContactChannel> channel = new ComboBox<>("Kontaktweg");
        channel.setItems(ContactChannel.values());
        channel.setItemLabelGenerator(ContactChannel::getLabel);
        channel.setValue(ContactChannel.PHONE);
        ComboBox<ContactResponse> response = new ComboBox<>("Rückmeldung");
        response.setItems(ContactResponse.values());
        response.setItemLabelGenerator(ContactResponse::getLabel);
        response.setValue(ContactResponse.NO_RESPONSE);
        TextArea note = new TextArea("Notiz");
        note.setMaxLength(1000);
        note.setPlaceholder("z. B. Rückruf am Nachmittag vereinbart");

        Div form = new Div(practice, date, time, channel, response, note);
        form.addClassName("form-grid");
        practice.addClassName("full-span");
        note.addClassName("full-span");
        dialog.add(form);

        Button save = new Button("Kontakt speichern", event -> {
            try {
                LocalDateTime contactedAt = LocalDateTime.of(
                        require(date.getValue(), "Datum"), require(time.getValue(), "Uhrzeit"));
                if (contactedAt.isAfter(LocalDateTime.now())) {
                    throw new PortalException("Der Kontaktzeitpunkt darf nicht in der Zukunft liegen.");
                }
                service.addPracticeContact(item.requestId(), new ContactCommand(
                        practice.getValue(), contactedAt,
                        require(channel.getValue(), "Kontaktweg"), require(response.getValue(), "Rückmeldung"), note.getValue()));
                dialog.close();
                refresh();
                success("Praxiskontakt wurde dokumentiert.");
            } catch (RuntimeException exception) {
                error(exception.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Abbrechen", event -> dialog.close()), save);
        dialog.open();
    }

    private StatusBadge.Tone statusTone(PortalViews.StaffRequestView item) {
        return switch (item.personalSearchStatus()) {
            case NEW -> StatusBadge.Tone.INFO;
            case IN_PROGRESS, WAITING_FOR_PRACTICE -> StatusBadge.Tone.WARNING;
            case COMPLETED -> StatusBadge.Tone.SUCCESS;
            case NOT_APPLICABLE -> StatusBadge.Tone.NEUTRAL;
        };
    }

    private Div meta(String label, String value) {
        Div item = new Div(new Span(label), strong(value));
        item.addClassName("metadata-item");
        return item;
    }

    private Div emptyState() {
        Div empty = new Div(VaadinIcon.PHONE.create(),
                new Div(strong("Noch kein Kontakt erfasst"), new Paragraph("Fügen Sie den ersten Praxiskontakt hinzu.")));
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
