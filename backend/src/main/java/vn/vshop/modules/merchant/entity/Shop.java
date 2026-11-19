package vn.vshop.modules.merchant.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Shop(
        UUID id,
        UUID ownerAccountId,
        String name,
        String description,
        ShopStatus status,
        String rejectionReason,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
