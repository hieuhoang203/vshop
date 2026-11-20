package vn.vshop.modules.order.entity;

import java.util.Set;

/** States of a seller order. Only PENDING_PAYMENT and AWAITING_SELLER are produced by checkout. */
public enum OrderStatus {
    PENDING_PAYMENT,
    AWAITING_SELLER,
    CONFIRMED,
    PACKING,
    READY_TO_SHIP,
    IN_FULFILLMENT,
    DELIVERED,
    COMPLETED,
    CANCEL_REQUESTED,
    CANCELED,
    REJECTED;

    public static final Set<OrderStatus> CLOSED = Set.of(DELIVERED, COMPLETED, CANCELED, REJECTED);
}
