package com.kiefer.arzttermin.service.dto;

import com.kiefer.arzttermin.domain.AppointmentType;
import com.kiefer.arzttermin.domain.AvailabilityWindow;
import com.kiefer.arzttermin.domain.BillingType;
import com.kiefer.arzttermin.domain.ServiceTier;
import com.kiefer.arzttermin.domain.Specialty;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record SearchRequestCommand(
        Specialty specialty,
        AppointmentType appointmentType,
        String city,
        String postalCode,
        int radiusKm,
        List<AvailabilityWindow> availabilityWindows,
        boolean shortNotice,
        boolean newPatient,
        BillingType billingType,
        ServiceTier serviceTier,
        LocalDate neededBy
) {
    public SearchRequestCommand {
        Objects.requireNonNull(specialty, "specialty");
        Objects.requireNonNull(appointmentType, "appointmentType");
        Objects.requireNonNull(billingType, "billingType");
        Objects.requireNonNull(serviceTier, "serviceTier");
        city = normalize(city);
        postalCode = normalize(postalCode);
        if (city.isBlank() && postalCode.isBlank()) {
            throw new IllegalArgumentException("Bitte geben Sie einen Ort oder eine Postleitzahl an.");
        }
        if (radiusKm < 1 || radiusKm > 100) {
            throw new IllegalArgumentException("Der Suchradius muss zwischen 1 und 100 km liegen.");
        }
        availabilityWindows = availabilityWindows == null ? List.of() : List.copyOf(availabilityWindows);
        if (availabilityWindows.isEmpty()) {
            throw new IllegalArgumentException("Mindestens ein Zeitfenster ist erforderlich.");
        }
        if (serviceTier == ServiceTier.AUTOMATIC_5) {
            neededBy = null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
