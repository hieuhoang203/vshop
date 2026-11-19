package vn.vshop.modules.inventory.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockReservation(
        UUID id,
        UUID skuId,
        UUID warehouseId,
        long quantity,
        ReservationStatus status,
        UUID referenceId,
        String idempotencyKey,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
