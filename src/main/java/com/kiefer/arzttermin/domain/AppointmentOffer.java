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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "appointment_offer")
public class AppointmentOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_slot_id", nullable = false)
    private AppointmentSlot appointmentSlot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "search_request_id", nullable = false)
    private SearchRequest searchRequest;

    @Column(name = "offered_at", nullable = false, updatable = false)
    private Instant offeredAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OfferStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AppointmentOffer() {
    }

    public AppointmentOffer(AppointmentSlot appointmentSlot, SearchRequest searchRequest,
                            Instant offeredAt, Instant expiresAt) {
        this.appointmentSlot = Objects.requireNonNull(appointmentSlot, "appointmentSlot");
        this.searchRequest = Objects.requireNonNull(searchRequest, "searchRequest");
        this.offeredAt = Objects.requireNonNull(offeredAt, "offeredAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(offeredAt)) {
            throw new IllegalArgumentException("Offer expiry must be after offer time");
        }
        this.status = OfferStatus.PENDING;
    }

    public UUID getId() { return id; }
    public AppointmentSlot getAppointmentSlot() { return appointmentSlot; }
    public SearchRequest getSearchRequest() { return searchRequest; }
    public Instant getOfferedAt() { return offeredAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public OfferStatus getStatus() { return status; }
    public long getVersion() { return version; }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(Objects.requireNonNull(now, "now"));
    }

    public void accept(Instant at) {
        transitionFromPending(OfferStatus.ACCEPTED, at);
    }

    public void decline(Instant at) {
        transitionFromPending(OfferStatus.DECLINED, at);
    }

    public void expire(Instant at) {
        transitionFromPending(OfferStatus.EXPIRED, at);
    }

    public void withdraw(Instant at) {
        transitionFromPending(OfferStatus.WITHDRAWN, at);
    }

    private void transitionFromPending(OfferStatus target, Instant at) {
        if (status != OfferStatus.PENDING) {
            throw new IllegalStateException("Only a pending offer can transition to " + target);
        }
        status = target;
        respondedAt = Objects.requireNonNull(at, "at");
    }
}
