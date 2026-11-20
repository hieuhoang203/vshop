package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Proof that the money was sent: the bank transaction reference, with an optional note. */
public record RefundPaidRequest(
        @NotBlank @Size(max = 120) String payoutReference, @Size(max = 600) String note) {}
