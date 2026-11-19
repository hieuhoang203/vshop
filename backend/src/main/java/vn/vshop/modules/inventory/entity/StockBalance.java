package vn.vshop.modules.inventory.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Composite identity is (skuId, warehouseId); quantities are owned by inventory. */
public record StockBalance(
        UUID skuId,
        UUID warehouseId,
        UUID shopId,
        long onHand,
        long reserved,
        long blocked,
        long version,
        OffsetDateTime updatedAt) {
    public long available() {
        return onHand - reserved - blocked;
    }
}
