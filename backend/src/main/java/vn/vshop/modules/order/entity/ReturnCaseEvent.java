package vn.vshop.modules.order.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReturnCaseEvent(
        UUID id,
        UUID returnCaseId,
        String fromStatus,
        String toStatus,
        String actorType,
        String actorSubject,
        String note,
        OffsetDateTime createdAt) {}
