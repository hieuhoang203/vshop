package vn.vshop.modules.inventory.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.inventory.entity.ReservationStatus;
import vn.vshop.modules.inventory.entity.StockBalance;
import vn.vshop.modules.inventory.entity.StockMovement;
import vn.vshop.modules.inventory.entity.StockReservation;
import vn.vshop.modules.inventory.entity.Warehouse;

/**
 * Lock order used by every command: reservation row first, then balance rows ordered by warehouse
 * id. Quantity changes only go through {@link #applyDelta}, which carries the non-negative and
 * not-below-held guards in a single conditional UPDATE.
 */
public interface InventoryRepository {
    /** Pilot: one DEFAULT warehouse per shop, created on first stock receipt. */
    Warehouse findOrCreateDefaultWarehouse(UUID shopId);

    void ensureBalance(UUID skuId, UUID warehouseId, UUID shopId);

    Optional<StockBalance> lockBalance(UUID skuId, UUID warehouseId);

    /** Balances of the SKU in ACTIVE warehouses, locked in warehouse id order. */
    List<StockBalance> lockActiveBalances(UUID skuId);

    List<StockBalance> findBalances(UUID skuId, UUID shopId);

    /** False when the change would break on_hand/reserved/blocked invariants. */
    boolean applyDelta(UUID skuId, UUID warehouseId, long deltaOnHand, long deltaReserved);

    /** False when the idempotency key already exists. */
    boolean insertMovement(StockMovement movement);

    Optional<StockMovement> findMovement(String idempotencyKey);

    List<StockMovement> recentMovements(UUID skuId, UUID shopId, int limit);

    /** False when the idempotency key already exists. Expiry is now() + ttlSeconds in the DB. */
    boolean insertReservation(StockReservation reservation, long ttlSeconds);

    Optional<StockReservation> findReservationByKey(String idempotencyKey);

    Optional<StockReservation> lockReservation(UUID id);

    boolean transitionReservation(UUID id, ReservationStatus from, ReservationStatus to);

    List<StockReservation> lockExpiredReservations(int limit);
}
