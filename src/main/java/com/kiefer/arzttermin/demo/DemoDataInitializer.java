package com.kiefer.arzttermin.demo;

import com.kiefer.arzttermin.calendar.PracticeCalendarGateway;
import com.kiefer.arzttermin.domain.AppointmentSlot;
import com.kiefer.arzttermin.domain.AppointmentType;
import com.kiefer.arzttermin.domain.AvailabilityWindow;
import com.kiefer.arzttermin.domain.BillingType;
import com.kiefer.arzttermin.domain.CalendarSource;
import com.kiefer.arzttermin.domain.PatientProfile;
import com.kiefer.arzttermin.domain.Practice;
import com.kiefer.arzttermin.domain.SearchRequest;
import com.kiefer.arzttermin.domain.ServiceTier;
import com.kiefer.arzttermin.domain.SlotStatus;
import com.kiefer.arzttermin.domain.Specialty;
import com.kiefer.arzttermin.repository.AppointmentSlotRepository;
import com.kiefer.arzttermin.repository.BookingRepository;
import com.kiefer.arzttermin.repository.PatientProfileRepository;
import com.kiefer.arzttermin.repository.PracticeRepository;
import com.kiefer.arzttermin.repository.SearchRequestRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(
        prefix = "terminradar",
        name = "demo-data-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private static final String PRACTICE_USERNAME = "praxis.haut";

    private final Clock clock;
    private final PatientProfileRepository patientProfiles;
    private final PracticeRepository practices;
    private final SearchRequestRepository searchRequests;
    private final AppointmentSlotRepository appointmentSlots;
    private final BookingRepository bookings;
    private final PracticeCalendarGateway calendarGateway;

    public DemoDataInitializer(
            Clock clock,
            PatientProfileRepository patientProfiles,
            PracticeRepository practices,
            SearchRequestRepository searchRequests,
            AppointmentSlotRepository appointmentSlots,
            BookingRepository bookings,
            PracticeCalendarGateway calendarGateway) {
        this.clock = clock;
        this.patientProfiles = patientProfiles;
        this.practices = practices;
        this.searchRequests = searchRequests;
        this.appointmentSlots = appointmentSlots;
        this.bookings = bookings;
        this.calendarGateway = calendarGateway;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        Instant now = Instant.now(clock);

        Practice practice = practices.findByUsername(PRACTICE_USERNAME)
                .orElseGet(() -> practices.save(new Practice(
                        PRACTICE_USERNAME,
                        "Hautarztpraxis am Rosenthaler Platz",
                        "Brunnenstraße 12",
                        "10119",
                        "Berlin",
                        "+49 30 555 0100",
                        52.5295,
                        13.4077)));

        PatientProfile anna = patient("patient.anna", "Anna Demo", "10117", "Berlin", 52.5163, 13.3889);
        PatientProfile ben = patient("patient.ben", "Ben Demo", "10115", "Berlin", 52.5324, 13.3848);
        PatientProfile clara = patient("patient.clara", "Clara Demo", "10999", "Berlin", 52.4997, 13.4311);

        AvailabilityWindow daytime = new AvailabilityWindow(
                LocalDateTime.of(tomorrow, LocalTime.of(8, 0)),
                LocalDateTime.of(tomorrow, LocalTime.of(16, 0)));

        ensureSearchRequest(
                anna, BillingType.STATUTORY, ServiceTier.AUTOMATIC_5, null,
                daytime, now.minus(Duration.ofHours(2)));
        ensureSearchRequest(
                ben, BillingType.STATUTORY, ServiceTier.PERSONAL_25, tomorrow.plusDays(1),
                daytime, now.minus(Duration.ofHours(1)));
        ensureSearchRequest(
                clara, BillingType.SELF_PAY, ServiceTier.AUTOMATIC_5, null,
                daytime, now.minus(Duration.ofMinutes(30)));

        List<AppointmentSlot> existingSlots = appointmentSlots
                .findByPractice_UsernameOrderByStartAtDesc(PRACTICE_USERNAME);

        ensureSlot(
                practice,
                existingSlots,
                LocalDateTime.of(tomorrow, LocalTime.of(10, 30)),
                Set.of(BillingType.STATUTORY),
                "Kassenleistung; keine privaten Behandlungskosten");
        ensureSlot(
                practice,
                existingSlots,
                LocalDateTime.of(tomorrow, LocalTime.of(14, 30)),
                Set.of(BillingType.SELF_PAY),
                "Selbstzahlerleistung nach GOÄ; voraussichtlich 45–70 €");

        republishLocalCalendar();
    }

    private PatientProfile patient(String username, String displayName, String postalCode, String city,
                                   double latitude, double longitude) {
        return patientProfiles.findByUsername(username)
                .orElseGet(() -> patientProfiles.save(new PatientProfile(
                        username, displayName, postalCode, city, latitude, longitude)));
    }

    private void ensureSearchRequest(
            PatientProfile patient,
            BillingType billingType,
            ServiceTier serviceTier,
            LocalDate neededBy,
            AvailabilityWindow window,
            Instant createdAt) {
        boolean alreadyExists = searchRequests
                .findByPatient_UsernameOrderByCreatedAtDesc(patient.getUsername())
                .stream()
                .anyMatch(request -> request.getSpecialty() == Specialty.DERMATOLOGY
                        && request.getAppointmentType() == AppointmentType.SKIN_CANCER_SCREENING
                        && request.getBillingType() == billingType
                        && request.getServiceTier() == serviceTier
                        && request.getAvailabilityWindows().stream()
                        .anyMatch(existing -> existing.getStart().toLocalDate()
                                .equals(window.getStart().toLocalDate())));
        if (alreadyExists) {
            return;
        }

        searchRequests.save(new SearchRequest(
                patient,
                Specialty.DERMATOLOGY,
                AppointmentType.SKIN_CANCER_SCREENING,
                "Berlin",
                patient.getPostalCode(),
                patient.getLatitude(),
                patient.getLongitude(),
                15,
                List.of(window),
                true,
                true,
                billingType,
                serviceTier,
                neededBy,
                createdAt));
    }

    private void ensureSlot(
            Practice practice,
            List<AppointmentSlot> existingSlots,
            LocalDateTime startsAt,
            Set<BillingType> billingTypes,
            String treatmentCostInformation) {
        boolean alreadyExists = existingSlots.stream()
                .anyMatch(slot -> slot.getStartAt().equals(startsAt)
                        && slot.getAppointmentType() == AppointmentType.SKIN_CANCER_SCREENING
                        && slot.getAcceptedBillingTypes().equals(billingTypes));
        if (alreadyExists) {
            return;
        }

        AppointmentSlot slot = new AppointmentSlot(
                practice,
                Specialty.DERMATOLOGY,
                AppointmentType.SKIN_CANCER_SCREENING,
                startsAt,
                20,
                billingTypes,
                true,
                CalendarSource.LOCAL_DEMO,
                "LOCAL-DEMO-" + startsAt.toLocalDate() + "-" + startsAt.toLocalTime(),
                treatmentCostInformation,
                Instant.now(clock));
        appointmentSlots.saveAndFlush(slot);
    }

    private void republishLocalCalendar() {
        appointmentSlots.findByPractice_UsernameOrderByStartAtDesc(PRACTICE_USERNAME).stream()
                .filter(slot -> slot.getStatus() != SlotStatus.CANCELLED)
                .forEach(slot -> {
                    calendarGateway.publish(new PracticeCalendarGateway.PublishSlotCommand(
                            slot.getId(),
                            slot.getPractice().getId(),
                            slot.getSpecialty(),
                            slot.getAppointmentType(),
                            slot.getStartAt(),
                            Duration.ofMinutes(slot.getDurationMinutes())));

                    if (slot.getStatus() == SlotStatus.BOOKED) {
                        bookings.findByAppointmentSlot_Id(slot.getId())
                                .ifPresent(booking -> calendarGateway.markBooked(slot.getId(), booking.getId()));
                    }
                });
    }
}
