package vn.vshop.modules.order.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Orders the shop has not confirmed within the approved deadline. {@code confirmMinutes} is null
 * while no deadline policy is approved, and then the list is empty. Nothing is canceled
 * automatically: support decides.
 */
public record OverdueOrders(Integer confirmMinutes, List<Overdue> items) {
    public OverdueOrders {
        items = List.copyOf(items);
    }

    public record Overdue(
            UUID id,
            String orderNumber,
            UUID shopId,
            String shopName,
            long payableAmount,
            OffsetDateTime createdAt,
            long minutesOverdue) {}
}
