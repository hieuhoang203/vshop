package vn.vshop.modules.platform.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Notification(
        UUID id,
        String kind,
        String title,
        String body,
        String link,
        OffsetDateTime readAt,
        OffsetDateTime createdAt) {}
