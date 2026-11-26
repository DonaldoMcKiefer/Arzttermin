package com.kiefer.arzttermin.service;

import com.kiefer.arzttermin.calendar.PracticeCalendarGateway;
import com.kiefer.arzttermin.config.AppConfig;
import com.kiefer.arzttermin.domain.*;
import com.kiefer.arzttermin.location.PostalCodeGeoService;
import com.kiefer.arzttermin.payment.PaymentProvider;
import com.kiefer.arzttermin.repository.*;
import com.kiefer.arzttermin.service.dto.ContactCommand;
import com.kiefer.arzttermin.service.dto.PortalViews;
import com.kiefer.arzttermin.service.dto.SearchRequestCommand;
import com.kiefer.arzttermin.service.dto.SlotCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PortalWorkflowService {

    private static final Currency EUR = Currency.getInstance("EUR");

    private final CurrentActor currentActor;
    private final Clock clock;
    private final Duration offerValidity;
    private final PatientProfileRepository patientProfiles;
    private final PracticeRepository practices;
    private final SearchRequestRepository searchRequests;
    private final AppointmentSlotRepository slots;
    private final AppointmentOfferRepository offers;
    private final BookingRepository bookings;
    private final PortalFeeRepository fees;
    private final PracticeContactRepository contacts;
    private final PostalCodeGeoService geoService;
    private final PracticeCalendarGateway calendarGateway;
    private final PaymentProvider paymentProvider;

    public PortalWorkflowService(
            CurrentActor currentActor,
            Clock clock,
            @Value("${terminradar.offer-validity-minutes:30}") long offerValidityMinutes,
            PatientProfileRepository patientProfiles,
            PracticeRepository practices,
            SearchRequestRepository searchRequests,
            AppointmentSlotRepository slots,
            AppointmentOfferRepository offers,
            BookingRepository bookings,
            PortalFeeRepository fees,
            PracticeContactRepository contacts,
            PostalCodeGeoService geoService,
            PracticeCalendarGateway calendarGateway,
            PaymentProvider paymentProvider) {
        this.currentActor = currentActor;
        this.clock = clock;
        this.offerValidity = Duration.ofMinutes(offerValidityMinutes);
        this.patientProfiles = patientProfiles;
        this.practices = practices;
        this.searchRequests = searchRequests;
        this.slots = slots;
        this.offers = offers;
        this.bookings = bookings;
        this.fees = fees;
        this.contacts = contacts;
        this.geoService = geoService;
        this.calendarGateway = calendarGateway;
        this.paymentProvider = paymentProvider;
    }

    @PreAuthorize("hasRole('PATIENT')")
    @Transactional
    public UUID createSearchRequest(SearchRequestCommand command) {
        PatientProfile patient = patientProfiles.findByUsername(currentActor.username())
                .orElseThrow(() -> new PortalException("Für dieses Demokonto fehlt das Patientenprofil."));
        String effectivePostalCode = command.postalCode().isBlank() ? patient.getPostalCode() : command.postalCode();
        String effectiveCity = command.city().isBlank() ? patient.getCity() : command.city();
        PostalCodeGeoService.GeoPoint point = geoService.resolve(effectivePostalCode)
                .orElse(new PostalCodeGeoService.GeoPoint(0, 0));
        SearchRequest request = new SearchRequest(
                patient, command.specialty(), command.appointmentType(), effectiveCity, effectivePostalCode,
                point.latitude(), point.longitude(), command.radiusKm(), command.availabilityWindows(),
                command.shortNotice(), command.newPatient(), command.billingType(), command.serviceTier(),
                command.neededBy(), Instant.now(clock));
        return searchRequests.save(request).getId();
    }

    @PreAuthorize("hasRole('PATIENT')")
    @Transactional(readOnly = true)
    public List<PortalViews.PatientRequestView> patientRequests() {
        return searchRequests.findByPatient_UsernameOrderByCreatedAtDesc(currentActor.username()).stream()
                .map(this::toPatientRequestView).toList();
    }

    @PreAuthorize("hasRole('PATIENT')")
    @Transactional(readOnly = true)
    public List<PortalViews.PatientOfferView> patientOffers() {
        return offers.findBySearchRequest_Patient_UsernameOrderByOfferedAtDesc(currentActor.username()).stream()
                .map(this::toPatientOfferView).toList();
    }

    @PreAuthorize("hasRole('PATIENT')")
    @Transactional(readOnly = true)
    public List<PortalViews.PatientBookingView> patientBookings() {
        return bookings.findBySearchRequest_Patient_UsernameOrderByBookedAtDesc(currentActor.username()).stream()
                .map(this::toPatientBookingView).toList();
    }

    @PreAuthorize("hasRole('PATIENT')")
    @Transactional(noRollbackFor = PortalException.class)
    public PortalViews.BookingReceipt acceptOffer(UUID offerId) {
        String username = currentActor.username();
        AppointmentOffer offer = offers.findByIdForUpdate(offerId)
                .orElseThrow(() -> new PortalException("Das Angebot wurde nicht gefunden."));
        assertPatientOwns(offer, username);
        if (offer.getStatus() == OfferStatus.ACCEPTED) {
            return existingReceipt(offer);
        }
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new PortalException("Dieses Angebot ist nicht mehr offen.");
        }

        Instant now = Instant.now(clock);
        AppointmentSlot slot = slots.findByIdForUpdate(offer.getAppointmentSlot().getId())
                .orElseThrow(() -> new PortalException("Der Termin wurde nicht gefunden."));
        SearchRequest request = searchRequests.findByIdForUpdate(offer.getSearchRequest().getId())
                .orElseThrow(() -> new PortalException("Die Suchanfrage wurde nicht gefunden."));
        if (offer.isExpiredAt(now)) {
            expireLockedOffer(offer, slot, request, now);
            throw new PortalException("Die Angebotsfrist ist abgelaufen. Die Praxis kann den Platz neu vergeben.");
        }
        if (slot.getStatus() != SlotStatus.OFFERED || request.getStatus() != SearchRequestStatus.OFFERED) {
            throw new PortalException("Der Termin ist nicht mehr verfügbar.");
        }

        offer.accept(now);
        slot.markBooked();
        request.markBooked();
        Booking booking = bookings.saveAndFlush(new Booking(offer, now));
        int amount = request.getServiceTier().getFeeCents();
        PaymentProvider.CaptureResult capture = paymentProvider.capture(new PaymentProvider.CaptureCommand(
                booking.getId(), amount, EUR, "BOOKING-" + booking.getId(),
                "Portalgebühr " + request.getServiceTier().getLabel()));
        PortalFee fee = new PortalFee(booking, request.getServiceTier(), capture.providerReference(), now);
        fee.markPaid(now);
        fees.saveAndFlush(fee);
        calendarGateway.markBooked(slot.getId(), booking.getId());
        return new PortalViews.BookingReceipt(booking.getId(), fee.getId(), amount, fee.getStatus());
    }

    @PreAuthorize("hasRole('PATIENT')")
    @Transactional
    public void declineOffer(UUID offerId) {
        String username = currentActor.username();
        AppointmentOffer offer = offers.findByIdForUpdate(offerId)
                .orElseThrow(() -> new PortalException("Das Angebot wurde nicht gefunden."));
        assertPatientOwns(offer, username);
        if (offer.getStatus() == OfferStatus.DECLINED) {
            return;
        }
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new PortalException("Dieses Angebot ist nicht mehr offen.");
        }
        AppointmentSlot slot = slots.findByIdForUpdate(offer.getAppointmentSlot().getId())
                .orElseThrow(() -> new PortalException("Der Termin wurde nicht gefunden."));
        SearchRequest request = searchRequests.findByIdForUpdate(offer.getSearchRequest().getId())
                .orElseThrow(() -> new PortalException("Die Suchanfrage wurde nicht gefunden."));
        offer.decline(Instant.now(clock));
        slot.releaseAfterUnsuccessfulOffer();
        request.reopenAfterUnsuccessfulOffer();
    }

    @PreAuthorize("hasRole('PRACTICE')")
    @Transactional
    public UUID createSlot(SlotCommand command) {
        Practice practice = practices.findByUsername(currentActor.username())
                .orElseThrow(() -> new PortalException("Für dieses Demokonto fehlt die Praxis."));
        if (!command.startsAt().isAfter(LocalDateTime.now(clock))) {
            throw new PortalException("Ein freier Termin muss in der Zukunft liegen.");
        }
        String treatmentCost = command.treatmentCostInformation().isBlank()
                ? defaultTreatmentCostText(command.acceptedBillingTypes()) : command.treatmentCostInformation();
        AppointmentSlot slot = slots.saveAndFlush(new AppointmentSlot(
                practice, command.specialty(), command.appointmentType(), command.startsAt(),
                command.durationMinutes(), command.acceptedBillingTypes(), command.acceptsNewPatients(),
                CalendarSource.LOCAL_DEMO, "LOCAL-DEMO-" + UUID.randomUUID(), treatmentCost));
        calendarGateway.publish(new PracticeCalendarGateway.PublishSlotCommand(
                slot.getId(), practice.getId(), slot.getSpecialty(), slot.getAppointmentType(),
                slot.getStartAt(), Duration.ofMinutes(slot.getDurationMinutes())));
        return slot.getId();
    }

    @PreAuthorize("hasRole('PRACTICE')")
    @Transactional(readOnly = true)
    public List<PortalViews.PracticeSlotView> practiceSlots() {
        return slots.findByPractice_UsernameOrderByStartAtDesc(currentActor.username()).stream()
                .map(this::toPracticeSlotView).toList();
    }

    @PreAuthorize("hasRole('PRACTICE')")
    @Transactional(readOnly = true)
    public List<PortalViews.PracticeMatchView> matchingRequests(UUID slotId) {
        AppointmentSlot slot = slots.findByIdAndPractice_Username(slotId, currentActor.username())
                .orElseThrow(() -> new PortalException("Der Termin wurde nicht gefunden oder gehört zu einer anderen Praxis."));
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            return List.of();
        }
        return searchRequests.findByStatusOrderByCreatedAtAsc(SearchRequestStatus.OPEN).stream()
                .filter(request -> matches(slot, request)).map(request -> toPracticeMatchView(slot, request)).toList();
    }

    @PreAuthorize("hasRole('PRACTICE')")
    @Transactional
    public UUID offerSlot(UUID slotId, UUID requestId) {
        String username = currentActor.username();
        AppointmentSlot slot = slots.findByIdForUpdate(slotId)
                .orElseThrow(() -> new PortalException("Der Termin wurde nicht gefunden."));
        if (!slot.getPractice().getUsername().equals(username)) {
            throw new PortalException("Dieser Termin gehört zu einer anderen Praxis.");
        }
        SearchRequest request = searchRequests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new PortalException("Die Suchanfrage wurde nicht gefunden."));
        if (slot.getStatus() != SlotStatus.AVAILABLE || request.getStatus() != SearchRequestStatus.OPEN) {
            throw new PortalException("Termin oder Suchanfrage ist inzwischen nicht mehr frei.");
        }
        if (!matches(slot, request)) {
            throw new PortalException("Die Suchanfrage passt nicht zu diesem Termin.");
        }
        Instant now = Instant.now(clock);
        AppointmentOffer offer = new AppointmentOffer(slot, request, now, now.plus(offerValidity));
        slot.markOffered();
        request.markOffered();
        return offers.saveAndFlush(offer).getId();
    }

    @PreAuthorize("hasRole('PRACTICE')")
    @Transactional
    public void cancelBooking(UUID bookingId) {
        String username = currentActor.username();
        Booking booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new PortalException("Die Buchung wurde nicht gefunden."));
        if (!booking.getAppointmentSlot().getPractice().getUsername().equals(username)) {
            throw new PortalException("Diese Buchung gehört zu einer anderen Praxis.");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED_BY_PRACTICE) {
            return;
        }
        AppointmentSlot slot = slots.findByIdForUpdate(booking.getAppointmentSlot().getId())
                .orElseThrow(() -> new PortalException("Der Termin wurde nicht gefunden."));
        SearchRequest request = searchRequests.findByIdForUpdate(booking.getSearchRequest().getId())
                .orElseThrow(() -> new PortalException("Die Suchanfrage wurde nicht gefunden."));
        PortalFee fee = fees.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new PortalException("Die zugehörige Portalgebühr wurde nicht gefunden."));
        Instant now = Instant.now(clock);
        booking.cancelByPractice(now);
        slot.cancel();
        request.reopenAfterPracticeCancellation();
        if (fee.getStatus() == FeeStatus.OPEN) {
            fee.voidOpenFee(now);
        } else if (fee.getStatus() == FeeStatus.PAID) {
            paymentProvider.requestRefund(new PaymentProvider.RefundCommand(
                    booking.getId(), fee.getProviderReference(), fee.getAmountCents(), EUR,
                    "REFUND-" + booking.getId(), "Absage durch die Praxis"));
            fee.markRefundPending(now);
        }
        try {
            calendarGateway.cancel(slot.getId(), "Absage durch die Praxis");
        } catch (IllegalArgumentException ignoredAfterRestart) {
            // The local demo calendar is process-local; the database remains authoritative.
        }
    }

    @PreAuthorize("hasRole('STAFF')")
    @Transactional(readOnly = true)
    public List<PortalViews.StaffRequestView> personalSearchWorklist() {
        return searchRequests.findByServiceTierOrderByCreatedAtAsc(ServiceTier.PERSONAL_25).stream()
                .map(this::toStaffRequestView).toList();
    }

    @PreAuthorize("hasRole('STAFF')")
    @Transactional
    public UUID addPracticeContact(UUID requestId, ContactCommand command) {
        SearchRequest request = searchRequests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new PortalException("Die Suchanfrage wurde nicht gefunden."));
        if (request.getServiceTier() != ServiceTier.PERSONAL_25) {
            throw new PortalException("Praxiskontakte gehören ausschließlich zur persönlichen Suche.");
        }
        Instant contactedAt = command.contactedAt().atZone(AppConfig.BUSINESS_TIME_ZONE).toInstant();
        if (contactedAt.isAfter(Instant.now(clock))) {
            throw new PortalException("Der Kontaktzeitpunkt darf nicht in der Zukunft liegen.");
        }
        PracticeContact contact = new PracticeContact(
                request, null, command.practiceName(), contactedAt,
                command.channel(), command.response(), command.note(), currentActor.username());
        if (request.getPersonalSearchStatus() == PersonalSearchStatus.NEW) {
            request.setPersonalSearchStatus(PersonalSearchStatus.IN_PROGRESS);
        }
        return contacts.save(contact).getId();
    }

    private PortalViews.BookingReceipt existingReceipt(AppointmentOffer offer) {
        Booking booking = bookings.findByAppointmentOffer_Id(offer.getId())
                .orElseThrow(() -> new PortalException("Die bestätigte Buchung ist noch nicht verfügbar."));
        PortalFee fee = fees.findByBooking_Id(booking.getId())
                .orElseThrow(() -> new PortalException("Die bestätigte Portalgebühr ist noch nicht verfügbar."));
        return new PortalViews.BookingReceipt(booking.getId(), fee.getId(), fee.getAmountCents(), fee.getStatus());
    }

    private void assertPatientOwns(AppointmentOffer offer, String username) {
        if (!offer.getSearchRequest().getPatient().getUsername().equals(username)) {
            throw new PortalException("Dieses Angebot gehört zu einem anderen Patientenkonto.");
        }
    }

    private void expireLockedOffer(AppointmentOffer offer, AppointmentSlot slot,
                                   SearchRequest request, Instant now) {
        offer.expire(now);
        if (slot.getStatus() == SlotStatus.OFFERED) {
            slot.releaseAfterUnsuccessfulOffer();
        }
        if (request.getStatus() == SearchRequestStatus.OFFERED) {
            request.reopenAfterUnsuccessfulOffer();
        }
    }

    private boolean matches(AppointmentSlot slot, SearchRequest request) {
        if (slot.getStatus() != SlotStatus.AVAILABLE || request.getStatus() != SearchRequestStatus.OPEN) return false;
        if (slot.getSpecialty() != request.getSpecialty()
                || slot.getAppointmentType() != request.getAppointmentType()
                || !slot.getAcceptedBillingTypes().contains(request.getBillingType())) return false;
        if (request.isNewPatient() && !slot.isAcceptsNewPatients()) return false;
        if (!geoService.isWithinRadius(request.getPostalCode(), slot.getPractice().getPostalCode(), request.getRadiusKm())) return false;
        LocalDateTime slotEnd = slot.getStartAt().plusMinutes(slot.getDurationMinutes());
        boolean withinWindow = request.getAvailabilityWindows().stream()
                .anyMatch(window -> !slot.getStartAt().isBefore(window.getStart()) && !slotEnd.isAfter(window.getEnd()));
        if (!withinWindow) return false;
        if (request.getNeededBy() != null && slot.getStartAt().toLocalDate().isAfter(request.getNeededBy())) return false;
        long hoursUntilSlot = ChronoUnit.HOURS.between(LocalDateTime.now(clock), slot.getStartAt());
        return hoursUntilSlot >= 48 || request.isShortNotice();
    }

    private PortalViews.PatientRequestView toPatientRequestView(SearchRequest request) {
        return new PortalViews.PatientRequestView(
                request.getId(), request.getSpecialty(), request.getAppointmentType(), request.getLocationText(),
                request.getPostalCode(), request.getRadiusKm(), List.copyOf(request.getAvailabilityWindows()),
                request.isShortNotice(), request.isNewPatient(), request.getBillingType(), request.getServiceTier(),
                request.getNeededBy(), request.getStatus(), local(request.getCreatedAt()));
    }

    private PortalViews.PatientOfferView toPatientOfferView(AppointmentOffer offer) {
        AppointmentSlot slot = offer.getAppointmentSlot();
        SearchRequest request = offer.getSearchRequest();
        return new PortalViews.PatientOfferView(
                offer.getId(), request.getId(), slot.getPractice().getName(), slot.getPractice().getFullAddress(),
                slot.getSpecialty(), slot.getAppointmentType(), slot.getStartAt(), slot.getDurationMinutes(),
                request.getBillingType(), request.getServiceTier().getFeeCents(),
                slot.getTreatmentCostInformation(), local(offer.getExpiresAt()), offer.getStatus());
    }

    private PortalViews.PatientBookingView toPatientBookingView(Booking booking) {
        PortalFee fee = fees.findByBooking_Id(booking.getId())
                .orElseThrow(() -> new PortalException("Zur Buchung fehlt die Portalgebühr."));
        AppointmentSlot slot = booking.getAppointmentSlot();
        return new PortalViews.PatientBookingView(
                booking.getId(), booking.getSearchRequest().getId(), booking.getAppointmentOffer().getId(),
                slot.getPractice().getName(), slot.getPractice().getFullAddress(), slot.getAppointmentType(),
                slot.getStartAt(), slot.getDurationMinutes(), booking.getStatus(), fee.getAmountCents(),
                fee.getStatus(), fee.getProviderReference());
    }

    private PortalViews.PracticeSlotView toPracticeSlotView(AppointmentSlot slot) {
        AppointmentOffer activeOffer = offers.findByAppointmentSlot_IdAndStatus(slot.getId(), OfferStatus.PENDING).orElse(null);
        Booking booking = bookings.findByAppointmentSlot_Id(slot.getId()).orElse(null);
        return new PortalViews.PracticeSlotView(
                slot.getId(), slot.getSpecialty(), slot.getAppointmentType(), slot.getStartAt(),
                slot.getDurationMinutes(), Set.copyOf(slot.getAcceptedBillingTypes()), slot.isAcceptsNewPatients(),
                slot.getTreatmentCostInformation(), slot.getStatus(), calendarGateway.sourceLabel(),
                activeOffer == null ? null : activeOffer.getId(), activeOffer == null ? null : activeOffer.getStatus(),
                booking == null ? null : booking.getId(), booking == null ? null : booking.getStatus());
    }

    private PortalViews.PracticeMatchView toPracticeMatchView(AppointmentSlot slot, SearchRequest request) {
        double distance = geoService.distanceInKilometers(request.getPostalCode(), slot.getPractice().getPostalCode());
        return new PortalViews.PracticeMatchView(
                request.getId(), request.getPatient().getDisplayName(), local(request.getCreatedAt()),
                request.getSpecialty(), request.getAppointmentType(), request.getPostalCode() + " " + request.getLocationText(),
                roundDistance(distance), List.copyOf(request.getAvailabilityWindows()), request.isShortNotice(),
                request.isNewPatient(), request.getBillingType());
    }

    private PortalViews.StaffRequestView toStaffRequestView(SearchRequest request) {
        List<PortalViews.ContactView> contactViews = contacts.findBySearchRequest_IdOrderByContactedAtDesc(request.getId()).stream()
                .map(contact -> new PortalViews.ContactView(
                        contact.getId(), contact.getPracticeName(), local(contact.getContactedAt()),
                        contact.getChannel(), contact.getResponse(), contact.getNotes(), contact.getCreatedByUsername()))
                .toList();
        return new PortalViews.StaffRequestView(
                request.getId(), request.getPatient().getDisplayName(), request.getSpecialty(), request.getAppointmentType(),
                request.getPostalCode() + " " + request.getLocationText(), request.getRadiusKm(), request.getBillingType(),
                request.getNeededBy(), request.getPersonalSearchStatus(), request.getStatus(), local(request.getCreatedAt()), contactViews);
    }

    private LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, AppConfig.BUSINESS_TIME_ZONE);
    }

    private double roundDistance(double value) {
        return Double.isFinite(value) ? Math.round(value * 10.0) / 10.0 : value;
    }

    private String defaultTreatmentCostText(Set<BillingType> acceptedTypes) {
        if (acceptedTypes.equals(Set.of(BillingType.STATUTORY))) {
            return "Kassenleistung; mögliche Zusatzleistungen werden von der Praxis separat erläutert.";
        }
        if (acceptedTypes.contains(BillingType.SELF_PAY)) {
            return "Behandlungskosten nach GOÄ; die Praxis informiert separat über den voraussichtlichen Betrag.";
        }
        return "Behandlungskosten nach Versicherungsstatus; nicht in der Portalgebühr enthalten.";
    }
}
