package vn.vshop.modules.merchant.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ShopResponse(
        UUID id,
        String name,
        String description,
        String status,
        String rejectionReason,
        OffsetDateTime createdAt) {}
