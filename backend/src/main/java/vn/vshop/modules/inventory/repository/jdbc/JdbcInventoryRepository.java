package vn.vshop.modules.inventory.repository.jdbc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.inventory.entity.ReservationStatus;
import vn.vshop.modules.inventory.entity.StockBalance;
import vn.vshop.modules.inventory.entity.StockMovement;
import vn.vshop.modules.inventory.entity.StockReservation;
import vn.vshop.modules.inventory.entity.Warehouse;
import vn.vshop.modules.inventory.repository.InventoryRepository;

@Repository
public class JdbcInventoryRepository implements InventoryRepository {
    private static final String BALANCE_COLUMNS =
            "b.sku_id, b.warehouse_id, b.shop_id, b.on_hand, b.reserved, b.blocked, b.version, b.updated_at";
    private static final String RESERVATION_COLUMNS =
            "id, sku_id, warehouse_id, quantity, status, reference_id, idempotency_key, expires_at, created_at, updated_at";
    private static final String MOVEMENT_COLUMNS =
            "id, sku_id, warehouse_id, movement_type, delta_on_hand, delta_reserved, delta_blocked, idempotency_key, reason, created_at";
    private final JdbcClient jdbc;

    public JdbcInventoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Warehouse findOrCreateDefaultWarehouse(UUID shopId) {
        jdbc.sql(
                        """
            INSERT INTO inventory.warehouse (id, shop_id, code, name)
            VALUES (:id, :shop, 'DEFAULT', 'Kho mặc định')
            ON CONFLICT (shop_id, code) DO NOTHING
            """)
                .param("id", UUID.randomUUID())
                .param("shop", shopId)
                .update();
        return jdbc.sql(
                        "SELECT id, shop_id, code, name, status, created_at FROM inventory.warehouse WHERE shop_id = :shop AND code = 'DEFAULT'")
                .param("shop", shopId)
                .query(Warehouse.class)
                .single();
    }

    @Override
    public void ensureBalance(UUID skuId, UUID warehouseId, UUID shopId) {
        jdbc.sql(
                        """
            INSERT INTO inventory.stock_balance (sku_id, warehouse_id, shop_id)
            VALUES (:sku, :warehouse, :shop) ON CONFLICT DO NOTHING
            """)
                .param("sku", skuId)
                .param("warehouse", warehouseId)
                .param("shop", shopId)
                .update();
    }

    @Override
    public Optional<StockBalance> lockBalance(UUID skuId, UUID warehouseId) {
        return jdbc.sql(
                        "SELECT "
                                + BALANCE_COLUMNS
                                + " FROM inventory.stock_balance b WHERE b.sku_id = :sku AND b.warehouse_id = :warehouse FOR UPDATE")
                .param("sku", skuId)
                .param("warehouse", warehouseId)
                .query(StockBalance.class)
                .optional();
    }

    @Override
    public List<StockBalance> lockActiveBalances(UUID skuId) {
        return jdbc.sql(
                        "SELECT "
                                + BALANCE_COLUMNS
                                + """
             FROM inventory.stock_balance b
             JOIN inventory.warehouse w ON w.id = b.warehouse_id AND w.status = 'ACTIVE'
             WHERE b.sku_id = :sku ORDER BY b.warehouse_id FOR UPDATE OF b
            """)
                .param("sku", skuId)
                .query(StockBalance.class)
                .list();
    }

    @Override
    public List<StockBalance> findBalances(UUID skuId, UUID shopId) {
        return jdbc.sql(
                        "SELECT "
                                + BALANCE_COLUMNS
                                + " FROM inventory.stock_balance b WHERE b.sku_id = :sku AND b.shop_id = :shop ORDER BY b.warehouse_id")
                .param("sku", skuId)
                .param("shop", shopId)
                .query(StockBalance.class)
                .list();
    }

    @Override
    public boolean applyDelta(UUID skuId, UUID warehouseId, long deltaOnHand, long deltaReserved) {
        return jdbc.sql(
                                """
            UPDATE inventory.stock_balance
            SET on_hand = on_hand + :onHand, reserved = reserved + :reserved,
                version = version + 1, updated_at = now()
            WHERE sku_id = :sku AND warehouse_id = :warehouse
              AND on_hand + :onHand >= 0 AND reserved + :reserved >= 0
              AND reserved + :reserved <= on_hand + :onHand
              AND blocked <= (on_hand + :onHand) - (reserved + :reserved)
            """)
                        .param("sku", skuId)
                        .param("warehouse", warehouseId)
                        .param("onHand", deltaOnHand)
                        .param("reserved", deltaReserved)
                        .update()
                == 1;
    }

