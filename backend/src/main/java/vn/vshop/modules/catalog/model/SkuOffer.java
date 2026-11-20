package vn.vshop.modules.catalog.model;

import java.util.UUID;

/**
 * Current sellable facts about one SKU for cart and quote pricing. {@code saleable} means the SKU
 * is active, its product is ACTIVE and its shop is APPROVED. Stock quantities are not included.
 */
public record SkuOffer(
        UUID skuId,
        UUID productId,
        String productSlug,
        String productName,
        String imageKey,
        String variantLabel,
        String sellerSku,
        long price,
        UUID shopId,
        String shopName,
        boolean saleable) {}
