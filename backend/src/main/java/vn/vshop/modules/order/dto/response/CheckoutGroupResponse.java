package vn.vshop.modules.order.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import vn.vshop.modules.order.entity.PaymentMethod;

/** Result of one checkout: the orders created for each shop and what the buyer owes in total. */
public record CheckoutGroupResponse(
        UUID id,
        PaymentMethod paymentMethod,
        String currency,
        long totalAmount,
        OffsetDateTime createdAt,
        List<OrderResponse> orders) {}
