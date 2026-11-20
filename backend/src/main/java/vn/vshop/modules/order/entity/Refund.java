package vn.vshop.modules.order.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Money owed back to a buyer for one order. {@code status}: AWAITING_ACCOUNT (we need the buyer's
 * bank account), APPROVED (ready to pay out), SUCCEEDED (paid, {@code payoutReference} is the bank
 * reference), FAILED. The account number and name are PiiCipher envelopes.
 */
public record Refund(
        UUID id,
        UUID chargeId,
        UUID checkoutGroupId,
        UUID sellerOrderId,
        String orderNumber,
        String reason,
        String status,
        long amount,
        String bankName,
        String accountNumberCiphertext,
        String accountNameCiphertext,
        String payoutReference,
        String note,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt) {}
