package vn.vshop.modules.order.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.common.security.PiiCipher;
import vn.vshop.modules.order.entity.Refund;
import vn.vshop.modules.order.gateway.PayoutGateway;
import vn.vshop.modules.order.model.AdminRefund;
import vn.vshop.modules.order.repository.RefundRepository;
import vn.vshop.modules.platform.service.AuditEventService;
import vn.vshop.modules.platform.service.OutboxService;

/**
 * Pays an approved refund out through the payment provider, with an administrator pressing the
 * button (nothing is sent on its own). One transaction claims the refund (APPROVED to SUBMITTED),
 * the provider is called with no lock held, and a second transaction records the answer. A clear
 * success finishes the refund, a clear refusal puts it back to APPROVED with the reason, and
 * anything else leaves it SUBMITTED: it is then resolved by asking the provider about the same
 * reference ({@link #check}, also run by a worker) and never by sending the transfer again.
 */
@Service
public class RefundPayoutService {
    private static final Logger LOG = LoggerFactory.getLogger(RefundPayoutService.class);
    static final String AUTO_SUBJECT = "payout:auto";

    private final RefundRepository refunds;
    private final RefundService refundService;
    private final PayoutGateway gateway;
    private final PiiCipher cipher;
    private final AuditEventService audit;
    private final OutboxService outbox;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    RefundPayoutService(
            RefundRepository refunds,
            RefundService refundService,
            PayoutGateway gateway,
            PiiCipher cipher,
            AuditEventService audit,
            OutboxService outbox,
            ObjectMapper json,
            PlatformTransactionManager transactions) {
        this.refunds = refunds;
        this.refundService = refundService;
        this.gateway = gateway;
        this.cipher = cipher;
        this.audit = audit;
        this.outbox = outbox;
        this.json = json;
        this.tx = new TransactionTemplate(transactions);
    }

    private record Claimed(PayoutGateway.Request request) {}

    /** Sends the money. Repeating the call for a refund already SUBMITTED only checks its state. */
    public AdminRefund submit(String subject, UUID refundId) {
        if (!gateway.enabled()) {
            throw new BusinessException(
                    ErrorCode.PAYOUT_NOT_AVAILABLE,
                    "Chưa bật chi tiền tự động. Hãy chuyển khoản tay rồi ghi nhận mã giao dịch.");
        }
        Claimed claimed = tx.execute(s -> claim(subject, refundId));
        if (claimed == null) {
            return check(subject, refundId);
        }
        apply(refundId, gateway.submit(claimed.request()), subject);
        return tx.execute(s -> refundService.adminGet(refundId));
    }

    /** Asks the provider about a SUBMITTED refund and records what it says. */
    public AdminRefund check(String subject, UUID refundId) {
        if (!gateway.enabled()) {
            throw new BusinessException(
                    ErrorCode.PAYOUT_NOT_AVAILABLE, "Chưa bật chi tiền tự động.");
        }
        var target = tx.execute(s -> refunds.payoutTarget(refundId));
        if (target != null && target.isPresent()) {
            apply(refundId, gateway.query(target.get().providerReference()), subject);
        }
        return tx.execute(s -> refundService.adminGet(refundId));
    }

    /** Worker entry: resolves refunds the provider has had for a while without an answer. */
    public int reconcileStale(int seconds, int limit) {
        if (!gateway.enabled()) {
            return 0;
        }
        List<UUID> stale = tx.execute(s -> refunds.staleSubmitted(seconds, limit));
        int resolved = 0;
        for (UUID id : stale) {
            try {
                var target = tx.execute(s -> refunds.payoutTarget(id));
                if (target != null && target.isPresent()) {
                    if (apply(id, gateway.query(target.get().providerReference()), AUTO_SUBJECT)) {
                        resolved++;
                    }
                }
            } catch (RuntimeException e) {
                LOG.warn("Could not reconcile refund {}: {}", id, e.toString());
            }
        }
        return resolved;
    }

    private Claimed claim(String subject, UUID refundId) {
        Refund refund =
                refunds.find(refundId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.REFUND_NOT_FOUND,
                                                "Không tìm thấy khoản hoàn tiền."));
        if (refund.status().equals("SUBMITTED")) {
            return null; // already with the provider
        }
        if (!refund.status().equals("APPROVED")) {
            throw new BusinessException(
                    ErrorCode.INVALID_REFUND_STATE,
                    "Khoản hoàn tiền đang ở trạng thái "
                            + refund.status()
                            + " nên không thể chi tự động.");
        }
        if (refund.bankBin() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REFUND_STATE,
                    "Tài khoản nhận chưa có mã ngân hàng (người mua nhập tay). Hãy chuyển khoản tay.");
        }
        // The provider reference is the refund id, so a retry can never pay a second time.
        if (!refunds.claimPayout(refundId, gateway.provider(), refundId.toString())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REFUND_STATE,
                    "Khoản hoàn tiền không còn ở trạng thái chờ chi.");
        }
        audit.record(
                subject,
                "REFUND_PAYOUT_SUBMITTED",
                refundId,
                "order " + refund.orderNumber() + " " + refund.amount());
        var target = refunds.payoutTarget(refundId).orElseThrow();
        String number =
                cipher.decrypt(
                        target.accountNumberCiphertext(),
                        "payment.refund.account_number:" + refundId);
        String name =
                cipher.decrypt(
                        target.accountNameCiphertext(), "payment.refund.account_name:" + refundId);
        return new Claimed(
                new PayoutGateway.Request(
                        target.providerReference(),
                        target.amount(),
                        "Hoan tien don " + refund.orderNumber(),
                        target.bankBin(),
                        number,
                        name));
    }

    /** Records the provider answer. True when the refund reached an end state or went back. */
    private boolean apply(UUID refundId, PayoutGateway.Result result, String subject) {
        Boolean done =
                tx.execute(
                        s -> {
                            switch (result.outcome()) {
                                case SUCCEEDED -> {
                                    String reference =
                                            result.providerReference() == null
                                                            || result.providerReference().isBlank()
                                                    ? "PAYOS-" + refundId
                                                    : result.providerReference();
                                    if (!refunds.payoutSucceeded(refundId, reference, subject)) {
                                        return false;
                                    }
                                    Refund refund = refunds.find(refundId).orElseThrow();
                                    audit.record(
                                            subject,
                                            "REFUND_PAID",
                                            refundId,
                                            "order "
                                                    + refund.orderNumber()
                                                    + " "
                                                    + refund.amount());
                                    Map<String, Object> payload = new LinkedHashMap<>();
                                    payload.put("refundId", refundId);
                                    payload.put("orderId", refund.sellerOrderId());
                                    payload.put("amount", refund.amount());
                                    outbox.append(
                                            "refund",
                                            "RefundSucceeded",
                                            refundId,
                                            json.writeValueAsString(payload));
                                    return true;
                                }
                                case FAILED -> {
                                    boolean back = refunds.payoutFailed(refundId, result.message());
                                    if (back) {
                                        audit.record(
                                                subject,
                                                "REFUND_PAYOUT_FAILED",
                                                refundId,
                                                result.message());
                                    }
                                    return back;
                                }
                                default -> {
                                    return false; // still with the provider; asked about again
                                    // later
                                }
                            }
                        });
        return Boolean.TRUE.equals(done);
    }
}
