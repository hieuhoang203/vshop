package vn.vshop.modules.order.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.common.security.PiiCipher;
import vn.vshop.modules.checkout.model.OrderBasis;
import vn.vshop.modules.checkout.model.PricedCart;
import vn.vshop.modules.checkout.service.QuoteService;
import vn.vshop.modules.identity.model.Recipient;
import vn.vshop.modules.identity.service.AccountService;
import vn.vshop.modules.identity.service.AddressService;
import vn.vshop.modules.inventory.entity.StockReservation;
import vn.vshop.modules.inventory.service.ReservationService;
import vn.vshop.modules.merchant.service.ShopService;
import vn.vshop.modules.order.entity.CheckoutGroup;
import vn.vshop.modules.order.entity.OrderLine;
import vn.vshop.modules.order.entity.OrderStatus;
import vn.vshop.modules.order.entity.OrderStatusEvent;
import vn.vshop.modules.order.entity.PaymentMethod;
import vn.vshop.modules.order.entity.SellerOrder;
import vn.vshop.modules.order.model.GroupDetail;
import vn.vshop.modules.order.model.OrderDetail;
import vn.vshop.modules.order.model.OrderSummary;
import vn.vshop.modules.order.repository.OrderRepository;
import vn.vshop.modules.platform.model.CheckoutPolicies;
import vn.vshop.modules.platform.service.OutboxService;
import vn.vshop.modules.platform.service.PolicyService;

/**
 * Places orders from a quote (docs/20 section 4). The whole placement is one database transaction:
 * the cart is locked, the quote is re-validated and the cart re-priced, orders are written, stock
 * is reserved (and, for COD, committed) and the cart is cleared. Any failure, including one shop
 * out of stock, rolls back everything. There is no network call inside it.
 */
@Service
public class OrderService {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final TypeReference<Map<String, String>> STRINGS = new TypeReference<>() {};
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orders;
    private final QuoteService quotes;
    private final PolicyService policies;
    private final ReservationService reservations;
    private final AddressService addresses;
    private final AccountService accounts;
    private final ShopService shops;
    private final OutboxService outbox;
    private final PaymentService payments;
    private final PiiCipher cipher;
    private final ObjectMapper json;
    private final Clock clock;

