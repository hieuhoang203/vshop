package vn.vshop.modules.inventory.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockBalanceResponse(
        UUID skuId,
        UUID warehouseId,
        long onHand,
        long reserved,
        long blocked,
        long available,
        OffsetDateTime updatedAt) {}
