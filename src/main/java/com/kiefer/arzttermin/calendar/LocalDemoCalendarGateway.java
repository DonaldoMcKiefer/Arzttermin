package com.kiefer.arzttermin.calendar;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * Thread-safe, process-local calendar used by the pilot.
 *
 * <p>No practice software is contacted and entries intentionally disappear
 * when the application is restarted. The database remains the source of truth
 * for the actual appointment and booking.</p>
 */
@Component
public class LocalDemoCalendarGateway implements PracticeCalendarGateway {

    public static final String SOURCE_LABEL =
            "Lokaler Demo-Kalender – keine Praxissoftware verbunden";

    private final Clock clock;
    private final ConcurrentMap<UUID, CalendarEntry> entries = new ConcurrentHashMap<>();

    public LocalDemoCalendarGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String sourceLabel() {
        return SOURCE_LABEL;
    }

    @Override
    public CalendarEntry publish(PublishSlotCommand command) {
        Objects.requireNonNull(command, "command");
        CalendarEntry published = new CalendarEntry(
                "LOCAL-DEMO-" + command.slotId(),
                command,
                EntryStatus.PUBLISHED,
                null,
                null,
                Instant.now(clock));

        CalendarEntry existing = entries.putIfAbsent(command.slotId(), published);
        if (existing == null) {
            return published;
        }
        if (!existing.slot().equals(command)) {
            throw new IllegalStateException(
                    "Calendar slot id is already used with different data: " + command.slotId());
        }
        return existing;
    }

    @Override
    public CalendarEntry markBooked(UUID slotId, UUID bookingId) {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(bookingId, "bookingId");
        return entries.compute(slotId, (ignored, current) -> {
            requirePublishedEntry(slotId, current);
            if (current.status() == EntryStatus.CANCELLED) {
                throw new IllegalStateException("Cancelled calendar entry cannot be booked: " + slotId);
            }
            if (current.status() == EntryStatus.BOOKED) {
                if (bookingId.equals(current.bookingId())) {
                    return current;
                }
                throw new IllegalStateException("Calendar entry is already booked: " + slotId);
            }
            return new CalendarEntry(
                    current.externalReference(),
                    current.slot(),
                    EntryStatus.BOOKED,
                    bookingId,
                    null,
                    Instant.now(clock));
        });
    }

    @Override
    public CalendarEntry cancel(UUID slotId, String reason) {
        Objects.requireNonNull(slotId, "slotId");
        String normalizedReason = normalizeReason(reason);
        return entries.compute(slotId, (ignored, current) -> {
            requirePublishedEntry(slotId, current);
            if (current.status() == EntryStatus.CANCELLED) {
                return current;
            }
            return new CalendarEntry(
                    current.externalReference(),
                    current.slot(),
                    EntryStatus.CANCELLED,
                    current.bookingId(),
                    normalizedReason,
                    Instant.now(clock));
        });
    }

    @Override
    public Optional<CalendarEntry> findBySlotId(UUID slotId) {
        Objects.requireNonNull(slotId, "slotId");
        return Optional.ofNullable(entries.get(slotId));
    }

    private static void requirePublishedEntry(UUID slotId, CalendarEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Unknown local calendar slot: " + slotId);
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Ohne Angabe";
        }
        return reason.strip();
    }
}
