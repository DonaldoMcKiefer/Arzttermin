package com.kiefer.arzttermin.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * Local, thread-safe payment simulation. It never moves real money.
 */
@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    private final Clock clock;
    private final ConcurrentMap<UUID, CapturedPayment> capturesByBooking = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RefundRecord> refundsByPayment = new ConcurrentHashMap<>();

    public SimulatedPaymentProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public CaptureResult capture(CaptureCommand command) {
        Objects.requireNonNull(command, "command");
        CapturedPayment candidate = new CapturedPayment(
                command,
                new CaptureResult(
                        "SIM-PAY-" + command.bookingId(),
                        CaptureStatus.PAID,
                        Instant.now(clock)));

        CapturedPayment existing = capturesByBooking.putIfAbsent(command.bookingId(), candidate);
        if (existing == null) {
            return candidate.result();
        }
        ensureSameCapture(existing.command(), command);
        return existing.result();
    }

    @Override
    public RefundResult requestRefund(RefundCommand command) {
        Objects.requireNonNull(command, "command");
        CapturedPayment payment = capturesByBooking.get(command.bookingId());
        boolean persistedDemoPayment = payment == null
                && command.paymentReference().equals("SIM-PAY-" + command.bookingId());
        if (!persistedDemoPayment
                && (payment == null || !payment.result().providerReference().equals(command.paymentReference()))) {
            throw new IllegalArgumentException("Unknown simulated payment: " + command.paymentReference());
        }
        if (payment != null && (payment.command().amountCents() != command.amountCents()
                || !payment.command().currency().equals(command.currency()))) {
            throw new IllegalArgumentException("Refund amount or currency differs from captured payment");
        }

        RefundRecord candidate = new RefundRecord(
                command,
                new RefundResult(
                        "SIM-REFUND-" + command.bookingId(),
                        RefundStatus.REQUESTED,
                        Instant.now(clock)));
        RefundRecord existing = refundsByPayment.putIfAbsent(command.paymentReference(), candidate);
        if (existing == null) {
            return candidate.result();
        }
        ensureSameRefund(existing.command(), command);
        return existing.result();
    }

    private static void ensureSameCapture(CaptureCommand existing, CaptureCommand retried) {
        if (existing.amountCents() != retried.amountCents()
                || !existing.currency().equals(retried.currency())
                || !existing.idempotencyKey().equals(retried.idempotencyKey())) {
            throw new IllegalStateException(
                    "A different payment was already captured for booking " + retried.bookingId());
        }
    }

    private static void ensureSameRefund(RefundCommand existing, RefundCommand retried) {
        if (existing.amountCents() != retried.amountCents()
                || !existing.currency().equals(retried.currency())
                || !existing.idempotencyKey().equals(retried.idempotencyKey())) {
            throw new IllegalStateException(
                    "A different refund was already requested for payment " + retried.paymentReference());
        }
    }

    private record CapturedPayment(CaptureCommand command, CaptureResult result) {
    }

    private record RefundRecord(RefundCommand command, RefundResult result) {
    }
}
