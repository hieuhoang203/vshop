package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Manual shipping mode: the seller types the tracking code of the parcel they handed over. */
public record ShipOrderRequest(@NotBlank @Size(max = 120) String trackingCode) {}
