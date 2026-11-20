package vn.vshop.modules.order.service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.common.security.PiiCipher;
import vn.vshop.modules.identity.service.AccountService;
import vn.vshop.modules.order.entity.Refund;
import vn.vshop.modules.order.model.AdminRefund;
import vn.vshop.modules.order.model.RefundView;
import vn.vshop.modules.order.repository.OrderRepository;
import vn.vshop.modules.order.repository.RefundRepository;
import vn.vshop.modules.platform.service.AuditEventService;
import vn.vshop.modules.platform.service.OutboxService;

/**
 * Refunds of money collected online (docs/24, slice R0). A refund is created inside the transaction
 * of the event that causes it, for exactly the money that is still refundable for that order (rows
 * locked, so the same money is never refunded twice, and a repeat of the same cause is a no-op). It
 * then waits for the buyer's bank account and is paid out by an administrator, who records the bank
 * reference. COD orders have no collected money and therefore never get a refund here.
 */
@Service
public class RefundService {
    private static final Pattern DIGITS = Pattern.compile("^\\d{6,20}$");
    private static final Pattern NAME = Pattern.compile("^[A-Z ]{2,100}$");

    private final RefundRepository refunds;
    private final OrderRepository orders;
    private final AccountService accounts;
    private final PiiCipher cipher;
    private final AuditEventService audit;
    private final OutboxService outbox;
    private final ObjectMapper json;

    RefundService(
            RefundRepository refunds,
            OrderRepository orders,
            AccountService accounts,
            PiiCipher cipher,
            AuditEventService audit,
            OutboxService outbox,
            ObjectMapper json) {
        this.refunds = refunds;
        this.orders = orders;
        this.accounts = accounts;
        this.cipher = cipher;
        this.audit = audit;
        this.outbox = outbox;
        this.json = json;
    }

    /** What the buyer enters; the service validates and normalises it. */
    public record Account(String bankName, String accountNumber, String accountName) {}

    /**
     * Creates the refund for everything still refundable on the order. No-op when nothing was
     * collected online (COD) or it was refunded already. {@code reason} is also part of the
     * idempotency key, so one cause produces one refund however often it is reported.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createForOrder(UUID orderId, String reason) {
        for (RefundRepository.Refundable r : refunds.lockRefundable(orderId)) {
            if (r.amount() <= 0) {
                continue;
            }
            UUID id = UUID.randomUUID();
            String key = "order:" + orderId + ":" + reason + ":" + r.allocationId();
            if (refunds.insertRefund(
                    id, r.chargeId(), r.checkoutGroupId(), orderId, reason, key, r.amount())) {
                refunds.insertAllocation(id, r.chargeId(), r.allocationId(), r.amount());
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("refundId", id);
                payload.put("orderId", orderId);
                payload.put("amount", r.amount());
                payload.put("reason", reason);
                outbox.append("refund", "RefundRequested", id, json.writeValueAsString(payload));
            }
        }
    }

    /** The newest refund of an order for its buyer's screen, if any. */
    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<RefundView> viewForOrder(UUID orderId) {
        List<Refund> list = refunds.findByOrder(orderId);
        return list.isEmpty() ? Optional.empty() : Optional.of(view(list.get(list.size() - 1)));
    }

