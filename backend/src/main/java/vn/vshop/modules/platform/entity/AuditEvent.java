package vn.vshop.modules.platform.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        String actorSubject,
        String action,
        UUID resourceId,
        String reason,
        OffsetDateTime createdAt) {}
