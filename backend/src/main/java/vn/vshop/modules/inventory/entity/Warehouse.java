package vn.vshop.modules.inventory.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Warehouse(
        UUID id,
        UUID shopId,
        String code,
        String name,
        WarehouseStatus status,
        OffsetDateTime createdAt) {}
