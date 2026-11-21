package vn.vshop.modules.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.order.entity.CodReceivable;

public interface CodReceivableRepository {
    /** One row per order; a repeat is ignored. */
    void insertIfAbsent(UUID orderId, UUID shopId, long amount, String collectedBy, String status);

    List<CodReceivable> list(String status, int limit);

    Optional<CodReceivable> find(UUID id);

    /** WITH_CARRIER to SETTLED, once. */
    boolean settle(UUID id, String reference, String subject);
}
