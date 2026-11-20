package vn.vshop.modules.inventory.service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.modules.inventory.entity.MovementType;
import vn.vshop.modules.inventory.entity.ReservationStatus;
import vn.vshop.modules.inventory.entity.StockBalance;
import vn.vshop.modules.inventory.entity.StockMovement;
import vn.vshop.modules.inventory.entity.StockReservation;
import vn.vshop.modules.inventory.repository.InventoryRepository;

/**
 * Atomic reservation lifecycle for the future checkout module. There is deliberately no HTTP
 * endpoint: only the owning checkout/order flow may hold or commit stock.
 *
 * <p>HELD -> COMMITTED consumes on_hand and reserved together (BR32). HELD -> RELEASED/EXPIRED only
 * returns reserved. Terminal states never execute twice; repeating the same transition is a no-op.
 * Commit does not check the TTL: the stored status is the authority, so a paid order is not lost
 * between the deadline and the next expiry sweep.
 */
@Service
@Transactional
public class ReservationService {
    static final long MAX_QUANTITY = 10_000L;
    static final Duration MIN_TTL = Duration.ofSeconds(1);
    static final Duration MAX_TTL = Duration.ofHours(24);

    private final InventoryRepository inventory;

    public ReservationService(InventoryRepository inventory) {
        this.inventory = inventory;
    }

    public StockReservation reserve(
            UUID skuId, long quantity, UUID referenceId, String idempotencyKey, Duration ttl) {
        if (quantity <= 0 || quantity > MAX_QUANTITY) {
            throw invalid("Số lượng giữ hàng phải từ 1 đến " + MAX_QUANTITY + ".");
        }
        if (ttl.compareTo(MIN_TTL) < 0 || ttl.compareTo(MAX_TTL) > 0) {
            throw invalid("Thời gian giữ hàng phải từ 1 giây đến 24 giờ.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 140) {
            throw invalid("Khóa idempotency không hợp lệ.");
        }
        List<StockBalance> balances = inventory.lockActiveBalances(skuId);
        StockBalance chosen =
                balances.stream()
                        .filter(balance -> balance.available() >= quantity)
                        .max(Comparator.comparingLong(StockBalance::available))
                        .orElse(null);
        UUID id = UUID.randomUUID();
        // Insert first so a replay with the same key is detected before touching quantities.
        UUID warehouse = chosen != null ? chosen.warehouseId() : null;
        if (warehouse == null) {
            return replayOrInsufficient(skuId, quantity, referenceId, idempotencyKey);
        }
        boolean fresh =
                inventory.insertReservation(
                        new StockReservation(
                                id,
                                skuId,
                                warehouse,
                                quantity,
                                ReservationStatus.HELD,
                                referenceId,
                                idempotencyKey,
                                null,
                                null,
                                null),
                        ttl.toSeconds());
        if (!fresh) {
            return replayOrInsufficient(skuId, quantity, referenceId, idempotencyKey);
        }
        if (!inventory.applyDelta(skuId, warehouse, 0, quantity)) {
            throw insufficient();
        }
        record(id, skuId, warehouse, MovementType.RESERVE, 0, quantity, "reserve:" + id);
        return inventory.findReservationByKey(idempotencyKey).orElseThrow();
    }

    public StockReservation commit(UUID reservationId) {
        StockReservation reservation = existing(reservationId);
        switch (reservation.status()) {
            case COMMITTED -> {
                return reservation;
            }
            case HELD -> {}
            default -> throw state(reservation);
        }
        inventory.lockBalance(reservation.skuId(), reservation.warehouseId()).orElseThrow();
        long quantity = reservation.quantity();
        if (!inventory.applyDelta(
                reservation.skuId(), reservation.warehouseId(), -quantity, -quantity)) {
            throw new IllegalStateException("Reserved stock is inconsistent for " + reservationId);
        }
        inventory.transitionReservation(
                reservationId, ReservationStatus.HELD, ReservationStatus.COMMITTED);
        record(
                reservationId,
                reservation.skuId(),
                reservation.warehouseId(),
                MovementType.COMMIT,
                -quantity,
                -quantity,
                "commit:" + reservationId);
        return existing(reservationId);
    }

    public StockReservation release(UUID reservationId) {
        StockReservation reservation = existing(reservationId);
        switch (reservation.status()) {
            case RELEASED, EXPIRED -> {
                return reservation;
            }
            case HELD -> {}
            default -> throw state(reservation);
        }
        giveBack(reservation, ReservationStatus.RELEASED, "release:" + reservationId);
        return existing(reservationId);
    }

    /** Expires HELD reservations past their deadline. Safe to run concurrently and repeatedly. */
    public int expireDue(int limit) {
        List<StockReservation> due = inventory.lockExpiredReservations(limit);
        for (StockReservation reservation : due) {
            giveBack(reservation, ReservationStatus.EXPIRED, "expire:" + reservation.id());
        }
        return due.size();
    }

    private void giveBack(StockReservation reservation, ReservationStatus target, String key) {
        inventory.lockBalance(reservation.skuId(), reservation.warehouseId()).orElseThrow();
        if (!inventory.applyDelta(
                reservation.skuId(), reservation.warehouseId(), 0, -reservation.quantity())) {
            throw new IllegalStateException(
                    "Reserved stock is inconsistent for " + reservation.id());
        }
        inventory.transitionReservation(reservation.id(), ReservationStatus.HELD, target);
        record(
                reservation.id(),
                reservation.skuId(),
                reservation.warehouseId(),
                MovementType.RELEASE,
                0,
                -reservation.quantity(),
                key);
    }

    private StockReservation replayOrInsufficient(
            UUID skuId, long quantity, UUID referenceId, String idempotencyKey) {
        var previous = inventory.findReservationByKey(idempotencyKey);
        if (previous.isEmpty()) {
            throw insufficient();
        }
        StockReservation reservation = previous.get();
        if (!reservation.skuId().equals(skuId)
                || reservation.quantity() != quantity
                || !reservation.referenceId().equals(referenceId)) {
            throw new BusinessException(
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Khóa idempotency này đã được dùng cho một lần giữ hàng khác.");
        }
        return reservation;
    }

    private StockReservation existing(UUID id) {
        return inventory
                .lockReservation(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.RESERVATION_NOT_FOUND,
                                        "Không tìm thấy lượt giữ hàng."));
    }

    private void record(
            UUID reservationId,
            UUID skuId,
            UUID warehouseId,
            MovementType type,
            long deltaOnHand,
            long deltaReserved,
            String key) {
        inventory.insertMovement(
                new StockMovement(
                        UUID.randomUUID(),
                        skuId,
                        warehouseId,
                        type,
                        deltaOnHand,
                        deltaReserved,
                        0,
                        key,
                        "reservation " + reservationId,
                        null));
    }

    private static BusinessException insufficient() {
        return new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Không đủ hàng để giữ.");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_STOCK_INPUT, message);
    }

    private static BusinessException state(StockReservation reservation) {
        return new BusinessException(
                ErrorCode.INVALID_RESERVATION_STATE,
                "Lượt giữ hàng đang ở trạng thái " + reservation.status() + ".");
    }
}
