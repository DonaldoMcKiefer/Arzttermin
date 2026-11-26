package com.kiefer.arzttermin;

import com.kiefer.arzttermin.calendar.PracticeCalendarGateway;
import com.kiefer.arzttermin.domain.AppointmentOffer;
import com.kiefer.arzttermin.domain.AppointmentSlot;
import com.kiefer.arzttermin.domain.AppointmentType;
import com.kiefer.arzttermin.domain.AvailabilityWindow;
import com.kiefer.arzttermin.domain.BillingType;
import com.kiefer.arzttermin.domain.BookingStatus;
import com.kiefer.arzttermin.domain.CalendarSource;
import com.kiefer.arzttermin.domain.FeeStatus;
import com.kiefer.arzttermin.domain.OfferStatus;
import com.kiefer.arzttermin.domain.PatientProfile;
import com.kiefer.arzttermin.domain.PortalFee;
import com.kiefer.arzttermin.domain.Practice;
import com.kiefer.arzttermin.domain.SearchRequest;
import com.kiefer.arzttermin.domain.SearchRequestStatus;
import com.kiefer.arzttermin.domain.ServiceTier;
import com.kiefer.arzttermin.domain.SlotStatus;
import com.kiefer.arzttermin.domain.Specialty;
import com.kiefer.arzttermin.repository.AppointmentOfferRepository;
import com.kiefer.arzttermin.repository.AppointmentSlotRepository;
import com.kiefer.arzttermin.repository.BookingRepository;
import com.kiefer.arzttermin.repository.PatientProfileRepository;
import com.kiefer.arzttermin.repository.PortalFeeRepository;
import com.kiefer.arzttermin.repository.PracticeRepository;
import com.kiefer.arzttermin.repository.SearchRequestRepository;
import com.kiefer.arzttermin.service.OfferExpiryService;
import com.kiefer.arzttermin.service.PortalWorkflowService;
import com.kiefer.arzttermin.service.PortalException;
import com.kiefer.arzttermin.service.dto.PortalViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "DEMO_DATA_ENABLED=false",
        "terminradar.demo-data-enabled=false"
})
class PortalWorkflowAcceptanceTest {

    private static final String ANNA = "patient.anna";
    private static final String BEN = "patient.ben";
    private static final String PRACTICE = "praxis.haut";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PortalWorkflowService workflow;

    @Autowired
    private OfferExpiryService offerExpiryService;

    @Autowired
    private PatientProfileRepository patientProfiles;

    @Autowired
    private PracticeRepository practices;

    @Autowired
    private SearchRequestRepository searchRequests;

    @Autowired
    private AppointmentSlotRepository slots;

    @Autowired
    private AppointmentOfferRepository offers;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private PortalFeeRepository fees;

