package com.kiefer.arzttermin.service.dto;

import com.kiefer.arzttermin.domain.ContactChannel;
import com.kiefer.arzttermin.domain.ContactResponse;

import java.time.LocalDateTime;
import java.util.Objects;

public record ContactCommand(
        String practiceName,
        LocalDateTime contactedAt,
        ContactChannel channel,
        ContactResponse response,
        String note
) {
    public ContactCommand {
        if (practiceName == null || practiceName.isBlank()) {
            throw new IllegalArgumentException("Der Praxisname ist erforderlich.");
        }
        practiceName = practiceName.trim();
        Objects.requireNonNull(contactedAt, "contactedAt");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(response, "response");
        note = note == null ? "" : note.trim();
    }
}