    @Override
    public boolean insertMovement(StockMovement movement) {
        return jdbc.sql(
                                """
            INSERT INTO inventory.stock_movement
                (id, sku_id, warehouse_id, movement_type, delta_on_hand, delta_reserved, delta_blocked, idempotency_key, reason)
            VALUES (:id, :sku, :warehouse, :type, :onHand, :reserved, :blocked, :key, :reason)
            ON CONFLICT (idempotency_key) DO NOTHING
            """)
                        .param("id", movement.id())
                        .param("sku", movement.skuId())
                        .param("warehouse", movement.warehouseId())
                        .param("type", movement.movementType().name())
                        .param("onHand", movement.deltaOnHand())
                        .param("reserved", movement.deltaReserved())
                        .param("blocked", movement.deltaBlocked())
                        .param("key", movement.idempotencyKey())
                        .param("reason", movement.reason())
                        .update()
                == 1;
    }

    @Override
    public Optional<StockMovement> findMovement(String idempotencyKey) {
        return jdbc.sql(
                        "SELECT "
                                + MOVEMENT_COLUMNS
                                + " FROM inventory.stock_movement WHERE idempotency_key = :key")
                .param("key", idempotencyKey)
                .query(StockMovement.class)
                .optional();
    }

    @Override
    public List<StockMovement> recentMovements(UUID skuId, UUID shopId, int limit) {
        return jdbc.sql(
                        """
            SELECT m.id, m.sku_id, m.warehouse_id, m.movement_type, m.delta_on_hand, m.delta_reserved,
                   m.delta_blocked, m.idempotency_key, m.reason, m.created_at
            FROM inventory.stock_movement m
            JOIN inventory.warehouse w ON w.id = m.warehouse_id
            WHERE m.sku_id = :sku AND w.shop_id = :shop
            ORDER BY m.created_at DESC, m.id LIMIT :limit
            """)
                .param("sku", skuId)
                .param("shop", shopId)
                .param("limit", limit)
                .query(StockMovement.class)
                .list();
    }

    @Override
    public boolean insertReservation(StockReservation reservation, long ttlSeconds) {
        return jdbc.sql(
                                """
            INSERT INTO inventory.stock_reservation
                (id, sku_id, warehouse_id, quantity, status, reference_id, idempotency_key, expires_at)
            VALUES (:id, :sku, :warehouse, :quantity, 'HELD', :reference, :key,
                    now() + make_interval(secs => :ttl))
            ON CONFLICT (idempotency_key) DO NOTHING
            """)
                        .param("id", reservation.id())
                        .param("sku", reservation.skuId())
                        .param("warehouse", reservation.warehouseId())
                        .param("quantity", reservation.quantity())
                        .param("reference", reservation.referenceId())
                        .param("key", reservation.idempotencyKey())
                        .param("ttl", (double) ttlSeconds)
                        .update()
                == 1;
    }

    @Override
    public Optional<StockReservation> findReservationByKey(String idempotencyKey) {
        return jdbc.sql(
                        "SELECT "
                                + RESERVATION_COLUMNS
                                + " FROM inventory.stock_reservation WHERE idempotency_key = :key")
                .param("key", idempotencyKey)
                .query(StockReservation.class)
                .optional();
    }

    @Override
    public Optional<StockReservation> lockReservation(UUID id) {
        return jdbc.sql(
                        "SELECT "
                                + RESERVATION_COLUMNS
                                + " FROM inventory.stock_reservation WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(StockReservation.class)
                .optional();
    }

    @Override
    public boolean transitionReservation(UUID id, ReservationStatus from, ReservationStatus to) {
        return jdbc.sql(
                                """
            UPDATE inventory.stock_reservation SET status = :to, updated_at = now()
            WHERE id = :id AND status = :from
            """)
                        .param("id", id)
                        .param("from", from.name())
                        .param("to", to.name())
                        .update()
                == 1;
    }

    @Override
    public List<StockReservation> lockExpiredReservations(int limit) {
        return jdbc.sql(
                        "SELECT "
                                + RESERVATION_COLUMNS
                                + """
             FROM inventory.stock_reservation
             WHERE status = 'HELD' AND expires_at <= now()
             ORDER BY expires_at, id LIMIT :limit FOR UPDATE SKIP LOCKED
            """)
                .param("limit", limit)
                .query(StockReservation.class)
                .list();
    }
}
