package vn.vshop.modules.order.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Orders waiting for a shop longer than the approved confirmation deadline. {@code confirmMinutes}
 * is null while no deadline policy is approved; nothing is canceled automatically.
 */
public record OverdueOrdersResponse(
        @Nullable Integer confirmMinutes, List<OverdueOrderResponse> items) {
    public record OverdueOrderResponse(
            UUID id,
            String orderNumber,
            UUID shopId,
            String shopName,
            long payableAmount,
            OffsetDateTime createdAt,
            long minutesOverdue) {}
}
