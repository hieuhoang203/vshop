package vn.vshop.modules.order.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cash collected on delivery for one order. {@code status}: WITH_CARRIER (the carrier holds it),
 * SETTLED (it reached the platform, or the shop collected it itself). {@code refundedAmount} is
 * what was refunded to the buyer afterwards, so {@code amount - refundedAmount} is what is really
 * due.
 */
public record CodReceivable(
        UUID id,
        UUID sellerOrderId,
        String orderNumber,
        UUID shopId,
        long amount,
        long refundedAmount,
        String collectedBy,
        String status,
        String settledReference,
        OffsetDateTime settledAt,
        OffsetDateTime createdAt) {}
