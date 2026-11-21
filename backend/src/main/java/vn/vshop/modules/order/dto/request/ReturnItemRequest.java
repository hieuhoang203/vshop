package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** One order line (and how many of it) the buyer wants to return. */
public record ReturnItemRequest(@NotNull UUID orderLineId, @Min(1) @Max(1000) int quantity) {}
