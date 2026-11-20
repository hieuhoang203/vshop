package vn.vshop.modules.platform.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vn.vshop.modules.platform.entity.PolicyStatus;
import vn.vshop.modules.platform.entity.PolicyType;

public record PolicyResponse(
        UUID id,
        PolicyType policyType,
        int version,
        Map<String, Long> content,
        PolicyStatus status,
        @Nullable OffsetDateTime effectiveFrom,
        @Nullable OffsetDateTime effectiveTo,
        @Nullable OffsetDateTime approvedAt,
        OffsetDateTime createdAt) {}
