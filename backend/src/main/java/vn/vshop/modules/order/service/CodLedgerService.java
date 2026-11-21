package vn.vshop.modules.order.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.modules.order.entity.CodReceivable;
import vn.vshop.modules.order.entity.SellerOrder;
import vn.vshop.modules.order.repository.CodReceivableRepository;
import vn.vshop.modules.order.repository.OrderRepository;
import vn.vshop.modules.platform.service.AuditEventService;

/**
 * Tracks cash-on-delivery money. When a cash order is delivered a receivable is recorded in the
 * same transaction; the carrier remits it later and an administrator records the remittance
 * reference. Refunds of cash orders are subtracted in the view, never by editing the receivable.
 */
@Service
public class CodLedgerService {
    private final CodReceivableRepository receivables;
    private final OrderRepository orders;
    private final AuditEventService audit;

    CodLedgerService(
            CodReceivableRepository receivables, OrderRepository orders, AuditEventService audit) {
        this.receivables = receivables;
        this.orders = orders;
        this.audit = audit;
    }

    /** Called when an order becomes DELIVERED; does nothing for online payments. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDelivered(SellerOrder order, boolean byCarrier) {
        if (!orders.paymentMethodOfGroup(order.checkoutGroupId()).equals("COD")
                || order.payableAmount() <= 0) {
            return;
        }
        receivables.insertIfAbsent(
                order.id(),
                order.shopId(),
                order.payableAmount(),
                byCarrier ? "CARRIER" : "SHOP",
                byCarrier ? "WITH_CARRIER" : "SETTLED");
    }

    @Transactional
    public List<CodReceivable> list(String status) {
        return receivables.list(status, 200);
    }

    /** The carrier paid the cash over; recorded once with the bank or statement reference. */
    @Transactional
    public CodReceivable settle(String subject, UUID id, String reference) {
        String ref = reference == null ? "" : reference.trim();
        if (ref.length() < 3 || ref.length() > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_REFUND_INPUT, "Mã đối soát cần từ 3 đến 100 ký tự.");
        }
        CodReceivable current =
                receivables
                        .find(id)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.ORDER_NOT_FOUND,
                                                "Không tìm thấy khoản thu hộ."));
        if (!receivables.settle(id, ref, subject)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REFUND_STATE,
                    "Khoản thu hộ đang ở trạng thái " + current.status() + ".");
        }
        audit.record(
                subject,
                "COD_SETTLED",
                id,
                "order " + current.orderNumber() + " " + current.amount());
        return receivables.find(id).orElseThrow();
    }
}
