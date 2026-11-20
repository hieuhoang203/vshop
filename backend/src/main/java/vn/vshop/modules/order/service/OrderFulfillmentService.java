package vn.vshop.modules.order.service;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.modules.identity.service.AccountService;
import vn.vshop.modules.inventory.service.ReservationService;
import vn.vshop.modules.merchant.service.ShopService;
import vn.vshop.modules.order.entity.OrderStatus;
import vn.vshop.modules.order.entity.OrderStatusEvent;
import vn.vshop.modules.order.entity.SellerOrder;
import vn.vshop.modules.order.model.OrderDetail;
import vn.vshop.modules.order.model.OverdueOrders;
import vn.vshop.modules.order.model.SellerOrderDetail;
import vn.vshop.modules.order.repository.OrderRepository;
import vn.vshop.modules.platform.service.OutboxService;
import vn.vshop.modules.platform.service.PolicyService;

/**
 * What happens to an order after checkout: the shop confirms, rejects, packs and ships it (manual
 * shipping mode, docs/20 D11), and the buyer may cancel before the shop starts. Every change is a
 * conditional UPDATE on the expected current status, so confirm and cancel racing each other have
 * exactly one winner, and stock goes back at most once. Delivery is declared by the seller; it is
 * not confirmed by a carrier and must not be used to pay anyone out.
 */
@Service
public class OrderFulfillmentService {
    private static final Pattern TRACKING = Pattern.compile("^[A-Za-z0-9._-]{3,100}$");
    private static final Set<OrderStatus> RECIPIENT_VISIBLE =
            EnumSet.of(
                    OrderStatus.CONFIRMED,
                    OrderStatus.PACKING,
                    OrderStatus.READY_TO_SHIP,
                    OrderStatus.IN_FULFILLMENT,
                    OrderStatus.DELIVERED,
                    OrderStatus.COMPLETED);

    private final OrderRepository orders;
    private final OrderService orderService;
    private final ShopService shops;
    private final AccountService accounts;
    private final ReservationService reservations;
    private final PolicyService policies;
    private final OutboxService outbox;
    private final ObjectMapper json;
    private final RefundService refunds;
    private final java.time.Clock clock;

