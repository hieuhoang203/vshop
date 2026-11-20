package vn.vshop.modules.platform.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.vshop.modules.platform.service.EmailDeliveryService;

/** Sends queued notification emails. Needs a configured mail server; otherwise it does nothing. */
@Component
@ConditionalOnProperty(name = "vshop.worker.enabled", havingValue = "true")
public class EmailWorker {
    private final EmailDeliveryService emails;

    public EmailWorker(EmailDeliveryService emails) {
        this.emails = emails;
    }

    @Scheduled(fixedDelayString = "${vshop.worker.email-ms:5000}")
    public void send() {
        emails.sendDue(20);
    }
}
