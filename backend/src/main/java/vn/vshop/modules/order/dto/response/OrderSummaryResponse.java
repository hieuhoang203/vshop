package vn.vshop.modules.order.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vn.vshop.modules.order.entity.OrderStatus;

public record OrderSummaryResponse(
        UUID id,
        String orderNumber,
        UUID checkoutGroupId,
        String shopName,
        OrderStatus status,
        long payableAmount,
        int itemCount,
        String firstProductName,
        @Nullable String firstImageKey,
        OffsetDateTime createdAt) {}
