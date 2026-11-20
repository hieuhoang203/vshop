package vn.vshop.modules.order.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vn.vshop.modules.order.entity.OrderStatus;

/** A seller order as its buyer sees it, with the delivery details they entered. */
public record OrderResponse(
        UUID id,
        String orderNumber,
        UUID checkoutGroupId,
        UUID shopId,
        String shopName,
        OrderStatus status,
        long itemAmount,
        long discountAmount,
        long shippingAmount,
        long payableAmount,
        OrderRecipientResponse recipient,
        @Nullable OffsetDateTime paymentDeadline,
        @Nullable OffsetDateTime canceledAt,
        @Nullable String cancelReason,
        OffsetDateTime createdAt,
        List<OrderLineResponse> lines,
        List<OrderStatusEventResponse> history) {

    public record OrderRecipientResponse(
            String name, String phone, String street, String ward, String province) {}

    public record OrderLineResponse(
            UUID skuId,
            UUID productId,
            String productSlug,
            String productName,
            @Nullable String imageKey,
            String sellerSku,
            @Nullable String variantLabel,
            int quantity,
            long unitPrice,
            long grossAmount,
            long discountAmount,
            long netAmount) {}

    public record OrderStatusEventResponse(
            @Nullable OrderStatus fromStatus,
            OrderStatus toStatus,
            String actorType,
            @Nullable String reason,
            OffsetDateTime createdAt) {}
}
