package vn.vshop.modules.order.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A refund as its buyer sees it: the account is shown only by its last four digits. */
public record RefundView(
        UUID id,
        String status,
        String reason,
        long amount,
        boolean needsAccount,
        String bankName,
        String accountLast4,
        String payoutReference,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt) {}
