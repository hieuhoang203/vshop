package vn.vshop.modules.checkout.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Composite identity is (cartId, skuId). Holds intent only: no price and no stock. */
public record CartLine(
        UUID cartId,
        UUID skuId,
        int quantity,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
