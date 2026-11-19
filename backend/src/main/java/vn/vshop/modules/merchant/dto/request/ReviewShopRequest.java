package vn.vshop.modules.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.vshop.modules.merchant.entity.ShopReviewDecision;

public record ReviewShopRequest(
        @NotNull ShopReviewDecision decision, @NotBlank @Size(min = 5, max = 500) String reason) {
    public ReviewShopRequest {
        if (reason != null) reason = reason.strip();
    }
}
