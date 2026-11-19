package vn.vshop.modules.catalog.dto.response;

import java.util.UUID;

public record ProductResponse(
        UUID id,
        String slug,
        String name,
        String description,
        String categorySlug,
        String categoryName,
        UUID shopId,
        String shopName,
        long price,
        String currency,
        long stock,
        String imageKey) {}
