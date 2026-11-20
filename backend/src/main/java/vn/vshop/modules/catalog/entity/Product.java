package vn.vshop.modules.catalog.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Product(
        UUID id,
        UUID shopId,
        UUID categoryId,
        String slug,
        String name,
        String description,
        String imageKey,
        ProductStatus status,
        String rejectionReason,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
