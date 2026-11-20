package vn.vshop.modules.platform.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record NotificationResponse(
        UUID id,
        String kind,
        String title,
        String body,
        @Nullable String link,
        boolean read,
        OffsetDateTime createdAt) {}
