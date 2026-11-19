package vn.vshop.modules.platform.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        String eventType,
        UUID aggregateId,
        int schemaVersion,
        String payload,
        OffsetDateTime createdAt,
        OffsetDateTime processedAt) {}
