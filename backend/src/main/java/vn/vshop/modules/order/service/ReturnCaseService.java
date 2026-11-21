package vn.vshop.modules.order.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.modules.identity.service.AccountService;
import vn.vshop.modules.inventory.service.ReservationService;
import vn.vshop.modules.merchant.service.ShopService;
import vn.vshop.modules.order.entity.OrderLine;
import vn.vshop.modules.order.entity.OrderStatus;
import vn.vshop.modules.order.entity.ReturnCase;
import vn.vshop.modules.order.entity.SellerOrder;
import vn.vshop.modules.order.model.ReturnCaseDetail;
import vn.vshop.modules.order.repository.OrderRepository;
import vn.vshop.modules.order.repository.ReturnCaseRepository;
import vn.vshop.modules.order.repository.ReturnCaseRepository.Change;
import vn.vshop.modules.platform.service.AuditEventService;
import vn.vshop.modules.platform.service.OutboxService;
import vn.vshop.modules.platform.service.PolicyService;

/**
 * Return cases after delivery (docs/24, R1). The buyer opens one for a delivered order inside the
 * return window; the shop offers money back, asks for the goods or rejects; an administrator
 * decides what the shop did not settle. Every step is a conditional status change, so two people
 * acting at once have one winner. An approved case creates a refund in the R0 ledger in the same
 * transaction. Return shipping is manual here: the buyer types a tracking code.
 */
@Service
public class ReturnCaseService {
    private static final Set<String> REASONS =
            Set.of(
                    "NOT_RECEIVED",
                    "MISSING_ITEMS",
                    "WRONG_ITEM",
                    "DAMAGED",
                    "DEFECTIVE",
                    "NOT_AS_DESCRIBED",
                    "FAKE",
                    "CHANGED_MIND");
    private static final Pattern TRACKING = Pattern.compile("^[A-Za-z0-9._-]{3,100}$");

    private final ReturnCaseRepository cases;
    private final OrderRepository orders;
    private final AccountService accounts;
    private final ShopService shops;
    private final PolicyService policies;
    private final RefundService refunds;
    private final ReservationService reservations;
    private final OutboxService outbox;
    private final AuditEventService audit;
    private final ObjectMapper json;
    private final Clock clock;

