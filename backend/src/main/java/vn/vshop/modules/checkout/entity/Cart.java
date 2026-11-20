package vn.vshop.modules.checkout.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One cart per buyer account. {@code version} increments on every line change. */
public record Cart(
        UUID id,
        UUID buyerAccountId,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
