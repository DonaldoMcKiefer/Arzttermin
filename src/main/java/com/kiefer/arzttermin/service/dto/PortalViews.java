package com.kiefer.arzttermin.service.dto;

import com.kiefer.arzttermin.domain.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PortalViews {

    private PortalViews() {
    }

    public record PatientRequestView(
            UUID id, Specialty specialty, AppointmentType appointmentType, String city, String postalCode,
            int radiusKm, List<AvailabilityWindow> windows, boolean shortNotice, boolean newPatient,
            BillingType billingType, ServiceTier serviceTier, LocalDate neededBy,
            SearchRequestStatus status, LocalDateTime createdAt) {
    }

    public record PatientOfferView(
            UUID id, UUID requestId, String practiceName, String practiceAddress,
            Specialty specialty, AppointmentType appointmentType, LocalDateTime startsAt, int durationMinutes,
            BillingType billingType, int portalFeeCents, String treatmentCostInformation,
            LocalDateTime expiresAt, OfferStatus status) {
    }

    public record PatientBookingView(
            UUID id, UUID requestId, UUID offerId, String practiceName, String practiceAddress,
            AppointmentType appointmentType, LocalDateTime startsAt, int durationMinutes,
            BookingStatus status, int portalFeeCents, FeeStatus feeStatus, String paymentReference) {
    }

    public record PracticeSlotView(
            UUID id, Specialty specialty, AppointmentType appointmentType, LocalDateTime startsAt,
            int durationMinutes, Set<BillingType> acceptedBillingTypes, boolean acceptsNewPatients,
            String treatmentCostInformation, SlotStatus status, String calendarSourceLabel,
            UUID activeOfferId, OfferStatus activeOfferStatus, UUID bookingId, BookingStatus bookingStatus) {
    }

    /** Intentionally contains neither service tier nor portal fee. */
    public record PracticeMatchView(
            UUID requestId, String patientDisplayName, LocalDateTime createdAt,
            Specialty specialty, AppointmentType appointmentType, String location,
            double distanceKm, List<AvailabilityWindow> windows, boolean shortNotice,
            boolean newPatient, BillingType billingType) {
    }

    public record StaffRequestView(
            UUID requestId, String patientDisplayName, Specialty specialty, AppointmentType appointmentType,
            String location, int radiusKm, BillingType billingType, LocalDate neededBy,
            PersonalSearchStatus personalSearchStatus, SearchRequestStatus requestStatus,
            LocalDateTime createdAt, List<ContactView> contacts) {
    }

    public record ContactView(
            UUID id, String practiceName, LocalDateTime contactedAt, ContactChannel channel,
            ContactResponse response, String note, String recordedBy) {
    }

    public record BookingReceipt(UUID bookingId, UUID feeId, int amountCents, FeeStatus feeStatus) {
    }
}