    ReturnCaseService(
            ReturnCaseRepository cases,
            OrderRepository orders,
            AccountService accounts,
            ShopService shops,
            PolicyService policies,
            RefundService refunds,
            ReservationService reservations,
            OutboxService outbox,
            AuditEventService audit,
            ObjectMapper json,
            Clock clock) {
        this.cases = cases;
        this.orders = orders;
        this.accounts = accounts;
        this.shops = shops;
        this.policies = policies;
        this.refunds = refunds;
        this.reservations = reservations;
        this.outbox = outbox;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ buyer

    @Transactional
    public ReturnCaseDetail open(
            String subject,
            UUID orderId,
            String reason,
            String preferred,
            String description,
            List<ItemInput> items) {
        UUID buyer = accounts.requireActiveAccount(subject);
        SellerOrder order =
                orders.findOrder(buyer, orderId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.ORDER_NOT_FOUND,
                                                "Không tìm thấy đơn hàng."));
        String text = description == null ? "" : description.trim().replaceAll("\\s+", " ");
        if (!REASONS.contains(reason)) {
            throw invalid("Lý do trả hàng không hợp lệ.");
        }
        if (!Set.of("REFUND_ONLY", "RETURN_AND_REFUND").contains(preferred)) {
            throw invalid("Hình thức xử lý không hợp lệ.");
        }
        if (reason.equals("CHANGED_MIND") && preferred.equals("REFUND_ONLY")) {
            throw invalid(
                    "Đổi ý chỉ được trả hàng và hoàn tiền, không hoàn tiền không cần trả hàng.");
        }
        if (text.length() < 10 || text.length() > 1000) {
            throw invalid("Mô tả cần từ 10 đến 1000 ký tự.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (order.status() != OrderStatus.DELIVERED
                || order.returnEligibleUntil() == null
                || now.isAfter(order.returnEligibleUntil())) {
            throw new BusinessException(
                    ErrorCode.RETURN_NOT_ALLOWED,
                    order.status() != OrderStatus.DELIVERED
                            ? "Chỉ đơn đã giao mới có thể yêu cầu trả hàng."
                            : "Đã quá hạn đổi trả của đơn hàng này.");
        }
        var handling =
                policies.returnHandling()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.CHECKOUT_POLICY_MISSING,
                                                "Chưa thể mở yêu cầu trả hàng: thiếu chính sách xử lý trả hàng đã được duyệt."));
        // Faults of the shop or the carrier refund the shipping fee too; changing one's mind does
        // not.
        long amount =
                reason.equals("CHANGED_MIND")
                        ? order.payableAmount() - order.shippingAmount()
                        : order.payableAmount();
        // Naming items refunds their share of what was paid for them; the shipping fee follows only
        // when the whole order goes back and the reason allows it.
        List<ItemInput> chosen = items == null ? List.of() : items;
        List<OrderLine> lines = chosen.isEmpty() ? List.of() : orders.findLines(orderId);
        if (!chosen.isEmpty()) {
            Map<UUID, OrderLine> byId = new HashMap<>();
            lines.forEach(l -> byId.put(l.id(), l));
            Set<UUID> seen = new HashSet<>();
            long picked = 0;
            boolean whole = chosen.size() == lines.size();
            for (ItemInput item : chosen) {
                OrderLine line = byId.get(item.orderLineId());
                if (line == null || !seen.add(item.orderLineId())) {
                    throw invalid("Sản phẩm trả không thuộc đơn hàng hoặc bị trùng.");
                }
                if (item.quantity() < 1 || item.quantity() > line.quantity()) {
                    throw invalid(
                            "Số lượng trả của " + line.productNameSnapshot() + " không hợp lệ.");
                }
                picked += line.netAmount() * item.quantity() / line.quantity();
                whole = whole && item.quantity() == line.quantity();
            }
            amount = whole ? amount : picked;
        }
        if (amount <= 0) {
            throw invalid("Đơn này không có số tiền nào có thể hoàn.");
        }
        UUID id = UUID.randomUUID();
        try {
            cases.insert(
                    id,
                    orderId,
                    order.shopId(),
                    buyer,
                    reason,
                    preferred,
                    text,
                    amount,
                    handling.policyId(),
                    now.plusHours(handling.sellerResponseHours()));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    ErrorCode.RETURN_NOT_ALLOWED, "Đơn này đã có yêu cầu trả hàng.");
        }
        for (ItemInput item : chosen) {
            cases.insertItem(id, item.orderLineId(), item.quantity());
        }
        cases.insertEvent(id, null, "REQUESTED", "BUYER", subject, text);
        publish("ReturnRequested", id, order);
        return detail(id);
    }

    @Transactional
    public ReturnCaseDetail acceptOffer(String subject, UUID caseId) {
        ReturnCase c = buyerCase(subject, caseId);
        long amount = c.offeredAmount() == null ? c.requestedAmount() : c.offeredAmount();
        move(
                c,
                Set.of("SELLER_OFFERED"),
                "REFUND_APPROVED",
                new Change(null, amount, "REFUND_ONLY", null, null, null));
        refunds.createForReturn(c.sellerOrderId(), c.id(), amount);
        event(
                c,
                "SELLER_OFFERED",
                "REFUND_APPROVED",
                "BUYER",
                subject,
                "Người mua đồng ý đề xuất hoàn tiền");
        publish("ReturnRefundApproved", c.id(), orders.findById(c.sellerOrderId()));
        return detail(caseId);
    }

    /** Declining the offer sends the case to an administrator. */
    @Transactional
    public ReturnCaseDetail declineOffer(String subject, UUID caseId, String note) {
        ReturnCase c = buyerCase(subject, caseId);
        move(c, Set.of("SELLER_OFFERED"), "REVIEWING", Change.none());
        event(
                c,
                "SELLER_OFFERED",
                "REVIEWING",
                "BUYER",
                subject,
                clean(note, "Người mua không đồng ý đề xuất"));
        return detail(caseId);
    }

    /** After the shop rejected the request the buyer may ask an administrator to decide. */
    @Transactional
    public ReturnCaseDetail escalate(String subject, UUID caseId, String note) {
        ReturnCase c = buyerCase(subject, caseId);
        move(c, Set.of("SELLER_REJECTED"), "REVIEWING", Change.none());
        event(
                c,
                "SELLER_REJECTED",
                "REVIEWING",
                "BUYER",
                subject,
                clean(note, "Người mua nhờ quản trị viên xem xét"));
        return detail(caseId);
    }

    /** An order line and the quantity the buyer returns. */
    public record ItemInput(UUID orderLineId, int quantity) {}

    /** What the return parcel needs from a case: only the buyer, only while goods are awaited. */
    public record ShippingContext(UUID orderId, UUID shopId, String status) {}

    @Transactional
    public ShippingContext shippingContext(String subject, UUID caseId) {
        ReturnCase c = buyerCase(subject, caseId);
        if (!c.status().equals("AWAITING_RETURN") && !c.status().equals("IN_RETURN")) {
            throw new BusinessException(
                    ErrorCode.INVALID_RETURN_STATE,
                    "Yêu cầu đang ở trạng thái "
                            + c.status()
                            + " nên không thể tạo vận đơn trả hàng.");
        }
        return new ShippingContext(c.sellerOrderId(), c.shopId(), c.status());
    }

    @Transactional
    public ReturnCaseDetail detailForBuyer(String subject, UUID caseId) {
        buyerCase(subject, caseId);
        return detail(caseId);
    }

    @Transactional
    public ReturnCaseDetail ship(String subject, UUID caseId, String trackingCode) {
        String code = trackingCode == null ? "" : trackingCode.trim();
        if (!TRACKING.matcher(code).matches()) {
            throw invalid(
                    "Mã vận đơn cần 3–100 ký tự gồm chữ, số, dấu chấm, gạch ngang hoặc gạch dưới.");
        }
        ReturnCase c = buyerCase(subject, caseId);
        move(
                c,
                Set.of("AWAITING_RETURN"),
                "IN_RETURN",
                new Change(null, null, null, null, null, code));
        event(c, "AWAITING_RETURN", "IN_RETURN", "BUYER", subject, "Mã vận đơn trả hàng " + code);
        return detail(caseId);
    }

    @Transactional
    public ReturnCaseDetail cancel(String subject, UUID caseId) {
        ReturnCase c = buyerCase(subject, caseId);
        move(
                c,
                Set.of("REQUESTED", "SELLER_OFFERED", "AWAITING_RETURN"),
                "CANCELED",
                Change.none());
        event(c, c.status(), "CANCELED", "BUYER", subject, "Người mua hủy yêu cầu");
        return detail(caseId);
    }

    @Transactional
    public ReturnCaseDetail forBuyer(String subject, UUID orderId) {
        UUID buyer = accounts.requireActiveAccount(subject);
        orders.findOrder(buyer, orderId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));
        ReturnCase c =
                cases.findLiveByOrder(orderId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.RETURN_CASE_NOT_FOUND,
                                                "Đơn này chưa có yêu cầu trả hàng."));
        return detail(c.id());
    }

    // ------------------------------------------------------------------ shop

    @Transactional
    public List<ReturnCaseDetail> listForShop(
            String subject, UUID shopId, String status, int page, int size) {
        shops.ownedShop(shopId, subject);
        return cases.listForShop(shopId, status, size, page * size).stream()
                .map(c -> detail(c.id()))
                .toList();
    }

    @Transactional
    public long countForShop(String subject, UUID shopId, String status) {
        shops.ownedShop(shopId, subject);
        return cases.countForShop(shopId, status);
    }

    /** The shop offers money back without taking the goods back (all or part of the request). */
    @Transactional
    public ReturnCaseDetail offerRefund(
            String subject, UUID shopId, UUID caseId, long amount, String note) {
        ReturnCase c = shopCase(subject, shopId, caseId);
        if (c.preferredResolution().equals("RETURN_AND_REFUND")
                && c.reasonCode().equals("CHANGED_MIND")) {
            throw invalid("Đổi ý cần nhận lại hàng trước khi hoàn tiền.");
        }
        if (amount <= 0 || amount > c.requestedAmount()) {
            throw invalid("Số tiền đề xuất phải từ 1 đến " + c.requestedAmount() + ".");
        }
        move(
                c,
                Set.of("REQUESTED"),
                "SELLER_OFFERED",
                new Change(amount, null, null, clean(note, null), null, null));
        event(c, "REQUESTED", "SELLER_OFFERED", "SELLER", subject, "Đề xuất hoàn " + amount);
        return detail(caseId);
    }

    /** The shop accepts a return: the goods come back first, the money after inspection. */
    @Transactional
    public ReturnCaseDetail approveReturn(String subject, UUID shopId, UUID caseId, String note) {
        ReturnCase c = shopCase(subject, shopId, caseId);
        move(
                c,
                Set.of("REQUESTED"),
                "AWAITING_RETURN",
                new Change(null, null, "RETURN_AND_REFUND", clean(note, null), null, null));
        event(
                c,
                "REQUESTED",
                "AWAITING_RETURN",
                "SELLER",
                subject,
                clean(note, "Đồng ý nhận lại hàng"));
        return detail(caseId);
    }

    @Transactional
    public ReturnCaseDetail reject(String subject, UUID shopId, UUID caseId, String reason) {
        ReturnCase c = shopCase(subject, shopId, caseId);
        String clean = requiredNote(reason);
        move(
                c,
                Set.of("REQUESTED"),
                "SELLER_REJECTED",
                new Change(null, null, null, clean, null, null));
        event(c, "REQUESTED", "SELLER_REJECTED", "SELLER", subject, clean);
        return detail(caseId);
    }

    /** The goods arrived and are fine: the full requested amount is refunded. */
    @Transactional
    public ReturnCaseDetail confirmReceived(
            String subject, UUID shopId, UUID caseId, boolean restock, String note) {
        ReturnCase c = shopCase(subject, shopId, caseId);
        move(
                c,
                Set.of("IN_RETURN"),
                "REFUND_APPROVED",
                new Change(null, c.requestedAmount(), null, clean(note, null), null, null));
        refunds.createForReturn(c.sellerOrderId(), c.id(), c.requestedAmount());
        // Goods go back on sale automatically only when the whole order came back; a partial
        // return is put back by the shop through a stock adjustment (it knows what is resellable).
        boolean partial = cases.isPartial(c.id());
        if (restock && !partial) {
            for (UUID reservationId : orders.findReservationIds(c.sellerOrderId())) {
                reservations.returnCommitted(reservationId);
            }
        }
        event(
                c,
                "IN_RETURN",
                "REFUND_APPROVED",
                "SELLER",
                subject,
                "Đã nhận hàng trả"
                        + (restock && !partial ? ", nhập kho lại" : "")
                        + (partial ? " (trả một phần: nhập kho bằng điều chỉnh tồn kho)" : ""));
        publish("ReturnRefundApproved", c.id(), orders.findById(c.sellerOrderId()));
        return detail(caseId);
    }

    /** The goods arrived but are not as the buyer described: an administrator decides. */
    @Transactional
    public ReturnCaseDetail reportInspectionFailed(
            String subject, UUID shopId, UUID caseId, String reason) {
        ReturnCase c = shopCase(subject, shopId, caseId);
        String clean = requiredNote(reason);
        move(c, Set.of("IN_RETURN"), "REVIEWING", new Change(null, null, null, clean, null, null));
        event(c, "IN_RETURN", "REVIEWING", "SELLER", subject, clean);
        return detail(caseId);
    }

    // ------------------------------------------------------------------ administrators

    @Transactional
    public List<ReturnCaseDetail> adminQueue() {
        return cases.listForAdmin(OffsetDateTime.now(clock), 200).stream()
                .map(c -> detail(c.id()))
                .toList();
    }

    /**
     * The administrator settles a case the shop did not: refund only (the buyer keeps the goods),
     * return and refund, or reject. The amount may be lower than requested.
     */
    @Transactional
    public ReturnCaseDetail decide(
            String subject, UUID caseId, String decision, Long amount, String note) {
        String why = requiredNote(note);
        ReturnCase c =
                cases.find(caseId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.RETURN_CASE_NOT_FOUND,
                                                "Không tìm thấy yêu cầu trả hàng."));
        boolean overdue =
                c.status().equals("REQUESTED")
                        && c.sellerDeadlineAt().isBefore(OffsetDateTime.now(clock));
        Set<String> from = overdue ? Set.of("REQUESTED") : Set.of("REVIEWING");
        long granted = amount == null ? c.requestedAmount() : amount;
        switch (decision) {
            case "REFUND_ONLY" -> {
                if (granted <= 0 || granted > c.requestedAmount()) {
                    throw invalid("Số tiền hoàn phải từ 1 đến " + c.requestedAmount() + ".");
                }
                if (c.reasonCode().equals("CHANGED_MIND")) {
                    throw invalid("Đổi ý cần nhận lại hàng trước khi hoàn tiền.");
                }
                move(
                        c,
                        from,
                        "REFUND_APPROVED",
                        new Change(null, granted, "REFUND_ONLY", null, why, null));
                refunds.createForReturn(c.sellerOrderId(), c.id(), granted);
                publish("ReturnRefundApproved", c.id(), orders.findById(c.sellerOrderId()));
            }
            case "RETURN_AND_REFUND" ->
                    move(
                            c,
                            from,
                            "AWAITING_RETURN",
                            new Change(null, null, "RETURN_AND_REFUND", null, why, null));
            case "REJECT" ->
                    move(c, from, "REJECTED", new Change(null, null, null, null, why, null));
            default -> throw invalid("Quyết định không hợp lệ.");
        }
        String to =
                decision.equals("REJECT")
                        ? "REJECTED"
                        : decision.equals("REFUND_ONLY") ? "REFUND_APPROVED" : "AWAITING_RETURN";
        event(c, c.status(), to, "ADMIN", subject, why);
        audit.record(subject, "RETURN_CASE_DECIDED", caseId, decision + " " + c.sellerOrderId());
        return detail(caseId);
    }

    // ------------------------------------------------------------------ helpers

    private ReturnCaseDetail detail(UUID id) {
        ReturnCase c = cases.find(id).orElseThrow();
        return new ReturnCaseDetail(c, cases.events(id), cases.items(id));
    }

    private ReturnCase buyerCase(String subject, UUID caseId) {
        UUID buyer = accounts.requireActiveAccount(subject);
        ReturnCase c = find(caseId);
        if (!c.buyerAccountId().equals(buyer)) {
            throw notFound();
        }
        return c;
    }

    private ReturnCase shopCase(String subject, UUID shopId, UUID caseId) {
        shops.ownedShop(shopId, subject);
        ReturnCase c = find(caseId);
        if (!c.shopId().equals(shopId)) {
            throw notFound();
        }
        return c;
    }

    private ReturnCase find(UUID id) {
        return cases.find(id).orElseThrow(ReturnCaseService::notFound);
    }

    private void move(ReturnCase c, Set<String> from, String to, Change change) {
        if (!cases.move(c.id(), from, to, change)) {
            ReturnCase now = cases.find(c.id()).orElse(c);
            throw new BusinessException(
                    ErrorCode.INVALID_RETURN_STATE,
                    "Yêu cầu đang ở trạng thái "
                            + now.status()
                            + " nên không thể thực hiện thao tác này.");
        }
    }

    private void event(
            ReturnCase c, String from, String to, String type, String actor, String note) {
        cases.insertEvent(c.id(), from, to, type, actor, note);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("returnCaseId", c.id());
        payload.put("orderId", c.sellerOrderId());
        payload.put("shopId", c.shopId());
        payload.put("toStatus", to);
        payload.put("actor", type);
        outbox.append("return", "ReturnCaseUpdated", c.id(), json.writeValueAsString(payload));
    }

    private void publish(String type, UUID caseId, SellerOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("returnCaseId", caseId);
        payload.put("orderId", order.id());
        payload.put("shopId", order.shopId());
        outbox.append("return", type, caseId, json.writeValueAsString(payload));
    }

    private static String clean(String s, String fallback) {
        String c = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        if (c.isEmpty()) {
            return fallback;
        }
        return c.substring(0, Math.min(500, c.length()));
    }

    private static String requiredNote(String s) {
        String c = clean(s, "");
        if (c.length() < 5) {
            throw invalid("Lý do cần từ 5 đến 500 ký tự.");
        }
        return c;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_RETURN_INPUT, message);
    }

    private static BusinessException notFound() {
        return new BusinessException(
                ErrorCode.RETURN_CASE_NOT_FOUND, "Không tìm thấy yêu cầu trả hàng.");
    }
}
