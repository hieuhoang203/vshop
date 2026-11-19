package vn.vshop.modules.catalog.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductSku(
        UUID id,
        UUID productId,
        UUID shopId,
        String sellerSku,
        String variantSignature,
        long price,
        String currency,
        SkuStatus status,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
