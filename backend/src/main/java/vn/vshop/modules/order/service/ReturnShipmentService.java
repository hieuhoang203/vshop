package vn.vshop.modules.order.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.common.security.PiiCipher;
import vn.vshop.modules.order.entity.OrderLine;
import vn.vshop.modules.order.entity.PickupAddress;
import vn.vshop.modules.order.entity.SellerOrder;
import vn.vshop.modules.order.entity.Shipment;
import vn.vshop.modules.order.gateway.ShippingCarrier;
import vn.vshop.modules.order.model.OrderDetail;
import vn.vshop.modules.order.model.ReturnCaseDetail;
import vn.vshop.modules.order.repository.OrderRepository;
import vn.vshop.modules.order.repository.ShipmentRepository;

/**
 * A carrier parcel that brings returned goods from the buyer back to the shop. It follows the same
 * rules as the outbound parcel: one transaction records the intent (a CREATING RETURN shipment),
 * the carrier is called with no lock held, a second transaction records the answer and moves the
 * case to IN_RETURN with the tracking number. A timeout leaves the parcel CREATING and the retry
 * sends the same partner order id, which the carrier answers as a duplicate, so no parcel is
 * created twice. The money still waits for the shop to confirm it received and inspected the goods.
 */
@Service
public class ReturnShipmentService {
    private static final int DEFAULT_WEIGHT_GRAMS = 500;

    private final ShipmentRepository shipments;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final ReturnCaseService returnCases;
    private final ShippingCarrier carrier;
    private final PiiCipher cipher;
    private final TransactionTemplate tx;

    ReturnShipmentService(
            ShipmentRepository shipments,
            OrderRepository orders,
            OrderService orderService,
            ReturnCaseService returnCases,
            ShippingCarrier carrier,
            PiiCipher cipher,
            PlatformTransactionManager transactions) {
        this.shipments = shipments;
        this.orders = orders;
        this.orderService = orderService;
        this.returnCases = returnCases;
        this.carrier = carrier;
        this.cipher = cipher;
        this.tx = new TransactionTemplate(transactions);
    }

    private record Prepared(UUID shipmentId, ShippingCarrier.CreateRequest request) {}

    /** The buyer asks the carrier to collect the goods; {@code district} completes the address. */
    public ReturnCaseDetail create(String subject, UUID caseId, String district) {
        String pickupDistrict = district == null ? "" : district.trim().replaceAll("\\s+", " ");
        if (pickupDistrict.isEmpty() || pickupDistrict.length() > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_RETURN_INPUT,
                    "Cần nhập quận/huyện nơi lấy hàng trả (≤ 100 ký tự).");
        }
        Prepared prepared;
        try {
            prepared = tx.execute(s -> prepare(subject, caseId, pickupDistrict));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    ErrorCode.INVALID_RETURN_STATE,
                    "Vận đơn trả hàng đang được tạo. Thử lại sau giây lát.");
        }
        if (prepared == null) {
            return tx.execute(s -> returnCases.detailForBuyer(subject, caseId));
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
                tx.executeWithoutResult(
                        s -> {
                            shipments.markCreated(
                                    prepared.shipmentId(),
                                    code,
                                    result.fee(),
                                    result.insuranceFee());
                            returnCases.ship(subject, caseId, code);
                        });
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
                            "Chưa nhận được phản hồi từ GHTK; vận đơn có thể đã được tạo. Hãy bấm tạo"
                                    + " lại để đối chiếu, hệ thống sẽ không tạo trùng.");
        }
        return tx.execute(s -> returnCases.detailForBuyer(subject, caseId));
    }

    private Prepared prepare(String subject, UUID caseId, String district) {
        ReturnCaseService.ShippingContext context = returnCases.shippingContext(subject, caseId);
        if (!carrier.configured()) {
            throw new BusinessException(
                    ErrorCode.CARRIER_NOT_CONFIGURED,
                    "Chưa cấu hình kết nối đơn vị vận chuyển. Hãy tự gửi hàng và nhập mã vận đơn.");
        }
        Optional<Shipment> live = shipments.findLiveReturnByOrder(context.orderId());
        if (live.isPresent() && !live.get().status().equals("CREATING")) {
            return null; // already created: the call is idempotent
        }
        if (!context.status().equals("AWAITING_RETURN")) {
            // IN_RETURN without a carrier parcel means the buyer already sent the goods themselves.
            throw new BusinessException(
                    ErrorCode.INVALID_RETURN_STATE,
                    "Hàng trả đã được gửi đi nên không thể tạo vận đơn mới.");
        }
        PickupAddress shop =
                shipments
                        .findPickup(context.shopId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.PICKUP_ADDRESS_REQUIRED,
                                                "Gian hàng chưa có địa chỉ nhận hàng nên chưa tạo được"
                                                        + " vận đơn trả. Hãy tự gửi hàng và nhập mã"
                                                        + " vận đơn."));
        SellerOrder order = orders.findById(context.orderId());
        OrderDetail detail = orderService.open(order);
        int weight =
                shipments
                        .findLiveByOrder(order.id())
                        .map(Shipment::weightGrams)
                        .orElse(DEFAULT_WEIGHT_GRAMS);
        long value = Math.max(order.itemAmount(), 1);
        UUID shipmentId;
        String partnerId;
        Optional<Shipment> failed =
                live.isPresent() ? live : shipments.findFailedReturnByOrder(order.id());
        if (failed.isPresent()) {
            shipmentId = failed.get().id();
            partnerId = failed.get().partnerOrderId();
            shipments.reopen(shipmentId, 0, weight, value);
        } else {
            partnerId = "R-" + order.orderNumber() + "-" + (shipments.countByOrder(order.id()) + 1);
            shipmentId = UUID.randomUUID();
            shipments.insertCreatingReturn(
                    shipmentId,
                    order.id(),
                    order.shopId(),
                    carrier.code(),
                    partnerId,
                    weight,
                    value);
        }
        OrderDetail.RecipientView buyer = detail.recipient();
        List<ShippingCarrier.Item> items = new ArrayList<>();
        List<OrderLine> lines = detail.lines();
        int base = weight / lines.size();
        int extra = weight - base * lines.size();
        for (int i = 0; i < lines.size(); i++) {
            items.add(
                    new ShippingCarrier.Item(
                            lines.get(i).productNameSnapshot(),
                            lines.get(i).quantity(),
                            base + (i == 0 ? extra : 0)));
        }
        String shopPhone =
                cipher.decrypt(
                        shop.phoneCiphertext(),
                        "fulfillment.pickup_address.phone:" + shop.shopId());
        String shopStreet =
                cipher.decrypt(
                        shop.streetCiphertext(),
                        "fulfillment.pickup_address.street:" + shop.shopId());
        return new Prepared(
                shipmentId,
                new ShippingCarrier.CreateRequest(
                        partnerId,
                        0,
                        value,
                        // The parcel is collected from the buyer and delivered to the shop.
                        new ShippingCarrier.Pickup(
                                buyer.name(),
                                buyer.phone(),
                                buyer.street(),
                                buyer.ward(),
                                district,
                                buyer.province()),
                        new ShippingCarrier.Recipient(
                                shop.contactName(),
                                shopPhone,
                                shopStreet,
                                shop.ward(),
                                shop.province()),
                        items,
                        "Trả hàng đơn " + order.orderNumber()));
    }
}
