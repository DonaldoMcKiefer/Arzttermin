package com.kiefer.arzttermin.calendar;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.kiefer.arzttermin.domain.AppointmentType;
import com.kiefer.arzttermin.domain.Specialty;

/**
 * Small boundary around a practice calendar.
 *
 * <p>The MVP uses an in-memory demo implementation. A later samedi or tomedo
 * adapter can implement this interface without leaking vendor-specific data
 * into booking services.</p>
 */
public interface PracticeCalendarGateway {

    /** A user-facing description of the connected calendar. */
    String sourceLabel();

    /**
     * Publishes a manually created slot. Repeating the same command must be
     * idempotent; reusing a slot id with different data must fail.
     */
    CalendarEntry publish(PublishSlotCommand command);

    /**
     * Marks a published entry as booked. Repeating the call for the same
     * booking is idempotent.
     */
    CalendarEntry markBooked(UUID slotId, UUID bookingId);

    /** Cancels a calendar entry. Repeating a cancellation is idempotent. */
    CalendarEntry cancel(UUID slotId, String reason);

    Optional<CalendarEntry> findBySlotId(UUID slotId);

    enum EntryStatus {
        PUBLISHED,
        BOOKED,
        CANCELLED
    }

    record PublishSlotCommand(
            UUID slotId,
            UUID practiceId,
            Specialty specialty,
            AppointmentType appointmentType,
            LocalDateTime startsAt,
            Duration duration) {

        public PublishSlotCommand {
            Objects.requireNonNull(slotId, "slotId");
            Objects.requireNonNull(practiceId, "practiceId");
            Objects.requireNonNull(specialty, "specialty");
            Objects.requireNonNull(appointmentType, "appointmentType");
            Objects.requireNonNull(startsAt, "startsAt");
            Objects.requireNonNull(duration, "duration");
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("duration must be positive");
            }
        }
    }

    record CalendarEntry(
            String externalReference,
            PublishSlotCommand slot,
            EntryStatus status,
            UUID bookingId,
            String cancellationReason,
            Instant updatedAt) {

        public CalendarEntry {
            Objects.requireNonNull(externalReference, "externalReference");
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }
}
