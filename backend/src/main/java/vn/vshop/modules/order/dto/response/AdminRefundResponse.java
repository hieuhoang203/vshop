package vn.vshop.modules.order.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A refund for the administrator who pays it out; the destination account is shown in full. */
public record AdminRefundResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        String reason,
        String status,
        long amount,
        @Nullable String bankName,
        @Nullable String accountNumber,
        @Nullable String accountName,
        @Nullable String payoutReference,
        @Nullable String note,
        OffsetDateTime createdAt,
        @Nullable OffsetDateTime paidAt) {}
