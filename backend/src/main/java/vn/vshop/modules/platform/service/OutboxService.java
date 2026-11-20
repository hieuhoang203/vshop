package vn.vshop.modules.platform.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.modules.platform.repository.EventRepository;

/** Appends a domain event to the outbox inside the caller's transaction. */
@Service
public class OutboxService {
    private final EventRepository events;

    public OutboxService(EventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(
            String aggregateType, String eventType, UUID aggregateId, String payloadJson) {
        events.appendOutbox(UUID.randomUUID(), aggregateType, eventType, aggregateId, payloadJson);
    }
}
