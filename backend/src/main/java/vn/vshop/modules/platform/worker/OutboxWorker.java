package vn.vshop.modules.platform.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.vshop.modules.platform.service.OutboxProcessingService;

@Component
@ConditionalOnProperty(name = "vshop.worker.enabled", havingValue = "true")
public class OutboxWorker {
    private final OutboxProcessingService processing;

    public OutboxWorker(OutboxProcessingService processing) {
        this.processing = processing;
    }

    @Scheduled(fixedDelayString = "${vshop.worker.poll-ms:3000}")
    public void receive() {
        processing.receive();
    }
}
