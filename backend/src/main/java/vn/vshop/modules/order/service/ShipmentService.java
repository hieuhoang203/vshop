package vn.vshop.modules.order.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.common.security.PiiCipher;
import vn.vshop.modules.merchant.service.ShopService;
import vn.vshop.modules.order.entity.OrderLine;
import vn.vshop.modules.order.entity.OrderStatus;
import vn.vshop.modules.order.entity.OrderStatusEvent;
import vn.vshop.modules.order.entity.PickupAddress;
import vn.vshop.modules.order.entity.SellerOrder;
import vn.vshop.modules.order.entity.Shipment;
import vn.vshop.modules.order.gateway.ShippingCarrier;
import vn.vshop.modules.order.model.OrderDetail;
import vn.vshop.modules.order.model.SellerOrderDetail;
import vn.vshop.modules.order.repository.OrderRepository;
import vn.vshop.modules.order.repository.ShipmentRepository;
import vn.vshop.modules.platform.service.OutboxService;
import vn.vshop.modules.platform.service.PolicyService;

/**
 * Hands READY_TO_SHIP orders to the carrier and follows them by webhook. The carrier is called
 * outside any database transaction: one transaction records the intent (a CREATING shipment), the
 * network call happens with no lock held, a second transaction records the answer. A timeout leaves
 * the shipment CREATING, and the retry sends the same partner order id, which the carrier treats as
 * a duplicate and answers with the parcel it already has, so a parcel is never created twice.
 */
@Service
public class ShipmentService {
    private static final Pattern PHONE = Pattern.compile("^0\\d{9,10}$");

    private final ShipmentRepository shipments;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final OrderFulfillmentService fulfillment;
    private final ShopService shops;
    private final ShippingCarrier carrier;
    private final PolicyService policies;
    private final OutboxService outbox;
    private final PiiCipher cipher;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    ShipmentService(
            ShipmentRepository shipments,
            OrderRepository orders,
            OrderService orderService,
            OrderFulfillmentService fulfillment,
            ShopService shops,
            ShippingCarrier carrier,
            PolicyService policies,
            OutboxService outbox,
            PiiCipher cipher,
            ObjectMapper json,
            PlatformTransactionManager transactions) {
        this.shipments = shipments;
        this.orders = orders;
        this.orderService = orderService;
        this.fulfillment = fulfillment;
        this.shops = shops;
        this.carrier = carrier;
        this.policies = policies;
        this.outbox = outbox;
        this.cipher = cipher;
        this.json = json;
        this.tx = new TransactionTemplate(transactions);
    }

    // ------------------------------------------------------------------ pickup address

    /** Plain pickup details for the owner, decrypted. */
    public record Pickup(
            String contactName,
            String phone,
            String street,
            String ward,
            String district,
            String province) {}

    @Transactional
    public Optional<Pickup> pickup(String subject, UUID shopId) {
        shops.ownedShop(shopId, subject);
        return shipments.findPickup(shopId).map(this::open);
    }

    @Transactional
    public Pickup savePickup(String subject, UUID shopId, Pickup in) {
        shops.ownedShop(shopId, subject);
        String name = collapse(in.contactName());
        String street = collapse(in.street());
        String ward = collapse(in.ward());
        String district = collapse(in.district());
        String province = collapse(in.province());
        String phone = in.phone() == null ? "" : in.phone().replaceAll("[\\s.\\-()]", "");
        if (phone.startsWith("+84")) {
            phone = "0" + phone.substring(3);
        }
        require(name.length() >= 2 && name.length() <= 100, "Tên người liên hệ cần 2–100 ký tự.");
        require(PHONE.matcher(phone).matches(), "Số điện thoại cần 10–11 chữ số, bắt đầu bằng 0.");
        require(street.length() >= 5 && street.length() <= 200, "Địa chỉ cần 5–200 ký tự.");
        require(!ward.isEmpty() && ward.length() <= 100, "Cần nhập phường/xã.");
        require(!district.isEmpty() && district.length() <= 100, "Cần nhập quận/huyện.");
        require(!province.isEmpty() && province.length() <= 100, "Cần nhập tỉnh/thành phố.");
        shipments.savePickup(
                new PickupAddress(
                        shopId,
                        name,
                        cipher.encrypt(phone, context("phone", shopId)),
                        cipher.encrypt(street, context("street", shopId)),
                        ward,
                        district,
                        province,
                        null));
        return new Pickup(name, phone, street, ward, district, province);
    }

