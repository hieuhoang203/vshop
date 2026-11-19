package vn.vshop.modules.catalog.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Category(
        UUID id,
        UUID parentId,
        String name,
        String slug,
        int sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
