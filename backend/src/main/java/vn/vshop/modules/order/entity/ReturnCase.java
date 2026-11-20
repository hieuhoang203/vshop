package vn.vshop.modules.order.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A buyer's request to return goods and/or get money back after delivery. {@code status}:
 * REQUESTED, SELLER_OFFERED, SELLER_REJECTED, AWAITING_RETURN, IN_RETURN, REVIEWING (waiting for an
 * administrator), REFUND_APPROVED (a refund exists), REJECTED, CANCELED.
 */
public record ReturnCase(
        UUID id,
        UUID sellerOrderId,
        String orderNumber,
        UUID shopId,
        UUID buyerAccountId,
        String status,
        String reasonCode,
        String preferredResolution,
        String resolution,
        String description,
        long requestedAmount,
        Long offeredAmount,
        Long approvedAmount,
        UUID policyId,
        OffsetDateTime sellerDeadlineAt,
        String sellerNote,
        String adminNote,
        String returnTrackingCode,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
