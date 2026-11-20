package vn.vshop.modules.catalog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.vshop.modules.catalog.entity.ProductReviewDecision;

public record ReviewProductRequest(
        @NotNull ProductReviewDecision decision,
        @NotBlank @Size(min = 5, max = 500) String reason,
        @NotNull @Min(0) Long expectedVersion) {
    public ReviewProductRequest {
        if (reason != null) reason = reason.strip();
    }
}
