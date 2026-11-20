package vn.vshop.modules.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.order.entity.CheckoutGroup;
import vn.vshop.modules.order.entity.OrderLine;
import vn.vshop.modules.order.entity.OrderStatus;
import vn.vshop.modules.order.entity.OrderStatusEvent;
import vn.vshop.modules.order.entity.SellerOrder;
import vn.vshop.modules.order.model.OrderSummary;

public interface OrderRepository {
    Optional<CheckoutGroup> findGroupByKey(UUID buyer, String idempotencyKey);

    Optional<CheckoutGroup> findGroupByQuote(UUID buyer, UUID quoteId);

    Optional<CheckoutGroup> findGroup(UUID buyer, UUID groupId);

    void insertGroup(CheckoutGroup group);

    void insertOrder(SellerOrder order);

    void insertLine(OrderLine line);

    void linkReservation(UUID orderLineId, UUID reservationId);

    void insertHistory(OrderStatusEvent event);

    /** Orders of the buyer that are still open (not delivered, completed, canceled or rejected). */
    long countOpenOrders(UUID buyer, String paymentMethod);

    List<SellerOrder> findOrdersOfGroup(UUID groupId);

    Optional<SellerOrder> findOrder(UUID buyer, UUID orderId);

    List<OrderLine> findLines(UUID orderId);

    List<OrderStatusEvent> findHistory(UUID orderId);

    List<OrderSummary> listSummaries(UUID buyer, int limit, int offset);

    long countOrders(UUID buyer);

    // ---- seller side and status changes

    Optional<SellerOrder> findOrderOfShop(UUID shopId, UUID orderId);

    /** {@code status} may be null for all statuses; newest first. */
    List<SellerOrder> listForShop(UUID shopId, String status, int limit, int offset);

    long countForShop(UUID shopId, String status);

    /**
     * Moves the order from one status to another only if it is still in {@code from}. Returns false
     * when it was not (someone else changed it first), which callers report as a conflict.
     */
    boolean transition(UUID orderId, OrderStatus from, OrderStatus to, String cancelReason);

    boolean markCanceled(UUID orderId, OrderStatus from, String reason);

    boolean markShipped(UUID orderId, String trackingCode);

    boolean markDelivered(UUID orderId, int returnWindowDays);

    List<UUID> findReservationIds(UUID orderId);

    /** Who an order concerns, for notifications. */
    record Parties(
            String orderNumber, UUID buyerAccountId, UUID shopOwnerAccountId, String shopName) {}

    Optional<Parties> findParties(UUID orderId);

    String paymentMethodOfGroup(UUID groupId);

    /** Unscoped lookup for system actions (webhooks); callers must already be authorised. */
    SellerOrder findById(UUID orderId);

    /** IN_FULFILLMENT back to READY_TO_SHIP (shipment canceled), clearing the tracking code. */
    boolean reopenForShipping(UUID orderId);

    /** AWAITING_SELLER orders placed at or before {@code cutoff}, oldest first. */
    List<vn.vshop.modules.order.model.OverdueOrders.Overdue> findOverdue(
            java.time.OffsetDateTime cutoff, int limit);
}
