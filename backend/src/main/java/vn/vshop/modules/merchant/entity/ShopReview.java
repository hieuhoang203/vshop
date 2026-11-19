package vn.vshop.modules.merchant.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ShopReview(
        UUID id,
        UUID shopId,
        UUID reviewerAccountId,
        ShopReviewDecision decision,
        String reason,
        OffsetDateTime createdAt) {}
