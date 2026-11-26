package com.kiefer.arzttermin.service.dto;

import com.kiefer.arzttermin.domain.AppointmentType;
import com.kiefer.arzttermin.domain.BillingType;
import com.kiefer.arzttermin.domain.Specialty;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

public record SlotCommand(
        Specialty specialty,
        AppointmentType appointmentType,
        LocalDateTime startsAt,
        int durationMinutes,
        Set<BillingType> acceptedBillingTypes,
        boolean acceptsNewPatients,
        String treatmentCostInformation
) {
    public SlotCommand {
        Objects.requireNonNull(specialty, "specialty");
        Objects.requireNonNull(appointmentType, "appointmentType");
        Objects.requireNonNull(startsAt, "startsAt");
        if (durationMinutes < 5 || durationMinutes > 240) {
            throw new IllegalArgumentException("Die Dauer muss zwischen 5 und 240 Minuten liegen.");
        }
        acceptedBillingTypes = acceptedBillingTypes == null ? Set.of() : Set.copyOf(acceptedBillingTypes);
        if (acceptedBillingTypes.isEmpty()) {
            throw new IllegalArgumentException("Mindestens eine Abrechnungsart ist erforderlich.");
        }
        treatmentCostInformation = treatmentCostInformation == null ? "" : treatmentCostInformation.trim();
    }
}
