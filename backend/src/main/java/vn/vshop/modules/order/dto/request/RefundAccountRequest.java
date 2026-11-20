package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Where to send a refund. Wire limits only; the service validates and normalises. */
public record RefundAccountRequest(
        @NotBlank @Size(max = 150) String bankName,
        @NotBlank @Size(max = 40) String accountNumber,
        @NotBlank @Size(max = 150) String accountName) {}
