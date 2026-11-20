package vn.vshop.modules.order.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One redirect to the provider. Status: CREATED, SUCCEEDED, FAILED, EXPIRED, UNKNOWN. */
public record PaymentAttempt(
        UUID id,
        UUID intentId,
        UUID checkoutGroupId,
        String provider,
        String merchantAttemptReference,
        String providerReference,
        String status,
        String clientIp,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt) {}