    OrderService(
            OrderRepository orders,
            QuoteService quotes,
            PolicyService policies,
            ReservationService reservations,
            AddressService addresses,
            AccountService accounts,
            ShopService shops,
            OutboxService outbox,
            PaymentService payments,
            PiiCipher cipher,
            ObjectMapper json,
            Clock clock) {
        this.orders = orders;
        this.quotes = quotes;
        this.policies = policies;
        this.reservations = reservations;
        this.addresses = addresses;
        this.accounts = accounts;
        this.shops = shops;
        this.outbox = outbox;
        this.payments = payments;
        this.cipher = cipher;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public GroupDetail place(
            String subject,
            UUID quoteId,
            PaymentMethod method,
            String idempotencyKey,
            String clientIp) {
        // 1. Lock the cart: serialises this buyer's checkouts and cart edits.
        UUID buyer = quotes.lockCart(subject);

        // 2. Idempotency. Under the lock a duplicate sees the first request's finished result.
        String hash = sha256(quoteId + ":" + method);
        var previous = orders.findGroupByKey(buyer, idempotencyKey);
        if (previous.isPresent()) {
            if (!previous.get().requestHash().equals(hash)) {
                throw new BusinessException(
                        ErrorCode.IDEMPOTENCY_CONFLICT,
                        "Khóa idempotency đã được dùng cho một yêu cầu đặt hàng khác.");
            }
            return detail(buyer, previous.get(), true);
        }
        if (orders.findGroupByQuote(buyer, quoteId).isPresent()) {
            throw new BusinessException(
                    ErrorCode.QUOTE_STALE, "Báo giá này đã được dùng để đặt hàng.");
        }
        boolean psp = method == PaymentMethod.PSP;
        if (psp && !payments.available()) {
            throw new BusinessException(
                    ErrorCode.INVALID_CART_INPUT,
                    "Thanh toán trực tuyến chưa được cấu hình. Vui lòng chọn thanh toán khi nhận hàng.");
        }

        // 3. Policy gate: no approved policy, no checkout.
        CheckoutPolicies approved = policies.checkoutPolicies();
        if (!approved.complete()) {
            throw new BusinessException(
                    ErrorCode.CHECKOUT_POLICY_MISSING,
                    "Chưa thể đặt hàng: thiếu chính sách đã được duyệt "
                            + approved.missing()
                            + ".");
        }

        // 4. Quote still current and cart priced again with nothing changed.
        OrderBasis basis = quotes.prepareOrder(subject, quoteId);
        PricedCart cart = basis.priced();

        // 5. Rules: no buying from a shop you work for; COD limits.
        Set<UUID> shopIds = new java.util.HashSet<>();
        cart.groups().forEach(g -> shopIds.add(g.shopId()));
        if (!shops.memberShops(buyer, shopIds).isEmpty()) {
            throw new BusinessException(
                    ErrorCode.SELF_PURCHASE_NOT_ALLOWED,
                    "Bạn không thể mua sản phẩm của gian hàng mà bạn là thành viên.");
        }
        var cod = approved.cod();
        for (PricedCart.ShopGroup g : psp ? List.<PricedCart.ShopGroup>of() : cart.groups()) {
            if (g.payable() > cod.maxOrderAmount()) {
                throw new BusinessException(
                        ErrorCode.COD_LIMIT_EXCEEDED,
                        "Đơn của "
                                + g.shopName()
                                + " vượt hạn mức thanh toán khi nhận hàng ("
                                + cod.maxOrderAmount()
                                + "đ).");
            }
        }
        long open = psp ? 0 : orders.countOpenOrders(buyer, PaymentMethod.COD.name());
        if (!psp && open + cart.groups().size() > cod.maxOpenOrders()) {
            throw new BusinessException(
                    ErrorCode.COD_LIMIT_EXCEEDED,
                    "Bạn đang có quá nhiều đơn COD chưa hoàn tất (tối đa "
                            + cod.maxOpenOrders()
                            + ").");
        }

        // 6. Write group, orders and lines from the server-side pricing.
        Recipient recipient = addresses.recipient(subject, basis.addressId());
        UUID groupId = UUID.randomUUID();
        OffsetDateTime deadline =
                psp
                        ? OffsetDateTime.now(clock).plusMinutes(approved.payment().paymentMinutes())
                        : null;
        orders.insertGroup(
                new CheckoutGroup(
                        groupId,
                        buyer,
                        quoteId,
                        idempotencyKey,
                        hash,
                        "VND",
                        cart.payableTotal(),
                        method,
                        json.writeValueAsString(approved),
                        null));
        record Reserved(UUID lineId, UUID orderId, UUID skuId, int quantity) {}
        List<Reserved> toReserve = new ArrayList<>();
        List<UUID> orderIds = new ArrayList<>();
        for (PricedCart.ShopGroup g : cart.groups()) {
            UUID orderId = UUID.randomUUID();
            orderIds.add(orderId);
            orders.insertOrder(
                    new SellerOrder(
                            orderId,
                            groupId,
                            g.shopId(),
                            orderNumber(),
                            psp ? OrderStatus.PENDING_PAYMENT : OrderStatus.AWAITING_SELLER,
                            0,
                            g.subtotal(),
                            0,
                            g.shippingFee(),
                            g.payable(),
                            g.shopName(),
                            sealRecipient(orderId, recipient),
                            recipient.province(),
                            recipient.ward(),
                            approved.returns().policyId(),
                            deadline,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null));
            for (PricedCart.Line l : g.lines()) {
                UUID lineId = UUID.randomUUID();
                orders.insertLine(
                        new OrderLine(
                                lineId,
                                orderId,
                                g.shopId(),
                                l.skuId(),
                                l.productId(),
                                l.productSlug(),
                                l.productName(),
                                l.imageKey(),
                                l.sellerSku(),
                                json.writeValueAsString(
                                        l.variantLabel() == null
                                                ? Map.of()
                                                : Map.of("label", l.variantLabel())),
                                l.quantity(),
                                l.unitPrice(),
                                l.lineTotal(),
                                0,
                                l.lineTotal()));
                toReserve.add(new Reserved(lineId, orderId, l.skuId(), l.quantity()));
            }
            orders.insertHistory(
                    new OrderStatusEvent(
                            UUID.randomUUID(),
                            orderId,
                            null,
                            psp ? OrderStatus.PENDING_PAYMENT : OrderStatus.AWAITING_SELLER,
                            "BUYER",
                            subject,
                            psp
                                    ? "Đặt hàng, chờ thanh toán trực tuyến"
                                    : "Đặt hàng, thanh toán khi nhận hàng",
                            null));
        }

        // 7. Reserve in ascending SKU order so two checkouts cannot deadlock each other; COD
        //    consumes the stock at once (BR32).
        Duration ttl =
                Duration.ofMinutes(
                        approved.payment().paymentMinutes()
                                + approved.payment().reservationBufferMinutes());
        toReserve.sort(Comparator.comparing(Reserved::skuId).thenComparing(Reserved::lineId));
        for (Reserved r : toReserve) {
            StockReservation held =
                    reservations.reserve(
                            r.skuId(), r.quantity(), r.lineId(), "order:" + r.lineId(), ttl);
            if (!psp) {
                reservations.commit(held.id());
            }
            orders.linkReservation(r.lineId(), held.id());
        }

        // 8. Event, then the purchased lines leave the cart.
        for (int i = 0; i < cart.groups().size(); i++) {
            PricedCart.ShopGroup g = cart.groups().get(i);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", orderIds.get(i));
            payload.put("checkoutGroupId", groupId);
            payload.put("shopId", g.shopId());
            payload.put("payableAmount", g.payable());
            payload.put("paymentMethod", method.name());
            outbox.append(
                    "order", "OrderPlaced", orderIds.get(i), json.writeValueAsString(payload));
        }
        quotes.clearCart(basis.cartId());
        if (psp) {
            payments.startIntent(groupId, cart.payableTotal(), deadline, clientIp);
        }
        return detail(buyer, orders.findGroup(buyer, groupId).orElseThrow(), false);
    }

    @Transactional
    public GroupDetail group(String subject, UUID groupId) {
        UUID buyer = accounts.requireActiveAccount(subject);
        CheckoutGroup group =
                orders.findGroup(buyer, groupId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.ORDER_NOT_FOUND,
                                                "Không tìm thấy đơn hàng."));
        return detail(buyer, group, false);
    }

