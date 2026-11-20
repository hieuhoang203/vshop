package vn.vshop.modules.order.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vn.vshop.modules.order.entity.OrderStatus;

/**
 * An order as its shop sees it. Name, phone and street of the buyer are null until the shop has
 * confirmed the order; the region is always shown. {@code trackingCode} and delivery are entered by
 * the seller (manual shipping mode), not confirmed by a carrier.
 */
public record SellerOrderResponse(
        UUID id,
        String orderNumber,
        OrderStatus status,
        long version,
        long itemAmount,
        long shippingAmount,
        long payableAmount,
        SellerRecipientResponse recipient,
        @Nullable String trackingCode,
        @Nullable String cancelReason,
        OffsetDateTime createdAt,
        List<OrderResponse.OrderLineResponse> lines,
        List<OrderResponse.OrderStatusEventResponse> history) {

    public record SellerRecipientResponse(
            @Nullable String name,
            @Nullable String phone,
            @Nullable String street,
            String ward,
            String province) {}
}
