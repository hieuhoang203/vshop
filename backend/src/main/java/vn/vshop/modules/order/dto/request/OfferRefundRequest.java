package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Money offered back without taking the goods back, in whole VND. */
public record OfferRefundRequest(
        @Min(1) @Max(1_000_000_000_000L) long amount, @Size(max = 600) String note) {}
