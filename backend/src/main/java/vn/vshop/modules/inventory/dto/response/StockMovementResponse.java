package vn.vshop.modules.inventory.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vshop.modules.inventory.entity.MovementType;

public record StockMovementResponse(
        UUID id,
        UUID warehouseId,
        MovementType type,
        long deltaOnHand,
        long deltaReserved,
        long deltaBlocked,
        String reason,
        OffsetDateTime createdAt) {}
