package vn.vshop.modules.order.service;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.modules.order.repository.OrderRepository;
import vn.vshop.modules.platform.entity.OutboxEvent;
import vn.vshop.modules.platform.service.NotificationService;
import vn.vshop.modules.platform.service.OutboxEventHandler;

/**
 * Turns order, refund and return events into in-app notifications for the buyer or the shop owner.
 * Text carries order numbers and amounts only. Administrators work from their queues and get none.
 * Delivery is at least once; the dedupe key (event, recipient) makes a repeat harmless.
 */
@Component
public class OrderNotificationHandler implements OutboxEventHandler {
    private final OrderRepository orders;
    private final NotificationService notifications;
    private final ObjectMapper json;

    OrderNotificationHandler(
            OrderRepository orders, NotificationService notifications, ObjectMapper json) {
        this.orders = orders;
        this.notifications = notifications;
        this.json = json;
    }

    @Override
    public void handle(OutboxEvent event) {
        JsonNode p = json.readTree(event.payload());
        UUID orderId = uuid(p.path("orderId").asText(null));
        if (orderId == null) {
            return; // not an order-related event
        }
        Optional<OrderRepository.Parties> found = orders.findParties(orderId);
        if (found.isEmpty()) {
            return;
        }
        var o = found.get();
        String no = o.orderNumber();
        String buyerLink = "/orders/" + orderId;
        switch (event.eventType()) {
            case "OrderPlaced" -> {
                if ("COD".equals(p.path("paymentMethod").asText())) {
                    toShop(
                            event,
                            o,
                            "Đơn mới " + no,
                            "Có đơn thanh toán khi nhận hàng "
                                    + vnd(p.path("payableAmount").asLong())
                                    + " đang chờ bạn xác nhận.",
                            "/seller/orders");
                }
            }
            case "OrderPaid" -> {
                toBuyer(
                        event,
                        o,
                        "Đã nhận thanh toán",
                        "Đơn " + no + " đã được thanh toán, đang chờ người bán xác nhận.",
                        buyerLink);
                toShop(
                        event,
                        o,
                        "Đơn mới " + no,
                        "Đơn đã thanh toán trực tuyến đang chờ bạn xác nhận.",
                        "/seller/orders");
            }
            case "OrderConfirmed" ->
                    toBuyer(
                            event,
                            o,
                            "Người bán đã xác nhận đơn",
                            "Đơn "
                                    + no
                                    + " của "
                                    + o.shopName()
                                    + " đã được xác nhận và đang được chuẩn bị.",
                            buyerLink);
            case "OrderRejected" ->
                    toBuyer(
                            event,
                            o,
                            "Người bán từ chối đơn",
                            "Đơn "
                                    + no
                                    + " không thể giao. Nếu bạn đã thanh toán, hãy nhập tài khoản nhận hoàn tiền trong trang đơn hàng.",
                            buyerLink);
            case "OrderCanceled" -> {
                if ("BUYER".equals(p.path("actor").asText())) {
                    toShop(
                            event,
                            o,
                            "Người mua hủy đơn",
                            "Đơn " + no + " đã bị người mua hủy trước khi bạn xác nhận.",
                            "/seller/orders");
                } else {
                    toBuyer(
                            event,
                            o,
                            "Đơn đã hủy",
                            "Đơn " + no + " đã hủy do quá hạn thanh toán. Hàng đã được nhả lại.",
                            buyerLink);
                }
            }
            case "OrderShipped" ->
                    toBuyer(
                            event,
                            o,
                            "Đơn đang được giao",
                            "Đơn " + no + " đã được bàn giao cho đơn vị vận chuyển.",
                            buyerLink);
            case "OrderDelivered" ->
                    toBuyer(
                            event,
                            o,
                            "Đơn đã giao",
                            "Đơn "
                                    + no
                                    + " đã giao. Bạn có thể yêu cầu trả hàng hoặc hoàn tiền trong thời hạn đổi trả.",
                            buyerLink);
            case "ShipmentProblem" -> {
                toShop(
                        event,
                        o,
                        "Vận đơn có vấn đề",
                        "Vận đơn của đơn " + no + " giao thất bại hoặc đang hoàn về.",
                        "/seller/orders");
                toBuyer(
                        event,
                        o,
                        "Giao hàng gặp sự cố",
                        "Đơn "
                                + no
                                + " giao chưa thành công. Chúng tôi sẽ cập nhật khi có thông tin mới.",
                        buyerLink);
            }
            case "RefundRequested" ->
                    toBuyer(
                            event,
                            o,
                            "Cần thông tin nhận hoàn tiền",
                            "Đơn "
                                    + no
                                    + " được hoàn "
                                    + vnd(p.path("amount").asLong())
                                    + ". Vui lòng nhập tài khoản ngân hàng nhận tiền.",
                            buyerLink);
            case "RefundSucceeded" ->
                    toBuyer(
                            event,
                            o,
                            "Đã hoàn tiền",
                            "Khoản hoàn "
                                    + vnd(p.path("amount").asLong())
                                    + " cho đơn "
                                    + no
                                    + " đã được chuyển khoản.",
                            buyerLink);
            case "ReturnRequested" ->
                    toShop(
                            event,
                            o,
                            "Yêu cầu trả hàng mới",
                            "Người mua yêu cầu trả hàng cho đơn " + no + ". Hãy phản hồi đúng hạn.",
                            "/seller/returns");
            case "ReturnCaseUpdated" ->
                    returnUpdated(
                            event,
                            o,
                            p.path("toStatus").asText(),
                            p.path("actor").asText(),
                            buyerLink);
            default -> {
                // not a notifying event
            }
        }
    }

