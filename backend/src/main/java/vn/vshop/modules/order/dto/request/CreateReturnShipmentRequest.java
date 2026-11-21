package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The buyer's address has no district; the carrier needs it to collect the return parcel. */
public record CreateReturnShipmentRequest(@NotBlank @Size(max = 100) String district) {}