    @Transactional
    public OrderDetail order(String subject, UUID orderId) {
        UUID buyer = accounts.requireActiveAccount(subject);
        SellerOrder order =
                orders.findOrder(buyer, orderId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.ORDER_NOT_FOUND,
                                                "Không tìm thấy đơn hàng."));
        return open(order);
    }

    @Transactional
    public List<OrderSummary> list(String subject, int page, int size) {
        UUID buyer = accounts.requireActiveAccount(subject);
        return orders.listSummaries(buyer, size, page * size);
    }

    @Transactional
    public long count(String subject) {
        return orders.countOrders(accounts.requireActiveAccount(subject));
    }

    private GroupDetail detail(UUID buyer, CheckoutGroup group, boolean replayed) {
        return new GroupDetail(
                group,
                orders.findOrdersOfGroup(group.id()).stream().map(this::open).toList(),
                replayed,
                payments.paymentUrl(group).orElse(null));
    }

    public OrderDetail open(SellerOrder order) {
        Map<String, String> r =
                json.readValue(
                        cipher.decrypt(order.recipientSnapshotCiphertext(), context(order.id())),
                        STRINGS);
        return new OrderDetail(
                order,
                orders.findLines(order.id()),
                orders.findHistory(order.id()),
                new OrderDetail.RecipientView(
                        r.get("name"),
                        r.get("phone"),
                        r.get("street"),
                        r.get("ward"),
                        r.get("province")));
    }

    private String sealRecipient(UUID orderId, Recipient r) {
        Map<String, String> plain = new LinkedHashMap<>();
        plain.put("name", r.name());
        plain.put("phone", r.phone());
        plain.put("street", r.street());
        plain.put("ward", r.ward());
        plain.put("province", r.province());
        return cipher.encrypt(json.writeValueAsString(plain), context(orderId));
    }

    private static String context(UUID orderId) {
        return "sales.seller_order.recipient:" + orderId;
    }

    private String orderNumber() {
        StringBuilder sb =
                new StringBuilder("VS")
                        .append(
                                LocalDate.now(clock.withZone(VIETNAM))
                                        .format(DateTimeFormatter.ofPattern("yyMMdd")))
                        .append('-');
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