    // ------------------------------------------------------------------ create / cancel

    private record Prepared(UUID shipmentId, ShippingCarrier.CreateRequest request, UUID orderId) {}

    public SellerOrderDetail create(String subject, UUID shopId, UUID orderId, int weightGrams) {
        if (weightGrams < 50 || weightGrams > 50_000) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_INPUT, "Khối lượng kiện hàng cần từ 50g đến 50.000g.");
        }
        Prepared prepared;
        try {
            prepared = tx.execute(status -> prepare(subject, shopId, orderId, weightGrams));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATE,
                    "Đơn đang được tạo vận đơn. Thử lại sau giây lát.");
        }
        if (prepared == null) {
            return tx.execute(s -> fulfillment.get(subject, shopId, orderId));
        }
        ShippingCarrier.CreateResult result = carrier.createShipment(prepared.request());
        String tracking = result.trackingNumber();
        if (result.outcome() == ShippingCarrier.Outcome.DUPLICATE
                && (tracking == null || tracking.isBlank())) {
            tracking = carrier.lookup(prepared.request().partnerOrderId());
        }
        switch (result.outcome()) {
            case CREATED, DUPLICATE -> {
                if (tracking == null || tracking.isBlank()) {
                    throw new BusinessException(
                            ErrorCode.CARRIER_UNAVAILABLE,
                            "Chưa xác định được mã vận đơn từ GHTK. Thử lại sau để đối chiếu.");
                }
                String code = tracking;
                tx.executeWithoutResult(s -> record(prepared, code, result, subject));
            }
            case REJECTED -> {
                tx.executeWithoutResult(
                        s -> shipments.markFailed(prepared.shipmentId(), result.message()));
                throw new BusinessException(
                        ErrorCode.CARRIER_REJECTED, "GHTK từ chối đơn: " + result.message());
            }
            case UNKNOWN ->
                    throw new BusinessException(
                            ErrorCode.CARRIER_UNAVAILABLE,
                            "Chưa nhận được phản hồi từ GHTK; đơn có thể đã được tạo. Hãy bấm tạo lại"
                                    + " để đối chiếu, hệ thống sẽ không tạo trùng.");
        }
        return tx.execute(s -> fulfillment.get(subject, shopId, orderId));
    }

    private Prepared prepare(String subject, UUID shopId, UUID orderId, int weightGrams) {
        shops.ownedShop(shopId, subject);
        SellerOrder order =
                orders.findOrderOfShop(shopId, orderId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.ORDER_NOT_FOUND,
                                                "Không tìm thấy đơn hàng."));
        if (!carrier.configured()) {
            throw new BusinessException(
                    ErrorCode.CARRIER_NOT_CONFIGURED,
                    "Chưa cấu hình kết nối đơn vị vận chuyển. Dùng chế độ giao thủ công.");
        }
        Optional<Shipment> live = shipments.findLiveByOrder(orderId);
        if (live.isPresent() && !live.get().status().equals("CREATING")) {
            return null; // already created: the call is idempotent
        }
        if (order.status() != OrderStatus.READY_TO_SHIP) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATE,
                    "Đơn đang ở trạng thái " + order.status() + " nên không thể tạo vận đơn.");
        }
        PickupAddress pickup =
                shipments
                        .findPickup(shopId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.PICKUP_ADDRESS_REQUIRED,
                                                "Hãy nhập địa chỉ lấy hàng của gian hàng trước."));
        OrderDetail detail = orderService.open(order);
        long cod =
                orders.paymentMethodOfGroup(order.checkoutGroupId()).equals("COD")
                        ? order.payableAmount()
                        : 0;
        long value = Math.max(order.itemAmount(), 1);
        UUID shipmentId;
        String partnerId;
        if (live.isPresent()) {
            shipmentId = live.get().id();
            partnerId = live.get().partnerOrderId();
            shipments.reopen(shipmentId, cod, weightGrams, value);
        } else {
            Optional<Shipment> failed = shipments.findFailedByOrder(orderId);
            if (failed.isPresent()) {
                shipmentId = failed.get().id();
                partnerId = failed.get().partnerOrderId();
                shipments.reopen(shipmentId, cod, weightGrams, value);
            } else {
                long previous = shipments.countByOrder(orderId);
                partnerId = order.orderNumber() + (previous == 0 ? "" : "-" + (previous + 1));
                shipmentId = UUID.randomUUID();
                shipments.insertCreating(
                        shipmentId,
                        orderId,
                        shopId,
                        carrier.code(),
                        partnerId,
                        cod,
                        weightGrams,
                        value);
            }
        }
        OrderDetail.RecipientView r = detail.recipient();
        List<ShippingCarrier.Item> items = new ArrayList<>();
        List<OrderLine> lines = detail.lines();
        int base = weightGrams / lines.size();
        int extra = weightGrams - base * lines.size();
        for (int i = 0; i < lines.size(); i++) {
            items.add(
                    new ShippingCarrier.Item(
                            lines.get(i).productNameSnapshot(),
                            lines.get(i).quantity(),
                            base + (i == 0 ? extra : 0)));
        }
        return new Prepared(
                shipmentId,
                new ShippingCarrier.CreateRequest(
                        partnerId,
                        cod,
                        value,
                        new ShippingCarrier.Pickup(
                                pickup.contactName(),
                                cipher.decrypt(pickup.phoneCiphertext(), context("phone", shopId)),
                                cipher.decrypt(
                                        pickup.streetCiphertext(), context("street", shopId)),
                                pickup.ward(),
                                pickup.district(),
                                pickup.province()),
                        new ShippingCarrier.Recipient(
                                r.name(), r.phone(), r.street(), r.ward(), r.province()),
                        items,
                        "Đơn " + order.orderNumber()),
                orderId);
    }

    private void record(
            Prepared prepared,
            String tracking,
            ShippingCarrier.CreateResult result,
            String subject) {
        SellerOrder order = orders.findById(prepared.orderId());
        shipments.markCreated(prepared.shipmentId(), tracking, result.fee(), result.insuranceFee());
        if (!orders.markShipped(order.id(), tracking)) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATE,
                    "Đơn đã đổi trạng thái trong lúc tạo vận đơn; cần đối chiếu mã "
                            + tracking
                            + ".");
        }
        orders.insertHistory(
                new OrderStatusEvent(
                        UUID.randomUUID(),
                        order.id(),
                        OrderStatus.READY_TO_SHIP,
                        OrderStatus.IN_FULFILLMENT,
                        "SELLER",
                        subject,
                        "Đã tạo vận đơn " + carrier.code() + " " + tracking,
                        null));
        publish("OrderShipped", order);
    }

    public SellerOrderDetail cancel(String subject, UUID shopId, UUID orderId) {
        Shipment shipment =
                tx.execute(
                        s -> {
                            shops.ownedShop(shopId, subject);
                            return shipments
                                    .findLiveByOrder(orderId)
                                    .filter(x -> x.status().equals("CREATED"))
                                    .orElseThrow(
                                            () ->
                                                    new BusinessException(
                                                            ErrorCode.INVALID_ORDER_STATE,
                                                            "Không có vận đơn nào có thể hủy."));
                        });
        ShippingCarrier.CancelResult result = carrier.cancel(shipment.trackingNumber());
        if (!result.canceled()) {
            throw new BusinessException(
                    ErrorCode.CARRIER_REJECTED, "GHTK không hủy được vận đơn: " + result.message());
        }
        tx.executeWithoutResult(
                s -> {
                    shipments.cancel(shipment.id());
                    if (orders.reopenForShipping(orderId)) {
                        orders.insertHistory(
                                new OrderStatusEvent(
                                        UUID.randomUUID(),
                                        orderId,
                                        OrderStatus.IN_FULFILLMENT,
                                        OrderStatus.READY_TO_SHIP,
                                        "SELLER",
                                        subject,
                                        "Đã hủy vận đơn " + shipment.trackingNumber(),
                                        null));
                    }
                });
        return tx.execute(s -> fulfillment.get(subject, shopId, orderId));
    }

    // ------------------------------------------------------------------ webhook

    /**
     * Applies one carrier status report. Returns false when the secret is wrong (nothing is
     * processed). Unknown parcels and repeated reports are accepted and ignored.
     */
    @Transactional
    public boolean handleWebhook(String token, Map<String, ?> payload) {
        if (!carrier.verifyWebhookToken(token)) {
            return false;
        }
        ShippingCarrier.Event event = carrier.parseWebhook(payload);
        Optional<Shipment> found = shipments.findByTracking(carrier.code(), event.trackingNumber());
        if (found.isEmpty()) {
            found = shipments.findByPartnerOrder(carrier.code(), event.partnerOrderId());
        }
        if (found.isEmpty()) {
            return true;
        }
        Shipment shipment = found.get();
        String normalized = normalize(event.statusId());
        boolean first =
                shipments.insertEvent(
                        shipment.id(),
                        event.trackingNumber()
                                + ":"
                                + event.statusId()
                                + ":"
                                + event.occurredAt()
                                + ":"
                                + event.reasonCode(),
                        Integer.toString(event.statusId()),
                        normalized,
                        event.reasonCode(),
                        event.reason(),
                        event.occurredAt());
        if (!first || normalized.equals("UNKNOWN")) {
            return true;
        }
        if ("RETURN".equals(shipment.direction())) {
            applyReturnEvent(shipment, normalized, event.statusId());
            return true;
        }
        if (normalized.equals("CANCELED")) {
            shipments.cancel(shipment.id());
            if (orders.reopenForShipping(shipment.sellerOrderId())) {
                history(
                        shipment,
                        OrderStatus.IN_FULFILLMENT,
                        OrderStatus.READY_TO_SHIP,
                        "GHTK báo hủy vận đơn");
            }
            return true;
        }
        shipments.advance(shipment.id(), normalized, event.statusId());
        SellerOrder order = orders.findById(shipment.sellerOrderId());
        if (normalized.equals("DELIVERED")
                && orders.markDelivered(
                        order.id(), policies.returnWindowDays(order.returnPolicyId()))) {
            history(
                    shipment,
                    OrderStatus.IN_FULFILLMENT,
                    OrderStatus.DELIVERED,
                    "Đơn vị vận chuyển xác nhận đã giao");
            publish("OrderDelivered", order);
        } else if (normalized.equals("DELIVERY_FAILED")
                || normalized.equals("RETURNING")
                || normalized.equals("RETURNED")) {
            publish("ShipmentProblem", order);
        }
        return true;
    }

    /**
     * A return parcel never changes the order. The shop is told when it arrives (to inspect and
     * confirm the goods) and both sides when it fails; the case itself moves only by people.
     */
    private void applyReturnEvent(Shipment shipment, String normalized, int statusId) {
        SellerOrder order = orders.findById(shipment.sellerOrderId());
        if (normalized.equals("CANCELED")) {
            shipments.cancel(shipment.id());
            publish("ReturnShipmentProblem", order);
            return;
        }
        shipments.advance(shipment.id(), normalized, statusId);
        if (normalized.equals("DELIVERED")) {
            publish("ReturnParcelDelivered", order);
        } else if (normalized.equals("DELIVERY_FAILED")
                || normalized.equals("RETURNING")
                || normalized.equals("RETURNED")) {
            publish("ReturnShipmentProblem", order);
        }
    }

    /** GHTK status_id to our normalized status; UNKNOWN means we only keep the raw event. */
    static String normalize(int statusId) {
        return switch (statusId) {
            case -1 -> "CANCELED";
            case 1, 2, 7, 8, 12, 127, 128 -> "CREATED";
            case 3, 123 -> "PICKED_UP";
            case 4 -> "IN_TRANSIT";
            case 5, 6, 45 -> "DELIVERED";
            case 9, 10, 49, 410 -> "DELIVERY_FAILED";
            case 20 -> "RETURNING";
            case 11, 21 -> "RETURNED";
            default -> "UNKNOWN";
        };
    }

    // ------------------------------------------------------------------ helpers

    private void history(Shipment shipment, OrderStatus from, OrderStatus to, String reason) {
        orders.insertHistory(
                new OrderStatusEvent(
                        UUID.randomUUID(),
                        shipment.sellerOrderId(),
                        from,
                        to,
                        "SYSTEM",
                        null,
                        reason,
                        null));
    }

    private void publish(String type, SellerOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.id());
        payload.put("shopId", order.shopId());
        payload.put("checkoutGroupId", order.checkoutGroupId());
        outbox.append("order", type, order.id(), json.writeValueAsString(payload));
    }

    private Pickup open(PickupAddress a) {
        return new Pickup(
                a.contactName(),
                cipher.decrypt(a.phoneCiphertext(), context("phone", a.shopId())),
                cipher.decrypt(a.streetCiphertext(), context("street", a.shopId())),
                a.ward(),
                a.district(),
                a.province());
    }

    private static String context(String column, UUID shopId) {
        return "fulfillment.pickup_address." + column + ":" + shopId;
    }

    private static String collapse(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new BusinessException(ErrorCode.INVALID_PICKUP_INPUT, message);
        }
    }
}
