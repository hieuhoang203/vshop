package vn.vshop.modules.order.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One checkout of one quote; owns one seller order per shop. */
public record CheckoutGroup(
        UUID id,
        UUID buyerAccountId,
        UUID quoteId,
        String idempotencyKey,
        String requestHash,
        String currency,
        long totalAmount,
        PaymentMethod paymentMethod,
        String policySnapshot,
        OffsetDateTime createdAt) {}
