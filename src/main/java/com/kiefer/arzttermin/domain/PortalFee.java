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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "portal_fee", uniqueConstraints =
        @UniqueConstraint(name = "uk_portal_fee_booking", columnNames = "booking_id"))
public class PortalFee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_tier", nullable = false, length = 30)
    private ServiceTier serviceTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FeeStatus status;

    @Column(name = "provider_reference", nullable = false, unique = true, length = 120)
    private String providerReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PortalFee() {
    }

    public PortalFee(Booking booking, ServiceTier serviceTier, String providerReference, Instant createdAt) {
        this.booking = Objects.requireNonNull(booking, "booking");
        this.serviceTier = Objects.requireNonNull(serviceTier, "serviceTier");
        this.amountCents = serviceTier.getFeeCents();
        this.currency = "EUR";
        this.status = FeeStatus.OPEN;
        this.providerReference = requireText(providerReference, "providerReference");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.statusChangedAt = createdAt;
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public int getAmountCents() { return amountCents; }
    public String getCurrency() { return currency; }
    public ServiceTier getServiceTier() { return serviceTier; }
    public FeeStatus getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
    public long getVersion() { return version; }

    public void markPaid(Instant at) {
        if (status == FeeStatus.PAID) {
            return;
        }
        requireStatus(FeeStatus.OPEN);
        status = FeeStatus.PAID;
        paidAt = Objects.requireNonNull(at, "at");
        statusChangedAt = at;
    }

    public void voidOpenFee(Instant at) {
        if (status == FeeStatus.VOID) {
            return;
        }
        requireStatus(FeeStatus.OPEN);
        status = FeeStatus.VOID;
        statusChangedAt = Objects.requireNonNull(at, "at");
    }

    public void markRefundPending(Instant at) {
        if (status == FeeStatus.REFUND_PENDING) {
            return;
        }
        requireStatus(FeeStatus.PAID);
        status = FeeStatus.REFUND_PENDING;
        statusChangedAt = Objects.requireNonNull(at, "at");
    }

    public void markRefunded(Instant at) {
        if (status == FeeStatus.REFUNDED) {
            return;
        }
        requireStatus(FeeStatus.REFUND_PENDING);
        status = FeeStatus.REFUNDED;
        statusChangedAt = Objects.requireNonNull(at, "at");
    }

    private void requireStatus(FeeStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected fee status " + expected + " but was " + status);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
