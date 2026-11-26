package com.kiefer.arzttermin.payment;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Boundary for collecting portal fees and requesting refunds.
 *
 * <p>Commands contain a stable idempotency key so retries never create a
 * second payment. The current adapter is simulated and must not be mistaken
 * for a real payment integration.</p>
 */
public interface PaymentProvider {

    CaptureResult capture(CaptureCommand command);

    RefundResult requestRefund(RefundCommand command);

    enum CaptureStatus {
        PAID
    }

    enum RefundStatus {
        REQUESTED
    }

    record CaptureCommand(
            UUID bookingId,
            int amountCents,
            Currency currency,
            String idempotencyKey,
            String description) {

        public CaptureCommand {
            Objects.requireNonNull(bookingId, "bookingId");
            Objects.requireNonNull(currency, "currency");
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
            description = requireText(description, "description");
            if (amountCents <= 0) {
                throw new IllegalArgumentException("amountCents must be positive");
            }
        }
    }

    record CaptureResult(
            String providerReference,
            CaptureStatus status,
            Instant processedAt) {

        public CaptureResult {
            providerReference = requireText(providerReference, "providerReference");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(processedAt, "processedAt");
        }
    }

    record RefundCommand(
            UUID bookingId,
            String paymentReference,
            int amountCents,
            Currency currency,
            String idempotencyKey,
            String reason) {

        public RefundCommand {
            Objects.requireNonNull(bookingId, "bookingId");
            paymentReference = requireText(paymentReference, "paymentReference");
            Objects.requireNonNull(currency, "currency");
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
            reason = requireText(reason, "reason");
            if (amountCents <= 0) {
                throw new IllegalArgumentException("amountCents must be positive");
            }
        }
    }

    record RefundResult(
            String refundReference,
            RefundStatus status,
            Instant requestedAt) {

        public RefundResult {
            refundReference = requireText(refundReference, "refundReference");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(requestedAt, "requestedAt");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
