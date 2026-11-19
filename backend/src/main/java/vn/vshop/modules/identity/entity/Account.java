package vn.vshop.modules.identity.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Account(
        UUID id,
        String externalSubject,
        AccountStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
