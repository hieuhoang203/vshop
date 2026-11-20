package vn.vshop.modules.order.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.vshop.modules.order.service.PaymentService;

/** Cancels online orders that were not paid before their deadline and returns their stock. */
@Component
@ConditionalOnProperty(name = "vshop.worker.enabled", havingValue = "true")
public class OrderExpiryWorker {
    private final PaymentService payments;

    public OrderExpiryWorker(PaymentService payments) {
        this.payments = payments;
    }

    @Scheduled(fixedDelayString = "${vshop.worker.order-expiry-ms:30000}")
    public void expire() {
        payments.expireUnpaid(50);
    }
}
