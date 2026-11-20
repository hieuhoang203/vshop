package vn.vshop.modules.order.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.modules.inventory.service.ReservationService;
import vn.vshop.modules.order.entity.CheckoutGroup;
import vn.vshop.modules.order.entity.OrderStatus;
import vn.vshop.modules.order.entity.OrderStatusEvent;
import vn.vshop.modules.order.entity.PaymentAttempt;
import vn.vshop.modules.order.entity.PaymentIntent;
import vn.vshop.modules.order.entity.SellerOrder;
import vn.vshop.modules.order.gateway.PaymentGateway;
import vn.vshop.modules.order.repository.OrderRepository;
import vn.vshop.modules.order.repository.PaymentRepository;
import vn.vshop.modules.platform.service.OutboxService;

/**
 * Online payment of a checkout group (docs/20 sections 4, 6 and 8). The browser coming back from
 * the provider proves nothing; only a callback whose signature and amount check out moves an order,
 * in one transaction with the charge, the allocation, the stock commit and the outbox event. A
 * payment that arrives after the order was canceled is recorded and put in a review queue, never
 * used to revive the order.
 */
@Service
public class PaymentService {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentGateway gateway;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final ReservationService reservations;
    private final OutboxService outbox;
    private final ObjectMapper json;
    private final Clock clock;
    private final String returnBase;

    PaymentService(
            PaymentGateway gateway,
            PaymentRepository payments,
            OrderRepository orders,
            ReservationService reservations,
            OutboxService outbox,
            ObjectMapper json,
            Clock clock,
            @Value("${vshop.payment.return-base-url:http://localhost:3100}") String returnBase) {
        this.gateway = gateway;
        this.payments = payments;
        this.orders = orders;
        this.reservations = reservations;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
        this.returnBase = returnBase;
    }

    /** False until the provider credentials are configured. */
    public boolean available() {
        return gateway.configured();
    }

    /** Creates the intent and the first attempt for a new group. Inside the placing transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void startIntent(UUID groupId, long amount, OffsetDateTime deadline, String clientIp) {
        UUID intentId = UUID.randomUUID();
        payments.insertIntent(intentId, groupId, reference("PI"), amount, deadline);
        payments.insertAttempt(
                UUID.randomUUID(),
                intentId,
                groupId,
                gateway.provider(),
                reference("PA"),
                safeIp(clientIp),
                deadline);
    }

    /**
     * The URL to pay at, rebuilt from stored data so a retried request gets the same one. Empty
     * once the group is no longer waiting for payment.
     */
    @Transactional
    public Optional<String> paymentUrl(CheckoutGroup group) {
        if (!gateway.configured()) {
            return Optional.empty();
        }
        return payments.findIntentByGroup(group.id())
                .filter(i -> i.status().equals("CREATED"))
                .flatMap(i -> payments.findLatestAttempt(i.id()).map(a -> url(i, a)))
                .flatMap(Optional::ofNullable);
    }

