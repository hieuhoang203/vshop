package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Total weight of the parcel in grams; the catalog has no product weights yet. */
public record CreateShipmentRequest(@Min(50) @Max(50000) int weightGrams) {}
