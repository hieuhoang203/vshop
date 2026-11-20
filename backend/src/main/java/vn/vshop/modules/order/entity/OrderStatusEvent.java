package vn.vshop.modules.order.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderStatusEvent(
        UUID id,
        UUID sellerOrderId,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String actorType,
        String actorSubject,
        String reason,
        OffsetDateTime createdAt) {}
