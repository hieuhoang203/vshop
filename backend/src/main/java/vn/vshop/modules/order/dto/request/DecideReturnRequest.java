package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code decision}: REFUND_ONLY, RETURN_AND_REFUND or REJECT. The amount defaults to the request.
 */
public record DecideReturnRequest(
        @NotBlank @Size(max = 30) String decision,
        @Min(1) @Max(1_000_000_000_000L) Long amount,
        @NotBlank @Size(max = 600) String note) {}
