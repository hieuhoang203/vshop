package vn.vshop.modules.platform.repository.jdbc;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.platform.entity.AuditEvent;
import vn.vshop.modules.platform.entity.OutboxEvent;
import vn.vshop.modules.platform.repository.EventRepository;

@Repository
public class JdbcEventRepository implements EventRepository {
    private final JdbcClient jdbc;

    public JdbcEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void appendAudit(AuditEvent event) {
        jdbc.sql(
                        """
            INSERT INTO platform.audit_event (id, actor_subject, action, resource_id, reason, created_at)
            VALUES (:id, :actor, :action, :resource, :reason, :created)
            """)
                .param("id", event.id())
                .param("actor", event.actorSubject())
                .param("action", event.action())
                .param("resource", event.resourceId())
                .param("reason", event.reason())
                .param("created", event.createdAt())
                .update();
    }

    @Override
    public void appendOutbox(AuditEvent event) {
        jdbc.sql(
                        """
            INSERT INTO platform.outbox_event (id, event_type, aggregate_id, payload)
            VALUES (:id, :type, :aggregate, jsonb_build_object('shopId', CAST(:shop AS text)))
            """)
                .param("id", event.id())
                .param("type", event.action())
                .param("aggregate", event.resourceId())
                .param("shop", event.resourceId().toString())
                .update();
    }

    @Override
    public List<OutboxEvent> lockPending(int limit) {
        return jdbc.sql(
                        """
            SELECT id, event_type, aggregate_id, schema_version, payload::text AS payload, created_at, processed_at
            FROM platform.outbox_event
            WHERE processed_at IS NULL ORDER BY created_at, id
            LIMIT :limit FOR UPDATE SKIP LOCKED
            """)
                .param("limit", limit)
                .query(OutboxEvent.class)
                .list();
    }

    @Override
    public void insertReceipt(OutboxEvent event) {
        jdbc.sql(
                        """
            INSERT INTO platform.event_receipt (event_id, event_type)
            VALUES (:id, :type) ON CONFLICT (event_id) DO NOTHING
            """)
                .param("id", event.id())
                .param("type", event.eventType())
                .update();
    }

    @Override
    public void markProcessed(UUID id) {
        jdbc.sql(
                        """
            UPDATE platform.outbox_event SET processed_at = now()
            WHERE id = :id AND processed_at IS NULL
            """)
                .param("id", id)
                .update();
    }
}
