package vn.vshop.modules.platform.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.modules.platform.repository.EventRepository;

@Service
public class OutboxProcessingService {
    private final EventRepository events;

    public OutboxProcessingService(EventRepository events) {
        this.events = events;
    }

    /** Local durable-receipt consumer, not an email/broker delivery implementation. */
    @Transactional
    public void receive() {
        for (var event : events.lockPending(50)) {
            events.insertReceipt(event);
            events.markProcessed(event.id());
        }
    }
}
