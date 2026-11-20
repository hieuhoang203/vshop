package vn.vshop.modules.merchant.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vn.vshop.modules.merchant.entity.ShopStatus;

public record ShopResponse(
        UUID id,
        String name,
        String description,
        ShopStatus status,
        @Nullable String rejectionReason,
        OffsetDateTime createdAt) {}
