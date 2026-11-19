package vn.vshop.modules.catalog.model;

import java.util.UUID;

/** Read projection across product, SKU, merchant and inventory; not a persisted entity. */
public record ProductSummary(
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
