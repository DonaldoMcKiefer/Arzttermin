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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "search_request")
public class SearchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientProfile patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "specialty", nullable = false, length = 50)
    private Specialty specialty;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_type", nullable = false, length = 60)
    private AppointmentType appointmentType;

    @Column(name = "location_text", nullable = false, length = 160)
    private String locationText;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "radius_km", nullable = false)
    private int radiusKm;

    @ElementCollection
    @CollectionTable(name = "search_request_window", joinColumns = @JoinColumn(name = "search_request_id"))
    @OrderColumn(name = "window_order")
    private List<AvailabilityWindow> availabilityWindows = new ArrayList<>();

    @Column(name = "short_notice", nullable = false)
    private boolean shortNotice;

    @Column(name = "new_patient", nullable = false)
    private boolean newPatient;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 30)
    private BillingType billingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_tier", nullable = false, length = 30)
    private ServiceTier serviceTier;

    @Column(name = "needed_by")
    private LocalDate neededBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SearchRequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "personal_search_status", nullable = false, length = 40)
    private PersonalSearchStatus personalSearchStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SearchRequest() {
    }

    public SearchRequest(PatientProfile patient, Specialty specialty, AppointmentType appointmentType,
                         String locationText, String postalCode, double latitude, double longitude,
                         int radiusKm, List<AvailabilityWindow> availabilityWindows,
                         boolean shortNotice, boolean newPatient, BillingType billingType,
                         ServiceTier serviceTier, LocalDate neededBy) {
        this(patient, specialty, appointmentType, locationText, postalCode, latitude, longitude,
                radiusKm, availabilityWindows, shortNotice, newPatient, billingType, serviceTier,
                neededBy, Instant.now());
    }

    public SearchRequest(PatientProfile patient, Specialty specialty, AppointmentType appointmentType,
                         String locationText, String postalCode, double latitude, double longitude,
                         int radiusKm, List<AvailabilityWindow> availabilityWindows,
                         boolean shortNotice, boolean newPatient, BillingType billingType,
                         ServiceTier serviceTier, LocalDate neededBy, Instant createdAt) {
        this.patient = Objects.requireNonNull(patient, "patient");
        this.specialty = Objects.requireNonNull(specialty, "specialty");
        this.appointmentType = Objects.requireNonNull(appointmentType, "appointmentType");
        this.locationText = requireText(locationText, "locationText");
        this.postalCode = requireText(postalCode, "postalCode");
        this.latitude = latitude;
        this.longitude = longitude;
        if (radiusKm <= 0 || radiusKm > 250) {
            throw new IllegalArgumentException("radiusKm must be between 1 and 250");
        }
        this.radiusKm = radiusKm;
        replaceAvailabilityWindows(availabilityWindows);
        this.shortNotice = shortNotice;
        this.newPatient = newPatient;
        this.billingType = Objects.requireNonNull(billingType, "billingType");
        this.serviceTier = Objects.requireNonNull(serviceTier, "serviceTier");
        this.neededBy = neededBy;
        this.status = SearchRequestStatus.OPEN;
        this.personalSearchStatus = serviceTier == ServiceTier.PERSONAL_25
                ? PersonalSearchStatus.NEW : PersonalSearchStatus.NOT_APPLICABLE;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID getId() { return id; }
    public PatientProfile getPatient() { return patient; }
    public Specialty getSpecialty() { return specialty; }
    public AppointmentType getAppointmentType() { return appointmentType; }
    public String getLocationText() { return locationText; }
    public String getPostalCode() { return postalCode; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public int getRadiusKm() { return radiusKm; }
    public List<AvailabilityWindow> getAvailabilityWindows() { return Collections.unmodifiableList(availabilityWindows); }
    public boolean isShortNotice() { return shortNotice; }
    public boolean isNewPatient() { return newPatient; }
    public BillingType getBillingType() { return billingType; }
    public ServiceTier getServiceTier() { return serviceTier; }
    public LocalDate getNeededBy() { return neededBy; }
    public SearchRequestStatus getStatus() { return status; }
    public PersonalSearchStatus getPersonalSearchStatus() { return personalSearchStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }

    public void markOffered() {
        requireStatus(SearchRequestStatus.OPEN);
        status = SearchRequestStatus.OFFERED;
    }

    public void reopenAfterUnsuccessfulOffer() {
        requireStatus(SearchRequestStatus.OFFERED);
        status = SearchRequestStatus.OPEN;
    }

    public void markBooked() {
        requireStatus(SearchRequestStatus.OFFERED);
        status = SearchRequestStatus.BOOKED;
        if (serviceTier == ServiceTier.PERSONAL_25) {
            personalSearchStatus = PersonalSearchStatus.COMPLETED;
        }
    }

    public void reopenAfterPracticeCancellation() {
        requireStatus(SearchRequestStatus.BOOKED);
        status = SearchRequestStatus.OPEN;
        if (serviceTier == ServiceTier.PERSONAL_25) {
            personalSearchStatus = PersonalSearchStatus.IN_PROGRESS;
        }
    }

    public void close() {
        if (status == SearchRequestStatus.BOOKED) {
            throw new IllegalStateException("A booked request cannot be closed directly");
        }
        status = SearchRequestStatus.CLOSED;
    }

    public void setPersonalSearchStatus(PersonalSearchStatus personalSearchStatus) {
        if (serviceTier != ServiceTier.PERSONAL_25
                && personalSearchStatus != PersonalSearchStatus.NOT_APPLICABLE) {
            throw new IllegalStateException("Work status is only available for personal searches");
        }
        this.personalSearchStatus = Objects.requireNonNull(personalSearchStatus, "personalSearchStatus");
    }

    public void replaceAvailabilityWindows(List<AvailabilityWindow> windows) {
        if (windows == null || windows.isEmpty()) {
            throw new IllegalArgumentException("At least one availability window is required");
        }
        availabilityWindows.clear();
        availabilityWindows.addAll(windows.stream().map(value -> Objects.requireNonNull(value, "window")).toList());
    }

    private void requireStatus(SearchRequestStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected request status " + expected + " but was " + status);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
