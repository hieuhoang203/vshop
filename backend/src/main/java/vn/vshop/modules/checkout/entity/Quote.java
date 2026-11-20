package vn.vshop.modules.checkout.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Calculated, expiring quote. {@code snapshot} is the JSON of the priced groups at that moment. */
public record Quote(
        UUID id,
        UUID buyerAccountId,
        UUID cartId,
        long cartVersion,
        long totalAmount,
        String currency,
        String snapshot,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt) {}
