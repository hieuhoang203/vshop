package vn.vshop.modules.checkout.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body of PUT /cart/lines/{skuId}: sets the absolute quantity. */
public record SetQuantityRequest(@NotNull @Min(1) @Max(99) Integer quantity) {}