    /** The buyer tells us where to send the money; allowed until it has been paid. */
    @Transactional
    public RefundView provideAccount(String subject, UUID orderId, Account in) {
        UUID buyer = accounts.requireActiveAccount(subject);
        orders.findOrder(buyer, orderId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));
        String bank = collapse(in.bankName());
        String number =
                in.accountNumber() == null ? "" : in.accountNumber().replaceAll("[\\s.\\-]", "");
        String name = unaccent(in.accountName());
        if (bank.length() < 2 || bank.length() > 100) {
            throw invalid("Tên ngân hàng cần 2–100 ký tự.");
        }
        if (!DIGITS.matcher(number).matches()) {
            throw invalid("Số tài khoản chỉ gồm 6–20 chữ số.");
        }
        if (!NAME.matcher(name).matches()) {
            throw invalid("Tên chủ tài khoản chỉ gồm chữ cái, 2–100 ký tự.");
        }
        List<Refund> open =
                refunds.findByOrder(orderId).stream()
                        .filter(
                                r ->
                                        r.status().equals("AWAITING_ACCOUNT")
                                                || r.status().equals("APPROVED"))
                        .toList();
        if (open.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.REFUND_NOT_FOUND, "Đơn này không có khoản hoàn tiền nào đang chờ.");
        }
        for (Refund r : open) {
            refunds.setAccount(
                    r.id(),
                    bank,
                    cipher.encrypt(number, context("account_number", r.id())),
                    cipher.encrypt(name, context("account_name", r.id())));
        }
        return view(refunds.find(open.get(open.size() - 1).id()).orElseThrow());
    }

    // ------------------------------------------------------------------ administrators

    @Transactional
    public List<AdminRefund> adminList(String status) {
        return refunds.list(status, 200).stream().map(this::adminView).toList();
    }

    /** Records that the money was transferred, with the bank reference as proof. Once only. */
    @Transactional
    public AdminRefund markPaid(String subject, UUID refundId, String reference, String note) {
        String ref = reference == null ? "" : reference.trim();
        if (ref.length() < 3 || ref.length() > 100) {
            throw invalid("Mã giao dịch ngân hàng cần 3–100 ký tự.");
        }
        Refund refund = require(refundId);
        if (!refunds.markPaid(refundId, ref, blankToNull(note), subject)) {
            throw state(refund);
        }
        audit.record(
                subject,
                "REFUND_PAID",
                refundId,
                "order " + refund.orderNumber() + " " + refund.amount());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refundId", refundId);
        payload.put("orderId", refund.sellerOrderId());
        payload.put("amount", refund.amount());
        outbox.append("refund", "RefundSucceeded", refundId, json.writeValueAsString(payload));
        return adminView(require(refundId));
    }

    /** The account turned out to be wrong: the buyer is asked for it again. */
    @Transactional
    public AdminRefund badAccount(String subject, UUID refundId, String reason) {
        String clean = reason == null ? "" : collapse(reason);
        if (clean.length() < 5 || clean.length() > 500) {
            throw invalid("Lý do cần từ 5 đến 500 ký tự.");
        }
        Refund refund = require(refundId);
        if (!refunds.badAccount(refundId, clean)) {
            throw state(refund);
        }
        audit.record(subject, "REFUND_ACCOUNT_REJECTED", refundId, clean);
        return adminView(require(refundId));
    }

    // ------------------------------------------------------------------ helpers

    private Refund require(UUID id) {
        return refunds.find(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.REFUND_NOT_FOUND,
                                        "Không tìm thấy khoản hoàn tiền."));
    }

    private RefundView view(Refund r) {
        String last4 = null;
        if (r.accountNumberCiphertext() != null) {
            String number =
                    cipher.decrypt(r.accountNumberCiphertext(), context("account_number", r.id()));
            last4 = number.substring(Math.max(0, number.length() - 4));
        }
        return new RefundView(
                r.id(),
                r.status(),
                r.reason(),
                r.amount(),
                r.status().equals("AWAITING_ACCOUNT"),
                r.bankName(),
                last4,
                r.payoutReference(),
                r.createdAt(),
                r.paidAt());
    }

    private AdminRefund adminView(Refund r) {
        boolean hasAccount = r.accountNumberCiphertext() != null;
        return new AdminRefund(
                r.id(),
                r.sellerOrderId(),
                r.orderNumber(),
                r.reason(),
                r.status(),
                r.amount(),
                r.bankName(),
                hasAccount
                        ? cipher.decrypt(
                                r.accountNumberCiphertext(), context("account_number", r.id()))
                        : null,
                hasAccount
                        ? cipher.decrypt(r.accountNameCiphertext(), context("account_name", r.id()))
                        : null,
                r.payoutReference(),
                r.note(),
                r.createdAt(),
                r.paidAt());
    }

    private static String context(String column, UUID id) {
        return "payment.refund." + column + ":" + id;
    }

    private static String collapse(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static String blankToNull(String s) {
        String c = collapse(s);
        return c.isEmpty() ? null : c.substring(0, Math.min(500, c.length()));
    }

    /** Bank transfers want unaccented capital letters, the way a bank prints the account holder. */
    private static String unaccent(String s) {
        String plain =
                Normalizer.normalize(collapse(s), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .replace('đ', 'd')
                        .replace('Đ', 'D');
        return plain.toUpperCase();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_REFUND_INPUT, message);
    }

    private static BusinessException state(Refund refund) {
        return new BusinessException(
                ErrorCode.INVALID_REFUND_STATE,
                "Khoản hoàn tiền đang ở trạng thái "
                        + refund.status()
                        + " nên không thể thực hiện thao tác này.");
    }
}
