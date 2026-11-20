package vn.vshop.modules.platform.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.modules.platform.repository.EventRepository;

@Service
public class OutboxProcessingService {
    private final EventRepository events;
    private final OutboxDispatcher dispatcher;

    public OutboxProcessingService(EventRepository events, OutboxDispatcher dispatcher) {
        this.events = events;
        this.dispatcher = dispatcher;
    }

    /**
     * Records a durable receipt for each pending event, hands it to every handler (notifications,
     * today) and marks it processed, all in one transaction. A handler that fails does not block
     * the event or the ones behind it.
     */
    @Transactional
    public void receive() {
        for (var event : events.lockPending(50)) {
            events.insertReceipt(event);
            dispatcher.dispatch(event);
            events.markProcessed(event.id());
        }
    }
}