    private void returnUpdated(
            OutboxEvent event,
            OrderRepository.Parties o,
            String status,
            String actor,
            String buyerLink) {
        String no = o.orderNumber();
        switch (status) {
            case "SELLER_OFFERED" ->
                    toBuyer(
                            event,
                            o,
                            "Người bán đề xuất hoàn tiền",
                            "Người bán đề xuất hoàn tiền cho yêu cầu trả hàng của đơn "
                                    + no
                                    + ". Hãy xem và phản hồi.",
                            buyerLink);
            case "SELLER_REJECTED" ->
                    toBuyer(
                            event,
                            o,
                            "Người bán từ chối yêu cầu",
                            "Yêu cầu trả hàng của đơn "
                                    + no
                                    + " bị người bán từ chối. Bạn có thể nhờ quản trị viên xem xét.",
                            buyerLink);
            case "AWAITING_RETURN" ->
                    toBuyer(
                            event,
                            o,
                            "Hãy gửi trả hàng",
                            "Yêu cầu trả hàng của đơn "
                                    + no
                                    + " đã được chấp nhận. Gửi hàng và nhập mã vận đơn trong trang đơn hàng.",
                            buyerLink);
            case "IN_RETURN" ->
                    toShop(
                            event,
                            o,
                            "Hàng đang được trả về",
                            "Người mua đã gửi trả hàng của đơn " + no + ".",
                            "/seller/returns");
            case "REVIEWING" -> {
                if ("BUYER".equals(actor)) {
                    toShop(
                            event,
                            o,
                            "Yêu cầu chuyển quản trị viên",
                            "Người mua nhờ quản trị viên quyết định yêu cầu trả hàng của đơn "
                                    + no
                                    + ".",
                            "/seller/returns");
                } else {
                    toBuyer(
                            event,
                            o,
                            "Đang chờ quản trị viên",
                            "Yêu cầu của đơn " + no + " đã chuyển cho quản trị viên quyết định.",
                            buyerLink);
                }
            }
            case "REFUND_APPROVED" -> {
                if ("BUYER".equals(actor)) {
                    toShop(
                            event,
                            o,
                            "Người mua đồng ý hoàn tiền",
                            "Người mua đã đồng ý đề xuất hoàn tiền của đơn " + no + ".",
                            "/seller/returns");
                } else {
                    toBuyer(
                            event,
                            o,
                            "Đã duyệt hoàn tiền",
                            "Yêu cầu của đơn "
                                    + no
                                    + " được duyệt hoàn tiền. Hãy nhập tài khoản nhận tiền nếu chưa có.",
                            buyerLink);
                }
            }
            case "REJECTED" -> {
                toBuyer(
                        event,
                        o,
                        "Yêu cầu bị từ chối",
                        "Quản trị viên đã từ chối yêu cầu trả hàng của đơn " + no + ".",
                        buyerLink);
                toShop(
                        event,
                        o,
                        "Yêu cầu trả hàng bị từ chối",
                        "Quản trị viên đã từ chối yêu cầu trả hàng của đơn " + no + ".",
                        "/seller/returns");
            }
            case "CANCELED" ->
                    toShop(
                            event,
                            o,
                            "Người mua hủy yêu cầu",
                            "Người mua đã hủy yêu cầu trả hàng của đơn " + no + ".",
                            "/seller/returns");
            default -> {
                // REQUESTED is announced by ReturnRequested
            }
        }
    }

    private void toBuyer(
            OutboxEvent e, OrderRepository.Parties o, String title, String body, String link) {
        notifications.notify(
                o.buyerAccountId(), e.eventType(), title, body, link, e.id() + ":buyer");
    }

    private void toShop(
            OutboxEvent e, OrderRepository.Parties o, String title, String body, String link) {
        notifications.notify(
                o.shopOwnerAccountId(), e.eventType(), title, body, link, e.id() + ":shop");
    }

    private static String vnd(long amount) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(amount) + "đ";
    }

    private static UUID uuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