    private String url(PaymentIntent intent, PaymentAttempt attempt) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!attempt.status().equals("CREATED") || !attempt.expiresAt().isAfter(now)) {
            return null;
        }
        return gateway.paymentUrl(
                new PaymentGateway.Session(
                        attempt.merchantAttemptReference(),
                        intent.requestedAmount(),
                        attempt.createdAt(),
                        attempt.expiresAt(),
                        attempt.clientIp(),
                        "Thanh toan don hang VShop",
                        returnBase + "/checkout/result/" + intent.checkoutGroupId()));
    }

    /**
     * Handles one provider callback and returns the reply the provider expects (RspCode and
     * Message). Repeated deliveries are harmless: a charge is recorded once per provider reference.
     */
    @Transactional
    public Map<String, String> handleCallback(Map<String, String> parameters) {
        PaymentGateway.Callback cb = gateway.verify(parameters);
        if (!cb.validSignature()) {
            return reply("97", "Invalid signature");
        }
        Optional<PaymentAttempt> found =
                payments.findAttemptByReferenceForUpdate(cb.merchantReference());
        if (found.isEmpty()) {
            return reply("01", "Order not found");
        }
        PaymentAttempt attempt = found.get();
        PaymentIntent intent = payments.findIntentForUpdate(attempt.intentId()).orElseThrow();
        payments.insertEvent(
                gateway.provider(),
                cb.merchantReference() + ":" + cb.providerTransactionNo() + ":" + cb.responseCode(),
                cb.merchantReference(),
                cb.success() ? "PAID" : "NOT_PAID");
        if (cb.amountVnd() != intent.requestedAmount()) {
            return reply("04", "Invalid amount");
        }
        if (attempt.status().equals("SUCCEEDED")) {
            return reply("02", "Order already confirmed");
        }
        if (!cb.success()) {
            if (attempt.status().equals("CREATED")) {
                payments.setAttemptStatus(attempt.id(), "FAILED", null);
            }
            return reply("00", "Confirm Success");
        }
        UUID chargeId = UUID.randomUUID();
        if (!payments.insertCharge(
                chargeId,
                attempt.id(),
                attempt.checkoutGroupId(),
                gateway.provider(),
                cb.providerTransactionNo(),
                cb.amountVnd())) {
            return reply("02", "Order already confirmed");
        }
        payments.setAttemptStatus(attempt.id(), "SUCCEEDED", cb.providerTransactionNo());

        List<SellerOrder> group = orders.findOrdersOfGroup(attempt.checkoutGroupId());
        boolean usable =
                group.stream().allMatch(o -> o.status() == OrderStatus.PENDING_PAYMENT)
                        && group.stream()
                                .flatMap(o -> orders.findReservationIds(o.id()).stream())
                                .allMatch(reservations::isHeld);
        if (!usable) {
            // Paid after the order was canceled or its stock released: keep the money trail and
            // leave the decision (refund) to a person. The order is not revived.
            payments.setIntentStatus(intent.id(), "UNKNOWN");
            payments.insertReview(chargeId, attempt.checkoutGroupId(), "PAID_AFTER_EXPIRY");
            publish(
                    "PaymentNeedsReview",
                    attempt.checkoutGroupId(),
                    attempt.checkoutGroupId(),
                    null);
            return reply("00", "Confirm Success");
        }
        for (SellerOrder order : group) {
            payments.insertAllocation(
                    UUID.randomUUID(),
                    chargeId,
                    order.id(),
                    attempt.checkoutGroupId(),
                    order.payableAmount());
            orders.transition(
                    order.id(), OrderStatus.PENDING_PAYMENT, OrderStatus.AWAITING_SELLER, null);
            for (UUID reservationId : orders.findReservationIds(order.id())) {
                reservations.commit(reservationId);
            }
            orders.insertHistory(
                    new OrderStatusEvent(
                            UUID.randomUUID(),
                            order.id(),
                            OrderStatus.PENDING_PAYMENT,
                            OrderStatus.AWAITING_SELLER,
                            "SYSTEM",
                            null,
                            "Đã nhận thanh toán trực tuyến",
                            null));
            publish("OrderPaid", order.id(), attempt.checkoutGroupId(), order.shopId());
        }
        payments.setIntentStatus(intent.id(), "PAID");
        return reply("00", "Confirm Success");
    }

    /**
     * Cancels orders whose payment deadline passed without a payment and gives their stock back.
     * Safe to repeat. Known gap: the provider is not asked first whether a payment is in flight; a
     * late success is handled as PAID_AFTER_EXPIRY instead.
     */
    @Transactional
    public int expireUnpaid(int limit) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<PaymentIntent> due = payments.lockOverdueIntents(now, limit);
        for (PaymentIntent intent : due) {
            for (SellerOrder order : orders.findOrdersOfGroup(intent.checkoutGroupId())) {
                if (orders.markCanceled(
                        order.id(), OrderStatus.PENDING_PAYMENT, "Quá hạn thanh toán")) {
                    for (UUID reservationId : orders.findReservationIds(order.id())) {
                        reservations.release(reservationId);
                    }
                    orders.insertHistory(
                            new OrderStatusEvent(
                                    UUID.randomUUID(),
                                    order.id(),
                                    OrderStatus.PENDING_PAYMENT,
                                    OrderStatus.CANCELED,
                                    "SYSTEM",
                                    null,
                                    "Quá hạn thanh toán",
                                    null));
                    publish("OrderCanceled", order.id(), intent.checkoutGroupId(), order.shopId());
                }
            }
            payments.findLatestAttempt(intent.id())
                    .filter(a -> a.status().equals("CREATED"))
                    .ifPresent(a -> payments.setAttemptStatus(a.id(), "EXPIRED", null));
            payments.setIntentStatus(intent.id(), "CANCELED");
        }
        return due.size();
    }

    private void publish(String type, UUID aggregateId, UUID groupId, UUID shopId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkoutGroupId", groupId);
        if (shopId != null) {
            payload.put("shopId", shopId);
        }
        outbox.append("order", type, aggregateId, json.writeValueAsString(payload));
    }

    private static Map<String, String> reply(String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("RspCode", code);
        body.put("Message", message);
        return body;
    }

    private String reference(String prefix) {
        StringBuilder sb =
                new StringBuilder(prefix)
                        .append(
                                OffsetDateTime.now(clock)
                                        .atZoneSameInstant(VIETNAM)
                                        .format(DateTimeFormatter.ofPattern("yyMMddHHmmss")));
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String safeIp(String ip) {
        return ip != null && ip.length() >= 7 && ip.length() <= 45 && ip.matches("[0-9a-fA-F:.]+")
                ? ip
                : "127.0.0.1";
    }
}
