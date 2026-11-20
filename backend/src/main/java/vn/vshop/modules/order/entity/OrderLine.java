package vn.vshop.modules.order.entity;

import java.util.UUID;

/** Immutable snapshot of what was bought: names, variant and prices as they were at checkout. */
public record OrderLine(
        UUID id,
        UUID sellerOrderId,
        UUID shopId,
        UUID skuId,
        UUID productId,
        String productSlug,
        String productNameSnapshot,
        String imageKey,
        String sellerSku,
        String variantSnapshot,
        int quantity,
        long unitPrice,
        long grossAmount,
        long discountAmount,
        long netAmount) {}
