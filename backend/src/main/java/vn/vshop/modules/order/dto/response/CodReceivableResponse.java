package vn.vshop.modules.order.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Cash collected on delivery; {@code dueAmount} is what remains after refunds to the buyer. */
public record CodReceivableResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        long amount,
        long refundedAmount,
        long dueAmount,
        String collectedBy,
        String status,
        @Nullable String settledReference,
        @Nullable OffsetDateTime settledAt,
        OffsetDateTime createdAt) {}
