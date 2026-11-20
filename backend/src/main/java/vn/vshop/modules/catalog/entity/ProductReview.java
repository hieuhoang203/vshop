package vn.vshop.modules.catalog.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable moderation decision for exactly one product content version. */
public record ProductReview(
        UUID id,
        UUID productId,
        UUID shopId,
        UUID reviewerAccountId,
        ProductReviewDecision decision,
        String reason,
        long productVersion,
        OffsetDateTime createdAt) {}