    OrderFulfillmentService(
            OrderRepository orders,
            OrderService orderService,
            ShopService shops,
            AccountService accounts,
            ReservationService reservations,
            PolicyService policies,
            OutboxService outbox,
            ObjectMapper json,
            RefundService refunds,
            java.time.Clock clock) {
        this.orders = orders;
        this.orderService = orderService;
        this.shops = shops;
        this.accounts = accounts;
        this.reservations = reservations;
        this.policies = policies;
        this.outbox = outbox;
        this.json = json;
        this.refunds = refunds;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ support queue

    /**
     * Orders still waiting for the shop after the approved deadline. Nothing is changed: no
     * automatic cancel, support decides (docs/20 D8).
     */
    @Transactional(readOnly = true)
    public OverdueOrders overdue() {
        var minutes = policies.sellerConfirmationMinutes();
        if (minutes.isEmpty()) {
            return new OverdueOrders(null, List.of());
        }
        var cutoff = java.time.OffsetDateTime.now(clock).minusMinutes(minutes.get());
        return new OverdueOrders(minutes.get(), orders.findOverdue(cutoff, 200));
    }

    // ------------------------------------------------------------------ seller reads

    @Transactional
    public List<SellerOrderDetail> list(
            String subject, UUID shopId, OrderStatus status, int page, int size) {
        shops.ownedShop(shopId, subject);
        return orders
                .listForShop(shopId, status == null ? null : status.name(), size, page * size)
                .stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public long count(String subject, UUID shopId, OrderStatus status) {
        shops.ownedShop(shopId, subject);
        return orders.countForShop(shopId, status == null ? null : status.name());
    }

    @Transactional
    public SellerOrderDetail get(String subject, UUID shopId, UUID orderId) {
        shops.ownedShop(shopId, subject);
        return view(find(shopId, orderId));
    }

    // ------------------------------------------------------------------ seller commands

    @Transactional
    public SellerOrderDetail confirm(String subject, UUID shopId, UUID orderId) {
        SellerOrder order = ownedOrder(subject, shopId, orderId);
        step(order, OrderStatus.AWAITING_SELLER, OrderStatus.CONFIRMED, null);
        record(order, OrderStatus.AWAITING_SELLER, OrderStatus.CONFIRMED, "SELLER", subject, null);
        publish("OrderConfirmed", order);
        return view(find(shopId, orderId));
    }

    @Transactional
    public SellerOrderDetail reject(String subject, UUID shopId, UUID orderId, String reason) {
        String clean = reason(reason);
        SellerOrder order = ownedOrder(subject, shopId, orderId);
        step(order, OrderStatus.AWAITING_SELLER, OrderStatus.REJECTED, clean);
        restock(order);
        refunds.createForOrder(orderId, "SELLER_REJECTED");
        record(order, OrderStatus.AWAITING_SELLER, OrderStatus.REJECTED, "SELLER", subject, clean);
        publish("OrderRejected", order);
        return view(find(shopId, orderId));
    }

    @Transactional
    public SellerOrderDetail startPacking(String subject, UUID shopId, UUID orderId) {
        return simple(subject, shopId, orderId, OrderStatus.CONFIRMED, OrderStatus.PACKING);
    }

    @Transactional
    public SellerOrderDetail markReady(String subject, UUID shopId, UUID orderId) {
        return simple(subject, shopId, orderId, OrderStatus.PACKING, OrderStatus.READY_TO_SHIP);
    }

    @Transactional
    public SellerOrderDetail ship(String subject, UUID shopId, UUID orderId, String trackingCode) {
        String code = trackingCode == null ? "" : trackingCode.trim();
        if (!TRACKING.matcher(code).matches()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_INPUT,
                    "Mã vận đơn cần 3–100 ký tự gồm chữ, số, dấu chấm, gạch ngang hoặc gạch dưới.");
        }
        SellerOrder order = ownedOrder(subject, shopId, orderId);
        if (!orders.markShipped(orderId, code)) {
            throw conflict(order);
        }
        record(
                order,
                OrderStatus.READY_TO_SHIP,
                OrderStatus.IN_FULFILLMENT,
                "SELLER",
                subject,
                "Giao thủ công, mã vận đơn do người bán nhập");
        publish("OrderShipped", order);
        return view(find(shopId, orderId));
    }

    @Transactional
    public SellerOrderDetail declareDelivered(String subject, UUID shopId, UUID orderId) {
        SellerOrder order = ownedOrder(subject, shopId, orderId);
        if (!orders.markDelivered(orderId, policies.returnWindowDays(order.returnPolicyId()))) {
            throw conflict(order);
        }
        record(
                order,
                OrderStatus.IN_FULFILLMENT,
                OrderStatus.DELIVERED,
                "SELLER",
                subject,
                "Người bán xác nhận đã giao (chưa có xác nhận từ đơn vị vận chuyển)");
        publish("OrderDelivered", order);
        return view(find(shopId, orderId));
    }

    // ------------------------------------------------------------------ buyer

    /** The buyer may cancel only while the shop has not started on the order. */
    @Transactional
    public OrderDetail cancelByBuyer(String subject, UUID orderId, String reason) {
        String clean = reason(reason);
        UUID buyer = accounts.requireActiveAccount(subject);
        SellerOrder order =
                orders.findOrder(buyer, orderId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.ORDER_NOT_FOUND,
                                                "Không tìm thấy đơn hàng."));
        if (!orders.markCanceled(orderId, OrderStatus.AWAITING_SELLER, clean)) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATE,
                    "Người bán đã xử lý đơn này nên không thể hủy trực tiếp.");
        }
        restock(order);
        refunds.createForOrder(orderId, "ORDER_CANCELED");
        record(order, OrderStatus.AWAITING_SELLER, OrderStatus.CANCELED, "BUYER", subject, clean);
        publish("OrderCanceled", order);
        return orderService.open(orders.findOrder(buyer, orderId).orElseThrow());
    }

    // ------------------------------------------------------------------ helpers

    private SellerOrderDetail simple(
            String subject, UUID shopId, UUID orderId, OrderStatus from, OrderStatus to) {
        SellerOrder order = ownedOrder(subject, shopId, orderId);
        step(order, from, to, null);
        record(order, from, to, "SELLER", subject, null);
        return view(find(shopId, orderId));
    }

    private void step(SellerOrder order, OrderStatus from, OrderStatus to, String reason) {
        if (!orders.transition(order.id(), from, to, reason)) {
            throw conflict(order);
        }
    }

    /** Stock was consumed at checkout (COD); give each line's stock back exactly once. */
    private void restock(SellerOrder order) {
        for (UUID reservationId : orders.findReservationIds(order.id())) {
            reservations.returnCommitted(reservationId);
        }
    }

    private SellerOrder ownedOrder(String subject, UUID shopId, UUID orderId) {
        shops.ownedShop(shopId, subject);
        return find(shopId, orderId);
    }

    private SellerOrder find(UUID shopId, UUID orderId) {
        return orders.findOrderOfShop(shopId, orderId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));
    }

    private BusinessException conflict(SellerOrder order) {
        OrderStatus now = orders.findOrderOfShop(order.shopId(), order.id()).orElse(order).status();
        return new BusinessException(
                ErrorCode.INVALID_ORDER_STATE,
                "Đơn đang ở trạng thái " + now + " nên không thể thực hiện thao tác này.");
    }

    private static String reason(String raw) {
        String clean = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (clean.length() < 5 || clean.length() > 500) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_INPUT, "Lý do cần từ 5 đến 500 ký tự.");
        }
        return clean;
    }

    private void record(
            SellerOrder order,
            OrderStatus from,
            OrderStatus to,
            String actorType,
            String actor,
            String reason) {
        orders.insertHistory(
                new OrderStatusEvent(
                        UUID.randomUUID(), order.id(), from, to, actorType, actor, reason, null));
    }

    private void publish(String type, SellerOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.id());
        payload.put("shopId", order.shopId());
        payload.put("checkoutGroupId", order.checkoutGroupId());
        outbox.append("order", type, order.id(), json.writeValueAsString(payload));
    }

    private SellerOrderDetail view(SellerOrder order) {
        OrderDetail detail = orderService.open(order);
        OrderDetail.RecipientView r = detail.recipient();
        OrderDetail.RecipientView shown =
                RECIPIENT_VISIBLE.contains(order.status())
                        ? r
                        : new OrderDetail.RecipientView(null, null, null, r.ward(), r.province());
        return new SellerOrderDetail(order, detail.lines(), detail.history(), shown);
    }
}
