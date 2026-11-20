package vn.vshop.modules.platform.repository;

import java.util.List;
import java.util.UUID;
import vn.vshop.modules.platform.entity.AuditEvent;
import vn.vshop.modules.platform.entity.OutboxEvent;

public interface EventRepository {
    void appendAudit(AuditEvent event);

    void appendOutbox(AuditEvent event);

    void appendOutbox(
            UUID id, String aggregateType, String eventType, UUID aggregateId, String payloadJson);

    List<OutboxEvent> lockPending(int limit);

    void insertReceipt(OutboxEvent event);

    void markProcessed(UUID eventId);
}
