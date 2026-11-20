package vn.vshop.modules.platform.service;

import vn.vshop.modules.platform.entity.OutboxEvent;

/**
 * A consumer of outbox events. Handlers must be idempotent: an event can be delivered more than
 * once. They run in the transaction that marks the event processed.
 */
public interface OutboxEventHandler {
    void handle(OutboxEvent event);
}
