package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import vn.vshop.modules.order.entity.PaymentMethod;

/** Prices are never part of the request: the order is built from the quote and a fresh pricing. */
public record PlaceOrderRequest(@NotNull UUID quoteId, @NotNull PaymentMethod paymentMethod) {}
