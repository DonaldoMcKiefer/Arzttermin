package com.kiefer.arzttermin.ui;

import com.kiefer.arzttermin.domain.*;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;

final class UiFormat {
    static final Locale GERMAN = Locale.GERMANY;
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy · HH:mm", GERMAN);
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", GERMAN);
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", GERMAN);

    private UiFormat() {
    }

    static String dateTime(LocalDateTime value) {
        return value == null ? "–" : DATE_TIME.format(value);
    }

    static String date(LocalDate value) {
        return value == null ? "Nicht angegeben" : DATE.format(value);
    }

    static String euros(int cents) {
        return NumberFormat.getCurrencyInstance(GERMAN).format(cents / 100.0);
    }

    static String billing(Collection<BillingType> types) {
        return types.stream().map(BillingType::getLabel).sorted().collect(Collectors.joining(", "));
    }

    static String slotStatus(SlotStatus status) {
        return switch (status) {
            case AVAILABLE -> "Frei";
            case OFFERED -> "Angebot offen";
            case BOOKED -> "Gebucht";
            case CANCELLED -> "Abgesagt";
        };
    }

    static String requestStatus(SearchRequestStatus status) {
        return switch (status) {
            case OPEN -> "Suche läuft";
            case OFFERED -> "Angebot erhalten";
            case BOOKED -> "Gebucht";
            case CLOSED -> "Geschlossen";
        };
    }

    static String personalStatus(PersonalSearchStatus status) {
        return switch (status) {
            case NOT_APPLICABLE -> "–";
            case NEW -> "Neu";
            case IN_PROGRESS -> "In Bearbeitung";
            case WAITING_FOR_PRACTICE -> "Wartet auf Praxis";
            case COMPLETED -> "Abgeschlossen";
        };
    }

    static String feeStatus(FeeStatus status) {
        return switch (status) {
            case OPEN -> "Offen";
            case PAID -> "Simuliert bezahlt";
            case VOID -> "Storniert";
            case REFUND_PENDING -> "Erstattung vorgemerkt";
            case REFUNDED -> "Erstattet";
        };
    }
}
