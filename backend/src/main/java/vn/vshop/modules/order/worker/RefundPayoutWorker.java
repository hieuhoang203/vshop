package vn.vshop.modules.order.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.vshop.modules.order.service.RefundPayoutService;

/** Asks the payment provider about refund payouts it has not answered yet. */
@Component
@ConditionalOnProperty(name = "vshop.worker.enabled", havingValue = "true")
public class RefundPayoutWorker {
    private final RefundPayoutService payouts;

    public RefundPayoutWorker(RefundPayoutService payouts) {
        this.payouts = payouts;
    }

    @Scheduled(fixedDelayString = "${vshop.worker.refund-payout-ms:60000}")
    public void reconcile() {
        payouts.reconcileStale(120, 20);
    }
}
