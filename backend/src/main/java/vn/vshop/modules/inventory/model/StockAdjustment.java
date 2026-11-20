package vn.vshop.modules.inventory.model;

import java.util.UUID;
import vn.vshop.modules.inventory.entity.MovementType;

/** Seller stock command. {@code type} is RECEIPT (delta &gt; 0) or ADJUSTMENT (delta != 0). */
public record StockAdjustment(
        UUID skuId, MovementType type, long delta, String reason, String idempotencyKey) {}