    @Autowired
    private PracticeCalendarGateway calendarGateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @BeforeEach
    void cleanDatabase() {
        SecurityContextHolder.clearContext();
        jdbc.execute("""
                TRUNCATE TABLE
                    practice_contact,
                    portal_fee,
                    booking,
                    appointment_offer,
                    appointment_slot_billing,
                    appointment_slot,
                    search_request_window,
                    search_request,
                    patient_profile,
                    practice
                CASCADE
                """);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void oldestMatchesComeFirstAndSelectingBenCreatesExactlyOnePersonalFee() {
        Scenario scenario = statutoryScenario();

        List<PortalViews.PracticeMatchView> matches = asPractice(
                () -> workflow.matchingRequests(scenario.slot().getId()));

        assertThat(matches)
                .extracting(PortalViews.PracticeMatchView::requestId)
                .containsExactly(scenario.annaRequest().getId(), scenario.benRequest().getId());
        assertPracticeMatchCannotContainTariffOrFee();

        UUID offerId = asPractice(
                () -> workflow.offerSlot(scenario.slot().getId(), scenario.benRequest().getId()));

        assertThat(bookings.count()).isZero();
        assertThat(fees.count()).isZero();

        PortalViews.BookingReceipt receipt = asPatient(BEN, () -> workflow.acceptOffer(offerId));

        assertThat(receipt.amountCents()).isEqualTo(2_500);
        assertThat(receipt.feeStatus()).isEqualTo(FeeStatus.PAID);
        assertThat(bookings.count()).isEqualTo(1);
        assertThat(fees.count()).isEqualTo(1);
        PortalFee fee = fees.findAll().getFirst();
        assertThat(fee.getAmountCents()).isEqualTo(2_500);
        assertThat(fee.getServiceTier()).isEqualTo(ServiceTier.PERSONAL_25);
        assertThat(fee.getBooking().getId()).isEqualTo(receipt.bookingId());
        assertThat(slots.findById(scenario.slot().getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.BOOKED);
        assertThat(searchRequests.findById(scenario.benRequest().getId()).orElseThrow().getStatus())
                .isEqualTo(SearchRequestStatus.BOOKED);
        assertThat(searchRequests.findById(scenario.annaRequest().getId()).orElseThrow().getStatus())
                .isEqualTo(SearchRequestStatus.OPEN);
        assertThat(calendarGateway.findBySlotId(scenario.slot().getId()).orElseThrow().status())
                .isEqualTo(PracticeCalendarGateway.EntryStatus.BOOKED);
    }

    @Test
    void declinedOfferReopensSlotAndRequestAndCanBeReassignedToBen() {
        Scenario scenario = statutoryScenario();
        UUID annaOfferId = asPractice(
                () -> workflow.offerSlot(scenario.slot().getId(), scenario.annaRequest().getId()));

        asPatient(ANNA, () -> {
            workflow.declineOffer(annaOfferId);
            return null;
        });

        assertThat(offers.findById(annaOfferId).orElseThrow().getStatus())
                .isEqualTo(OfferStatus.DECLINED);
        assertThat(slots.findById(scenario.slot().getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.AVAILABLE);
        assertThat(searchRequests.findById(scenario.annaRequest().getId()).orElseThrow().getStatus())
                .isEqualTo(SearchRequestStatus.OPEN);
        assertThat(bookings.count()).isZero();
        assertThat(fees.count()).isZero();

        UUID benOfferId = asPractice(
                () -> workflow.offerSlot(scenario.slot().getId(), scenario.benRequest().getId()));
        PortalViews.BookingReceipt receipt = asPatient(BEN, () -> workflow.acceptOffer(benOfferId));

        assertThat(receipt.amountCents()).isEqualTo(2_500);
        assertThat(offers.findById(benOfferId).orElseThrow().getStatus())
                .isEqualTo(OfferStatus.ACCEPTED);
        assertThat(slots.findById(scenario.slot().getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.BOOKED);
        assertThat(bookings.count()).isEqualTo(1);
        assertThat(fees.count()).isEqualTo(1);
    }

    @Test
    void selfPayOnlySlotExcludesStatutoryRequestButIncludesExplicitSelfPayRequest() {
        LocalDateTime startsAt = tomorrowAtTenThirty();
        Practice practice = savePractice();
        PatientProfile anna = savePatient(ANNA, "Anna Alt", "10115");
        PatientProfile ben = savePatient(BEN, "Ben Beispiel", "10119");
        Instant now = Instant.now(clock);
        SearchRequest statutory = saveRequest(
                anna, startsAt, BillingType.STATUTORY, ServiceTier.AUTOMATIC_5,
                null, now.minusSeconds(120));
        SearchRequest selfPay = saveRequest(
                ben, startsAt, BillingType.SELF_PAY, ServiceTier.AUTOMATIC_5,
                null, now.minusSeconds(60));
        AppointmentSlot slot = saveSlot(practice, startsAt, Set.of(BillingType.SELF_PAY));

        List<PortalViews.PracticeMatchView> matches = asPractice(
                () -> workflow.matchingRequests(slot.getId()));

        assertThat(matches)
                .extracting(PortalViews.PracticeMatchView::requestId)
                .containsExactly(selfPay.getId())
                .doesNotContain(statutory.getId());
    }

    @Test
    void twoConcurrentAcceptsReturnSameBookingAndCreateOneBookingAndFee() throws Exception {
        Scenario scenario = statutoryScenario();
        UUID offerId = asPractice(
                () -> workflow.offerSlot(scenario.slot().getId(), scenario.benRequest().getId()));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<PortalViews.BookingReceipt> first = executor.submit(
                    () -> acceptWhenReleased(offerId, ready, start));
            Future<PortalViews.BookingReceipt> second = executor.submit(
                    () -> acceptWhenReleased(offerId, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            PortalViews.BookingReceipt firstReceipt = first.get(30, TimeUnit.SECONDS);
            PortalViews.BookingReceipt secondReceipt = second.get(30, TimeUnit.SECONDS);

            assertThat(firstReceipt.bookingId()).isEqualTo(secondReceipt.bookingId());
            assertThat(firstReceipt.feeId()).isEqualTo(secondReceipt.feeId());
            assertThat(bookings.count()).isEqualTo(1);
            assertThat(fees.count()).isEqualTo(1);
            assertThat(fees.findAll().getFirst().getAmountCents()).isEqualTo(2_500);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void patientCannotAcceptAnotherPatientsPendingOffer() {
        Scenario scenario = statutoryScenario();
        UUID offerId = asPractice(
                () -> workflow.offerSlot(scenario.slot().getId(), scenario.benRequest().getId()));

        assertThatThrownBy(() -> asPatient(ANNA, () -> workflow.acceptOffer(offerId)))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("anderen Patientenkonto");

        assertThat(offers.findById(offerId).orElseThrow().getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(bookings.count()).isZero();
        assertThat(fees.count()).isZero();
    }

    @Test
    void anotherPracticeCannotOfferOrCancelTheForeignSlot() {
        Scenario scenario = statutoryScenario();

        assertThatThrownBy(() -> runAs("praxis.fremd", "PRACTICE",
                () -> workflow.offerSlot(scenario.slot().getId(), scenario.benRequest().getId())))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("anderen Praxis");
        assertThat(slots.findById(scenario.slot().getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.AVAILABLE);
        assertThat(offers.count()).isZero();

        UUID offerId = asPractice(
                () -> workflow.offerSlot(scenario.slot().getId(), scenario.benRequest().getId()));
        PortalViews.BookingReceipt receipt = asPatient(BEN, () -> workflow.acceptOffer(offerId));

        assertThatThrownBy(() -> runAs("praxis.fremd", "PRACTICE", () -> {
            workflow.cancelBooking(receipt.bookingId());
            return null;
        }))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("anderen Praxis");
        assertThat(bookings.findById(receipt.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(fees.findByBooking_Id(receipt.bookingId()).orElseThrow().getStatus())
                .isEqualTo(FeeStatus.PAID);
    }

    @Test
    void patientRoleIsDeniedAtPracticeServiceBoundary() {
        Scenario scenario = statutoryScenario();

        assertThatThrownBy(() -> asPatient(ANNA,
                () -> workflow.matchingRequests(scenario.slot().getId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void expiredOfferReleasesSlotAndRequestForReassignment() {
        Scenario scenario = statutoryScenario();
        Instant now = Instant.now(clock);
        AppointmentOffer expired = new AppointmentOffer(
                scenario.slot(), scenario.annaRequest(), now.minusSeconds(1_800), now.minusSeconds(60));
        scenario.slot().markOffered();
        scenario.annaRequest().markOffered();
        slots.saveAndFlush(scenario.slot());
        searchRequests.saveAndFlush(scenario.annaRequest());
        offers.saveAndFlush(expired);

        offerExpiryService.expireDueOffers();

        assertThat(offers.findById(expired.getId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(slots.findById(scenario.slot().getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(searchRequests.findById(scenario.annaRequest().getId()).orElseThrow().getStatus())
                .isEqualTo(SearchRequestStatus.OPEN);
        UUID reassigned = asPractice(
                () -> workflow.offerSlot(scenario.slot().getId(), scenario.benRequest().getId()));
        assertThat(offers.findById(reassigned).orElseThrow().getStatus()).isEqualTo(OfferStatus.PENDING);
    }

    @Test
    void practiceCancellationReopensSearchAndMarksPaidFeeForRefund() {
        Scenario scenario = statutoryScenario();
        UUID offerId = asPractice(
                () -> workflow.offerSlot(scenario.slot().getId(), scenario.benRequest().getId()));
        PortalViews.BookingReceipt receipt = asPatient(BEN, () -> workflow.acceptOffer(offerId));

        asPractice(() -> {
            workflow.cancelBooking(receipt.bookingId());
            return null;
        });

        assertThat(bookings.findById(receipt.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED_BY_PRACTICE);
        assertThat(slots.findById(scenario.slot().getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.CANCELLED);
        assertThat(searchRequests.findById(scenario.benRequest().getId()).orElseThrow().getStatus())
                .isEqualTo(SearchRequestStatus.OPEN);
        assertThat(fees.findByBooking_Id(receipt.bookingId()).orElseThrow().getStatus())
                .isEqualTo(FeeStatus.REFUND_PENDING);
    }

    private PortalViews.BookingReceipt acceptWhenReleased(
            UUID offerId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("The concurrent acceptance start signal was not received");
        }
        return asPatient(BEN, () -> workflow.acceptOffer(offerId));
    }

    private Scenario statutoryScenario() {
        LocalDateTime startsAt = tomorrowAtTenThirty();
        Practice practice = savePractice();
        PatientProfile anna = savePatient(ANNA, "Anna Alt", "10115");
        PatientProfile ben = savePatient(BEN, "Ben Beispiel", "10119");
        Instant now = Instant.now(clock);
        SearchRequest annaRequest = saveRequest(
                anna, startsAt, BillingType.STATUTORY, ServiceTier.AUTOMATIC_5,
                null, now.minusSeconds(120));
        SearchRequest benRequest = saveRequest(
                ben, startsAt, BillingType.STATUTORY, ServiceTier.PERSONAL_25,
                startsAt.toLocalDate(), now.minusSeconds(60));
        AppointmentSlot slot = saveSlot(practice, startsAt, Set.of(BillingType.STATUTORY));
        return new Scenario(slot, annaRequest, benRequest);
    }

    private Practice savePractice() {
        return practices.saveAndFlush(new Practice(
                PRACTICE,
                "Hautarztpraxis am Park",
                "Demoallee 17",
                "10117",
                "Berlin",
                "+49 30 555 0100",
                52.5163,
                13.3889));
    }

    private PatientProfile savePatient(String username, String displayName, String postalCode) {
        return patientProfiles.saveAndFlush(new PatientProfile(
                username,
                displayName,
                postalCode,
                "Berlin",
                52.52,
                13.40));
    }

    private SearchRequest saveRequest(
            PatientProfile patient,
            LocalDateTime startsAt,
            BillingType billingType,
            ServiceTier serviceTier,
            LocalDate neededBy,
            Instant createdAt) {
        return searchRequests.saveAndFlush(new SearchRequest(
                patient,
                Specialty.DERMATOLOGY,
                AppointmentType.SKIN_CANCER_SCREENING,
                "Berlin",
                patient.getPostalCode(),
                patient.getLatitude(),
                patient.getLongitude(),
                15,
                List.of(new AvailabilityWindow(startsAt.minusHours(2), startsAt.plusHours(2))),
                true,
                true,
                billingType,
                serviceTier,
                neededBy,
                createdAt));
    }

    private AppointmentSlot saveSlot(
            Practice practice,
            LocalDateTime startsAt,
            Set<BillingType> acceptedBillingTypes) {
        AppointmentSlot slot = slots.saveAndFlush(new AppointmentSlot(
                practice,
                Specialty.DERMATOLOGY,
                AppointmentType.SKIN_CANCER_SCREENING,
                startsAt,
                20,
                acceptedBillingTypes,
                true,
                CalendarSource.LOCAL_DEMO,
                "TEST-" + UUID.randomUUID(),
                acceptedBillingTypes.contains(BillingType.SELF_PAY)
                        ? "Behandlungskosten nach GOÄ; separat zur Portalgebühr."
                        : "Kassenleistung; mögliche Zusatzleistungen separat.",
                Instant.now(clock)));
        calendarGateway.publish(new PracticeCalendarGateway.PublishSlotCommand(
                slot.getId(),
                practice.getId(),
                slot.getSpecialty(),
                slot.getAppointmentType(),
                slot.getStartAt(),
                Duration.ofMinutes(slot.getDurationMinutes())));
        return slot;
    }

    private LocalDateTime tomorrowAtTenThirty() {
        return LocalDate.now(clock).plusDays(1).atTime(10, 30);
    }

    private static void assertPracticeMatchCannotContainTariffOrFee() {
        List<String> componentNames = Arrays.stream(PortalViews.PracticeMatchView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        List<Class<?>> componentTypes = Arrays.stream(PortalViews.PracticeMatchView.class.getRecordComponents())
                .map(RecordComponent::getType)
                .toList();

        assertThat(componentNames)
                .doesNotContain("serviceTier", "portalFeeCents", "fee", "tariff");
        assertThat(componentTypes).doesNotContain(ServiceTier.class);
    }

    private <T> T asPractice(Supplier<T> action) {
        return runAs(PRACTICE, "PRACTICE", action);
    }

    private <T> T asPatient(String username, Supplier<T> action) {
        return runAs(username, "PATIENT", action);
    }

    private static <T> T runAs(String username, String role, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                username,
                "test-only",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private record Scenario(
            AppointmentSlot slot,
            SearchRequest annaRequest,
            SearchRequest benRequest) {
    }
}
