package vn.vshop.modules.inventory.mapper;

import vn.vshop.modules.inventory.dto.request.StockAdjustmentRequest;
import vn.vshop.modules.inventory.dto.response.StockBalanceResponse;
import vn.vshop.modules.inventory.dto.response.StockMovementResponse;
import vn.vshop.modules.inventory.entity.MovementType;
import vn.vshop.modules.inventory.entity.StockBalance;
import vn.vshop.modules.inventory.entity.StockMovement;
import vn.vshop.modules.inventory.model.StockAdjustment;

public final class InventoryMapper {
    private InventoryMapper() {}

    public static StockAdjustment toCommand(StockAdjustmentRequest request, String idempotencyKey) {
        return new StockAdjustment(
                request.skuId(),
                MovementType.valueOf(request.type().name()),
                request.delta(),
                request.reason(),
                idempotencyKey);
    }

    public static StockBalanceResponse toResponse(StockBalance balance) {
        return new StockBalanceResponse(
                balance.skuId(),
                balance.warehouseId(),
                balance.onHand(),
                balance.reserved(),
                balance.blocked(),
                balance.available(),
                balance.updatedAt());
    }

    public static StockMovementResponse toResponse(StockMovement movement) {
        return new StockMovementResponse(
                movement.id(),
                movement.warehouseId(),
                movement.movementType().name(),
                movement.deltaOnHand(),
                movement.deltaReserved(),
                movement.deltaBlocked(),
                movement.reason(),
                movement.createdAt());
    }
}
