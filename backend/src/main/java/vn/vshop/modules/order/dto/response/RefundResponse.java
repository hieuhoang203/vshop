package vn.vshop.modules.order.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A refund as its buyer sees it. {@code needsAccount} means we are waiting for a bank account; the
 * account is shown only by its last four digits.
 */
public record RefundResponse(
        UUID id,
        String status,
        String reason,
        long amount,
        boolean needsAccount,
        @Nullable String bankName,
        @Nullable String accountLast4,
        @Nullable String payoutReference,
        OffsetDateTime createdAt,
        @Nullable OffsetDateTime paidAt) {}
