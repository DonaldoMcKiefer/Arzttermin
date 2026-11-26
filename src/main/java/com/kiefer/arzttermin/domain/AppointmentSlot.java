package com.kiefer.arzttermin.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "appointment_slot")
public class AppointmentSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practice_id", nullable = false)
    private Practice practice;

    @Enumerated(EnumType.STRING)
    @Column(name = "specialty", nullable = false, length = 50)
    private Specialty specialty;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_type", nullable = false, length = 60)
    private AppointmentType appointmentType;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @ElementCollection
    @CollectionTable(name = "appointment_slot_billing", joinColumns = @JoinColumn(name = "appointment_slot_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 30)
    private Set<BillingType> acceptedBillingTypes = new HashSet<>();

    @Column(name = "accepts_new_patients", nullable = false)
    private boolean acceptsNewPatients;

    @Enumerated(EnumType.STRING)
    @Column(name = "calendar_source", nullable = false, length = 40)
    private CalendarSource calendarSource;

    @Column(name = "external_calendar_reference", nullable = false, length = 120)
    private String externalCalendarReference;

    @Column(name = "treatment_cost_information", nullable = false, length = 300)
    private String treatmentCostInformation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SlotStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AppointmentSlot() {
    }

    public AppointmentSlot(Practice practice, Specialty specialty, AppointmentType appointmentType,
                           LocalDateTime startAt, int durationMinutes,
                           Set<BillingType> acceptedBillingTypes, boolean acceptsNewPatients,
                           CalendarSource calendarSource, String externalCalendarReference,
                           String treatmentCostInformation) {
        this(practice, specialty, appointmentType, startAt, durationMinutes, acceptedBillingTypes,
                acceptsNewPatients, calendarSource, externalCalendarReference,
                treatmentCostInformation, Instant.now());
    }

    public AppointmentSlot(Practice practice, Specialty specialty, AppointmentType appointmentType,
                           LocalDateTime startAt, int durationMinutes,
                           Set<BillingType> acceptedBillingTypes, boolean acceptsNewPatients,
                           CalendarSource calendarSource, String externalCalendarReference,
                           String treatmentCostInformation, Instant createdAt) {
        this.practice = Objects.requireNonNull(practice, "practice");
        this.specialty = Objects.requireNonNull(specialty, "specialty");
        this.appointmentType = Objects.requireNonNull(appointmentType, "appointmentType");
        this.startAt = Objects.requireNonNull(startAt, "startAt");
        if (durationMinutes <= 0 || durationMinutes > 480) {
            throw new IllegalArgumentException("durationMinutes must be between 1 and 480");
        }
        this.durationMinutes = durationMinutes;
        if (acceptedBillingTypes == null || acceptedBillingTypes.isEmpty()) {
            throw new IllegalArgumentException("At least one billing type is required");
        }
        this.acceptedBillingTypes.addAll(acceptedBillingTypes);
        this.acceptsNewPatients = acceptsNewPatients;
        this.calendarSource = Objects.requireNonNull(calendarSource, "calendarSource");
        this.externalCalendarReference = requireText(externalCalendarReference, "externalCalendarReference");
        this.treatmentCostInformation = requireText(treatmentCostInformation, "treatmentCostInformation");
        this.status = SlotStatus.AVAILABLE;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID getId() { return id; }
    public Practice getPractice() { return practice; }
    public Specialty getSpecialty() { return specialty; }
    public AppointmentType getAppointmentType() { return appointmentType; }
    public LocalDateTime getStartAt() { return startAt; }
    public int getDurationMinutes() { return durationMinutes; }
    public Set<BillingType> getAcceptedBillingTypes() { return Collections.unmodifiableSet(acceptedBillingTypes); }
    public boolean isAcceptsNewPatients() { return acceptsNewPatients; }
    public CalendarSource getCalendarSource() { return calendarSource; }
    public String getExternalCalendarReference() { return externalCalendarReference; }
    public String getTreatmentCostInformation() { return treatmentCostInformation; }
    public SlotStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }

    public void markOffered() {
        requireStatus(SlotStatus.AVAILABLE);
        status = SlotStatus.OFFERED;
    }

    public void releaseAfterUnsuccessfulOffer() {
        requireStatus(SlotStatus.OFFERED);
        status = SlotStatus.AVAILABLE;
    }

    public void markBooked() {
        requireStatus(SlotStatus.OFFERED);
        status = SlotStatus.BOOKED;
    }

    public void cancel() {
        if (status == SlotStatus.CANCELLED) {
            return;
        }
        status = SlotStatus.CANCELLED;
    }

    private void requireStatus(SlotStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected slot status " + expected + " but was " + status);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
