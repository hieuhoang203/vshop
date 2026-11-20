package vn.vshop.modules.checkout.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of POST /cart/lines: adds {@code quantity} of the SKU to the cart. */
public record CartLineRequest(@NotNull UUID skuId, @NotNull @Min(1) @Max(99) Integer quantity) {}
