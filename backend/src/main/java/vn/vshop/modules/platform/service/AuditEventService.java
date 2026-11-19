package vn.vshop.modules.platform.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.modules.platform.entity.AuditEvent;
import vn.vshop.modules.platform.repository.EventRepository;

@Service
public class AuditEventService {
    private final EventRepository events;

    public AuditEventService(EventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String subject, String action, UUID resourceId, String reason) {
        var event =
                new AuditEvent(
                        UUID.randomUUID(),
                        subject,
                        action,
                        resourceId,
                        reason,
                        java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        events.appendAudit(event);
        events.appendOutbox(event);
    }
}
