package com.kiefer.arzttermin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "booking", uniqueConstraints = {
        @UniqueConstraint(name = "uk_booking_offer", columnNames = "appointment_offer_id"),
        @UniqueConstraint(name = "uk_booking_slot", columnNames = "appointment_slot_id")
})
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_offer_id", nullable = false)
    private AppointmentOffer appointmentOffer;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_slot_id", nullable = false)
    private AppointmentSlot appointmentSlot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "search_request_id", nullable = false)
    private SearchRequest searchRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private BookingStatus status;

    @Column(name = "booked_at", nullable = false, updatable = false)
    private Instant bookedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Booking() {
    }

    public Booking(AppointmentOffer appointmentOffer, Instant bookedAt) {
        this.appointmentOffer = Objects.requireNonNull(appointmentOffer, "appointmentOffer");
        this.appointmentSlot = appointmentOffer.getAppointmentSlot();
        this.searchRequest = appointmentOffer.getSearchRequest();
        this.status = BookingStatus.CONFIRMED;
        this.bookedAt = Objects.requireNonNull(bookedAt, "bookedAt");
    }

    public UUID getId() { return id; }
    public AppointmentOffer getAppointmentOffer() { return appointmentOffer; }
    public AppointmentSlot getAppointmentSlot() { return appointmentSlot; }
    public SearchRequest getSearchRequest() { return searchRequest; }
    public BookingStatus getStatus() { return status; }
    public Instant getBookedAt() { return bookedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public long getVersion() { return version; }

    public void cancelByPractice(Instant at) {
        if (status == BookingStatus.CANCELLED_BY_PRACTICE) {
            return;
        }
        if (status != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only a confirmed booking can be cancelled");
        }
        status = BookingStatus.CANCELLED_BY_PRACTICE;
        cancelledAt = Objects.requireNonNull(at, "at");
    }
}
