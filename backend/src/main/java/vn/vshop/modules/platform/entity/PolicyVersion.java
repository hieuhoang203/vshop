package vn.vshop.modules.platform.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One version of a business parameter. {@code content} is a JSON object of integers. */
public record PolicyVersion(
        UUID id,
        PolicyType policyType,
        int version,
        String content,
        PolicyStatus status,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo,
        UUID createdByAccountId,
        UUID approvedByAccountId,
        OffsetDateTime approvedAt,
        OffsetDateTime createdAt) {}
