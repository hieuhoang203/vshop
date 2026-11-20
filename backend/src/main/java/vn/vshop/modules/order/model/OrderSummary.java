package vn.vshop.modules.order.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vshop.modules.order.entity.OrderStatus;

/** One row of the buyer's order list. */
public record OrderSummary(
        UUID id,
        String orderNumber,
        UUID checkoutGroupId,
        String shopNameSnapshot,
        OrderStatus status,
        long payableAmount,
        int itemCount,
        String firstProductName,
        String firstImageKey,
        OffsetDateTime createdAt) {}
