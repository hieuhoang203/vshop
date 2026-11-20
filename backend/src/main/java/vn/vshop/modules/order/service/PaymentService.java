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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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

    private final List<PaymentGateway> gateways;
    private final PaymentGateway gateway;
    private final TransactionTemplate tx;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final ReservationService reservations;
    private final OutboxService outbox;
    private final RefundService refunds;
    private final ObjectMapper json;
    private final Clock clock;
    private final String returnBase;

    PaymentService(
            List<PaymentGateway> gateways,
            PlatformTransactionManager transactions,
            PaymentRepository payments,
            OrderRepository orders,
            ReservationService reservations,
            OutboxService outbox,
            RefundService refunds,
            ObjectMapper json,
            Clock clock,
            @Value("${vshop.payment.return-base-url:http://localhost:3100}") String returnBase,
            @Value("${vshop.payment.provider:payos}") String provider) {
        this.gateways = gateways;
        this.gateway =
                gateways.stream()
                        .filter(g -> g.provider().equalsIgnoreCase(provider))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Unknown payment provider " + provider));
        this.tx = new TransactionTemplate(transactions);
        this.payments = payments;
        this.orders = orders;
        this.reservations = reservations;
        this.outbox = outbox;
        this.refunds = refunds;
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
        newAttempt(intentId, groupId, deadline, clientIp);
    }

    private void newAttempt(
            UUID intentId, UUID groupId, OffsetDateTime expiresAt, String clientIp) {
        String reference =
                gateway.provider().equals("PAYOS")
                        ? Long.toString(payments.nextOrderCode())
                        : reference("PA");
        payments.insertAttempt(
                UUID.randomUUID(),
                intentId,
                groupId,
                gateway.provider(),
                reference,
                safeIp(clientIp),
                expiresAt);
    }

    /**
     * The page to pay at. Creating it may call the provider over the network, so this must be
     * called outside any database transaction (it opens its own short ones). It is safe to call
     * again and again: a stored page is returned as it is; a provider that already knows the
     * reference but gave us no page gets a fresh attempt. Empty once the group is no longer waiting
     * for payment.
     */
    public Optional<String> ensureSession(CheckoutGroup group) {
        if (!gateway.configured()) {
            return Optional.empty();
        }
        for (int round = 0; round < 3; round++) {
            PaymentIntent intent =
                    tx.execute(x -> payments.findIntentByGroup(group.id()).orElse(null));
            if (intent == null || !intent.status().equals("CREATED")) {
                return Optional.empty();
            }
            PaymentAttempt attempt =
                    tx.execute(x -> payments.findLatestAttempt(intent.id()).orElse(null));
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (attempt == null
                    || !attempt.status().equals("CREATED")
                    || !attempt.expiresAt().isAfter(now)
                    || !attempt.provider().equals(gateway.provider())) {
                return Optional.empty();
            }
            if (attempt.checkoutUrl() != null) {
                return Optional.of(attempt.checkoutUrl());
            }
            PaymentGateway.SessionResult result =
                    gateway.createSession(
                            new PaymentGateway.Session(
                                    attempt.merchantAttemptReference(),
                                    intent.requestedAmount(),
                                    attempt.createdAt(),
                                    attempt.expiresAt(),
                                    attempt.clientIp(),
                                    "Thanh toan don hang VShop",
                                    returnBase + "/checkout/result/" + group.id()));
            switch (result.outcome()) {
                case CREATED -> {
                    Boolean stored =
                            tx.execute(
                                    x ->
                                            payments.setCheckout(
                                                    attempt.id(),
                                                    result.url(),
                                                    result.providerReference()));
                    if (Boolean.TRUE.equals(stored)) {
                        return Optional.of(result.url());
                    }
                    // Another request stored its page first: loop and return that one.
                }
                case DUPLICATE ->
                        tx.executeWithoutResult(
                                x -> {
                                    var current = payments.findLatestAttempt(intent.id());
                                    if (current.isPresent()
                                            && current.get().id().equals(attempt.id())
                                            && current.get().checkoutUrl() == null) {
                                        // The provider has this reference but we never got its
                                        // page:
                                        // abandon it and ask again with a new one.
                                        payments.setAttemptStatus(attempt.id(), "EXPIRED", null);
                                        newAttempt(
                                                intent.id(),
                                                group.id(),
                                                intent.paymentDeadline(),
                                                attempt.clientIp());
                                    }
                                });
                case FAILED -> {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Handles one provider callback and returns the reply the provider expects (RspCode and
     * Message). Repeated deliveries are harmless: a charge is recorded once per provider reference.
     */
    @Transactional
    public Map<String, String> handleCallback(String provider, Map<String, ?> parameters) {
        PaymentGateway callbackGateway =
                gateways.stream()
                        .filter(g -> g.provider().equalsIgnoreCase(provider))
                        .findFirst()
                        .orElseThrow();
        PaymentGateway.Callback cb = callbackGateway.verify(parameters);
        if (!cb.validSignature()) {
            return reply("97", "Invalid signature");
        }
        Optional<PaymentAttempt> found =
                payments.findAttemptByReferenceForUpdate(cb.merchantReference());
        if (found.isEmpty()) {
            return reply("01", "Order not found");
        }
        PaymentAttempt attempt = found.get();
        if (!attempt.provider().equals(callbackGateway.provider())) {
            return reply("01", "Order not found");
        }
        PaymentIntent intent = payments.findIntentForUpdate(attempt.intentId()).orElseThrow();
        payments.insertEvent(
                callbackGateway.provider(),
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
                callbackGateway.provider(),
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
            // The money belongs to these orders even though they are canceled: allocate it, then
            // refund it, so the trail from charge to refund is complete.
            for (SellerOrder order : group) {
                payments.insertAllocation(
                        UUID.randomUUID(),
                        chargeId,
                        order.id(),
                        attempt.checkoutGroupId(),
                        order.payableAmount());
                refunds.createForOrder(order.id(), "PAID_AFTER_EXPIRY");
            }
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
