package vn.vshop.modules.order.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A refund for the person who pays it out: the buyer's bank account is shown in full. */
public record AdminRefund(
        UUID id,
        UUID orderId,
        String orderNumber,
        String reason,
        String status,
        long amount,
        String bankName,
        String accountNumber,
        String accountName,
        String payoutReference,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt) {}
