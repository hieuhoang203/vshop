package vn.vshop.modules.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitShopRequest(
        @NotBlank @Size(min = 3, max = 100) String name,
        @NotBlank @Size(min = 20, max = 2000) String description) {
    public SubmitShopRequest {
        if (name != null) name = name.strip();
        if (description != null) description = description.strip();
    }
}
