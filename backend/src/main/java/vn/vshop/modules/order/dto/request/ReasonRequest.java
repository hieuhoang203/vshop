package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason for rejecting or canceling an order; the service applies the real length rules. */
public record ReasonRequest(@NotBlank @Size(max = 600) String reason) {}
