package com.kiefer.arzttermin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;
import java.util.Objects;

@Embeddable
public class AvailabilityWindow {

    @Column(name = "window_start", nullable = false)
    private LocalDateTime start;

    @Column(name = "window_end", nullable = false)
    private LocalDateTime end;

    protected AvailabilityWindow() {
    }

    public AvailabilityWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Availability window end must be after its start");
        }
        this.start = start;
        this.end = end;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public boolean contains(LocalDateTime value) {
        return value != null && !value.isBefore(start) && value.isBefore(end);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AvailabilityWindow that
                && Objects.equals(start, that.start) && Objects.equals(end, that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}
