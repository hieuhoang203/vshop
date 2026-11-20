package vn.vshop.modules.inventory.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID warehouseId,
        String type,
        long deltaOnHand,
        long deltaReserved,
        long deltaBlocked,
        String reason,
        OffsetDateTime createdAt) {}
