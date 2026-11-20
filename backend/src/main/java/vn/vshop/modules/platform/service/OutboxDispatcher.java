package vn.vshop.modules.platform.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.vshop.modules.platform.entity.OutboxEvent;

/**
 * Runs every handler for one event in a nested transaction (a savepoint), so a handler that fails
 * is logged and rolled back alone and never blocks the events behind it.
 */
@Component
public class OutboxDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final List<OutboxEventHandler> handlers;
    private final TransactionTemplate nested;

    OutboxDispatcher(List<OutboxEventHandler> handlers, PlatformTransactionManager transactions) {
        this.handlers = handlers;
        this.nested = new TransactionTemplate(transactions);
        this.nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }

    public void dispatch(OutboxEvent event) {
        for (OutboxEventHandler handler : handlers) {
            try {
                nested.executeWithoutResult(status -> handler.handle(event));
            } catch (RuntimeException e) {
                LOG.error(
                        "Outbox handler {} failed for event {} ({})",
                        handler.getClass().getSimpleName(),
                        event.id(),
                        event.eventType(),
                        e);
            }
        }
    }
}
