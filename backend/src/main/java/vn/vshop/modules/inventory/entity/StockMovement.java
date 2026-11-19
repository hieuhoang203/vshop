package vn.vshop.modules.inventory.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockMovement(
        UUID id,
        UUID skuId,
        UUID warehouseId,
        MovementType movementType,
        long deltaOnHand,
        long deltaReserved,
        long deltaBlocked,
        String idempotencyKey,
        String reason,
        OffsetDateTime createdAt) {}
