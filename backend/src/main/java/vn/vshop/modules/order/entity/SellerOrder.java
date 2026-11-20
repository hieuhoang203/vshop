package vn.vshop.modules.order.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code recipientSnapshotCiphertext} is a PiiCipher envelope bound to this order's id. */
public record SellerOrder(
        UUID id,
        UUID checkoutGroupId,
        UUID shopId,
        String orderNumber,
        OrderStatus status,
        long version,
        long itemAmount,
        long discountAmount,
        long shippingAmount,
        long payableAmount,
        String shopNameSnapshot,
        String recipientSnapshotCiphertext,
        String shipProvince,
        String shipWard,
        UUID returnPolicyId,
        OffsetDateTime paymentDeadline,
        OffsetDateTime confirmedAt,
        OffsetDateTime canceledAt,
        String cancelReason,
        OffsetDateTime deliveredAt,
        OffsetDateTime returnEligibleUntil,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String manualTrackingCode,
        OffsetDateTime shippedAt) {}
