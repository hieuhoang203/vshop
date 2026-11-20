package vn.vshop.modules.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.order.entity.CheckoutGroup;
import vn.vshop.modules.order.entity.OrderLine;
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
}
